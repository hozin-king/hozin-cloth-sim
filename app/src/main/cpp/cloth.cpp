// Verlet cloth — implementasi. Lihat cloth.h untuk desain.
#include "cloth.h"

#include <cmath>

namespace cloth {

namespace {
constexpr float kClothSize = 3.4f;   // lebar kain (bola r=1, kain menutup bola)
constexpr float kStartY = 3.4f;      // tinggi awal kain
constexpr float kSphereR = 1.0f;
constexpr Vec3 kSphereC{0.0f, 1.0f, 0.0f};
constexpr float kGravity = -9.8f;
constexpr float kDamping = 0.985f;   // redaman verlet (biar kain diam setelah melapis)

Vec3 operator+(const Vec3& a, const Vec3& b) { return {a.x + b.x, a.y + b.y, a.z + b.z}; }
Vec3 operator-(const Vec3& a, const Vec3& b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
Vec3 operator*(const Vec3& a, float s) { return {a.x * s, a.y * s, a.z * s}; }
float length(const Vec3& a) { return std::sqrt(a.x * a.x + a.y * a.y + a.z * a.z); }
Vec3 cross(const Vec3& a, const Vec3& b) {
    return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x};
}
}  // namespace

ClothSim::ClothSim(int segments) : n_(segments) {
    // Iterasi adaptif: grid besar = iterasi lebih sedikit supaya tetap jalan.
    iterations_ = (n_ <= 16) ? 4 : (n_ <= 64) ? 3 : 2;
    const int count = particleCount();
    pos_.resize(count);
    prev_.resize(count);
    normals_.resize(count);
    const float step = kClothSize / n_;
    // Constraint struktural (horizontal + vertikal) + shear (diagonal).
    for (int j = 0; j <= n_; j++) {
        for (int i = 0; i <= n_; i++) {
            const uint32_t id = j * (n_ + 1) + i;
            if (i < n_) cons_.push_back({id, id + 1, step});
            if (j < n_) cons_.push_back({id, id + (n_ + 1), step});
            if (i < n_ && j < n_) {
                const float diag = step * 1.41421356f;
                cons_.push_back({id, id + (n_ + 1) + 1, diag});
                cons_.push_back({id + 1, id + (n_ + 1), diag});
            }
        }
    }
    // Indeks segitiga (2 per quad). Winding bebas: kain di-render double-sided.
    for (int j = 0; j < n_; j++) {
        for (int i = 0; i < n_; i++) {
            const uint32_t a = j * (n_ + 1) + i;
            const uint32_t b = a + 1;
            const uint32_t c = a + (n_ + 1);
            const uint32_t d = c + 1;
            indices_.push_back(a);
            indices_.push_back(b);
            indices_.push_back(d);
            indices_.push_back(a);
            indices_.push_back(d);
            indices_.push_back(c);
        }
    }
    reset();
}

void ClothSim::reset() {
    const float step = kClothSize / n_;
    for (int j = 0; j <= n_; j++) {
        for (int i = 0; i <= n_; i++) {
            const uint32_t id = j * (n_ + 1) + i;
            pos_[id] = {-kClothSize / 2 + i * step, kStartY, -kClothSize / 2 + j * step};
            prev_[id] = pos_[id];
            normals_[id] = {0.0f, 1.0f, 0.0f};
        }
    }
}

void ClothSim::step(float dt, float windStrength, float time) {
    const float dt2 = dt * dt;
    // Angin: tiupan searah +x dengan hembusan sinus (variasi per posisi).
    const float gust = 0.6f + 0.4f * std::sin(time * 1.7f);
    const float windAcc = windStrength * 6.0f * gust;
    for (int id = 0; id < particleCount(); id++) {
        Vec3& p = pos_[id];
        const Vec3 v = (p - prev_[id]) * kDamping;
        prev_[id] = p;
        const float phase = std::sin(time * 0.9f + (p.x + p.z) * 0.8f) * 0.35f;
        p = p + v + Vec3{windAcc * (1.0f + phase), kGravity, windAcc * 0.25f * phase} * dt2;
    }
    for (int it = 0; it < iterations_; it++) satisfyConstraints();
    collide();
    computeNormals();
}

void ClothSim::satisfyConstraints() {
    for (const Constraint& c : cons_) {
        Vec3& a = pos_[c.a];
        Vec3& b = pos_[c.b];
        Vec3 delta = b - a;
        const float d = length(delta);
        if (d < 1e-6f) continue;
        const float diff = (d - c.rest) / d * 0.5f;
        const Vec3 corr = delta * diff;
        a = a + corr;
        b = b - corr;
    }
}

void ClothSim::collide() {
    // Gesekan (mu=0.5): tanpa ini kain licin sempurna dan melorot dari bola
    // sampai ke lantai — di video referensi kain diam melapis bola.
    constexpr float kFriction = 0.5f;
    for (int id = 0; id < particleCount(); id++) {
        Vec3& p = pos_[id];
        // Bola: dorong partikel ke luar permukaan, lalu redam kecepatan
        // tangensial (gesekan) supaya kain menempel, bukan melorot.
        Vec3 d = p - kSphereC;
        const float dist = length(d);
        if (dist < kSphereR) {
            if (dist < 1e-6f) {
                p = {kSphereC.x, kSphereC.y + kSphereR, kSphereC.z};
                d = {0.0f, 1.0f, 0.0f};
            } else {
                p = kSphereC + d * (kSphereR / dist);
                d = d * (1.0f / dist);
            }
            Vec3 v = p - prev_[id];
            const float vn = v.x * d.x + v.y * d.y + v.z * d.z;
            Vec3 vt = {v.x - vn * d.x, v.y - vn * d.y, v.z - vn * d.z};
            vt = vt * (1.0f - kFriction);
            prev_[id] = {p.x - (vn * d.x + vt.x), p.y - (vn * d.y + vt.y),
                         p.z - (vn * d.z + vt.z)};
        }
        // Lantai: tahan di y=0, bunuh kecepatan vertikal + redam geser.
        if (p.y < 0.0f) {
            p.y = 0.0f;
            prev_[id].x = p.x - (p.x - prev_[id].x) * (1.0f - kFriction);
            prev_[id].z = p.z - (p.z - prev_[id].z) * (1.0f - kFriction);
            prev_[id].y = 0.0f;
        }
    }
}

void ClothSim::computeNormals() {
    for (int id = 0; id < particleCount(); id++) normals_[id] = {0.0f, 0.0f, 0.0f};
    for (int t = 0; t < triangleCount(); t++) {
        const uint32_t a = indices_[t * 3];
        const uint32_t b = indices_[t * 3 + 1];
        const uint32_t c = indices_[t * 3 + 2];
        const Vec3 n = cross(pos_[b] - pos_[a], pos_[c] - pos_[a]);
        normals_[a] = normals_[a] + n;
        normals_[b] = normals_[b] + n;
        normals_[c] = normals_[c] + n;
    }
    for (int id = 0; id < particleCount(); id++) {
        const float len = length(normals_[id]);
        if (len > 1e-6f) normals_[id] = normals_[id] * (1.0f / len);
    }
}

}  // namespace cloth

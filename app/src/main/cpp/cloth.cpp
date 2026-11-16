// Verlet cloth — implementasi. Lihat cloth.h untuk desain.
#include "cloth.h"

#include <cmath>

namespace cloth {

namespace {
constexpr float kClothSize = 3.4f;   // lebar kain (bola r=1, kain menutup bola)
constexpr float kStartY = 3.4f;      // tinggi awal kain
constexpr float kSphereR = 1.0f;
constexpr Vec3 kSphereC{0.0f, 1.0f, 0.0f};
// Kulit tabrakan: partikel/segitiga didorong ke r+offset, BUKAN persis r.
// Tanpa ini kain menempel persis di permukaan bola -> z-fighting dengan
// bola (garis coklat radial di Face 4096) dan sisa sagitta segitiga
// yang meregang masih mengintip. 0.01 unit ~ 4px di layar, tak terlihat
// sebagai celah, tapi > 1 LSB depth 16-bit (~0.004).
constexpr float kCollideR = kSphereR + 0.01f;
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
    // Iterasi adaptif: grid besar = rantai constraint lebih panjang, jadi
    // butuh iterasi LEBIH BANYAK supaya tidak meregang (terukur: N=128
    // regang 3.4x dan N=256 melorot total dari bola dengan 2 iterasi).
    // 5 iterasi diuji di N=256/512: regang maks turun sedikit tapi jumlah
    // edge >2x naik dan waktu +45% — tidak sepadan, tetap 4.
    iterations_ = (n_ <= 16) ? 4 : (n_ <= 64) ? 3 : 4;
    // Gesekan adaptif: grid besar = rantai lebih panjang -> tarikan tepi
    // yang menggantung lebih mudah menyeret kain melorot dari bola
    // (terukur: N=512 tetap melorot walau 4 iterasi). Grid sangat kasar
    // (N<=4) juga butuh gesekan kuat: hanya sedikit titik kontak yang
    // menahan papan kaku di atas bola (terukur: N=2 melorot ke lantai
    // setelah ~25 detik dengan mu=0.5).
    friction_ = (n_ <= 4 || n_ > 64) ? 0.8f : 0.5f;
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
    for (int id = 0; id < particleCount(); id++) {
        Vec3& p = pos_[id];
        // Bola: dorong partikel ke luar permukaan, lalu redam kecepatan
        // tangensial (gesekan) supaya kain menempel, bukan melorot.
        Vec3 d = p - kSphereC;
        const float dist = length(d);
        if (dist < kCollideR) {
            if (dist < 1e-6f) {
                p = {kSphereC.x, kSphereC.y + kCollideR, kSphereC.z};
                d = {0.0f, 1.0f, 0.0f};
            } else {
                p = kSphereC + d * (kCollideR / dist);
                d = d * (1.0f / dist);
            }
            applyFriction(id, d);
        }
        // Lantai: tahan di y=0, bunuh kecepatan vertikal + redam geser.
        if (p.y < 0.0f) {
            p.y = 0.0f;
            prev_[id].x = p.x - (p.x - prev_[id].x) * (1.0f - friction_);
            prev_[id].z = p.z - (p.z - prev_[id].z) * (1.0f - friction_);
            prev_[id].y = 0.0f;
        }
    }
    // Tabrakan segitiga (hanya grid kasar, N<=64): tabrakan partikel saja
    // tidak melindungi INTERIOR segitiga — bola menembus tengah quad
    // (terukur: Face 1 puncak 0.98, Face 4: 0.21, Face 16: 0.015).
    // Centroid + titik tengah tiap sisi didorong keluar; seluruh vertex
    // segitiga/sisi digeser sama besar supaya panjang constraint utuh.
    // Grid halus (N>64) tidak butuh ini: sagitta segitiga kecil sudah
    // tertutup offset kCollideR.
    if (n_ <= 64) collideTriangles();
}

void ClothSim::applyFriction(int id, const Vec3& normal) {
    // Kecepatan setelah dorong: komponen normal dipertahankan, komponen
    // tangensial diredam (gesekan) — cermin perilaku tabrakan partikel.
    Vec3& p = pos_[id];
    Vec3 v = p - prev_[id];
    const float vn = v.x * normal.x + v.y * normal.y + v.z * normal.z;
    Vec3 vt = {v.x - vn * normal.x, v.y - vn * normal.y, v.z - vn * normal.z};
    vt = vt * (1.0f - friction_);
    prev_[id] = {p.x - (vn * normal.x + vt.x), p.y - (vn * normal.y + vt.y),
                 p.z - (vn * normal.z + vt.z)};
}

void ClothSim::pushVertices(const uint32_t* ids, int count, const Vec3& dir,
                            float push) {
    const Vec3 shift = dir * push;
    for (int k = 0; k < count; k++) pos_[ids[k]] = pos_[ids[k]] + shift;
    for (int k = 0; k < count; k++) applyFriction(ids[k], dir);
}

void ClothSim::collideTriangles() {
    // 1) Centroid tiap segitiga.
    for (int t = 0; t < triangleCount(); t++) {
        const uint32_t a = indices_[t * 3];
        const uint32_t b = indices_[t * 3 + 1];
        const uint32_t c = indices_[t * 3 + 2];
        Vec3 cen = (pos_[a] + pos_[b] + pos_[c]) * (1.0f / 3.0f);
        Vec3 d = cen - kSphereC;
        const float dist = length(d);
        if (dist >= kCollideR) continue;
        if (dist < 1e-6f) {
            d = {0.0f, 1.0f, 0.0f};
        } else {
            d = d * (1.0f / dist);
        }
        const uint32_t ids[3] = {a, b, c};
        pushVertices(ids, 3, d, kCollideR - dist);
    }
    // 2) Titik tengah tiap sisi struktural (horizontal + vertikal).
    for (int j = 0; j <= n_; j++) {
        for (int i = 0; i <= n_; i++) {
            const uint32_t id = j * (n_ + 1) + i;
            if (i < n_) collideEdgeMidpoint(id, id + 1);
            if (j < n_) collideEdgeMidpoint(id, id + (n_ + 1));
        }
    }
    // 3) Bidang segitiga: centroid di luar saja tidak cukup — segitiga
    // besar yang miring bisa memotong bola walau ketiga vertex + centroid
    // di luar (terukur sisa: Face 1: 0.33, Face 2: 0.046). Bila jarak
    // bidang ke pusat bola < R dan kaki tegak lurus jatuh di dalam
    // segitiga, geser seluruh segitiga sepanjang normal bidang sampai
    // jarak bidang = R.
    for (int t = 0; t < triangleCount(); t++) {
        const uint32_t a = indices_[t * 3];
        const uint32_t b = indices_[t * 3 + 1];
        const uint32_t c = indices_[t * 3 + 2];
        Vec3 nrm = cross(pos_[b] - pos_[a], pos_[c] - pos_[a]);
        const float nl = length(nrm);
        if (nl < 1e-9f) continue;
        nrm = nrm * (1.0f / nl);
        const float s = (kSphereC.x - pos_[a].x) * nrm.x +
                        (kSphereC.y - pos_[a].y) * nrm.y +
                        (kSphereC.z - pos_[a].z) * nrm.z;
        if (std::fabs(s) >= kCollideR) continue;
        // Kaki tegak lurus dari pusat ke bidang.
        Vec3 foot = {kSphereC.x - nrm.x * s, kSphereC.y - nrm.y * s,
                     kSphereC.z - nrm.z * s};
        if (!pointInTriangle(foot, pos_[a], pos_[b], pos_[c])) continue;
        // Geser bidang menjauhi pusat: t = -sign(s)*(R-|s|)*n.
        const float push = kCollideR - std::fabs(s);
        const Vec3 dir = (s > 0.0f) ? nrm * -1.0f : nrm;
        const uint32_t ids[3] = {a, b, c};
        pushVertices(ids, 3, dir, push);
    }
}

bool ClothSim::pointInTriangle(const Vec3& p, const Vec3& a, const Vec3& b,
                               const Vec3& c) {
    const Vec3 v0 = b - a, v1 = c - a, v2 = p - a;
    const float d00 = v0.x * v0.x + v0.y * v0.y + v0.z * v0.z;
    const float d01 = v0.x * v1.x + v0.y * v1.y + v0.z * v1.z;
    const float d11 = v1.x * v1.x + v1.y * v1.y + v1.z * v1.z;
    const float d20 = v2.x * v0.x + v2.y * v0.y + v2.z * v0.z;
    const float d21 = v2.x * v1.x + v2.y * v1.y + v2.z * v1.z;
    const float den = d00 * d11 - d01 * d01;
    if (std::fabs(den) < 1e-12f) return false;
    const float v = (d11 * d20 - d01 * d21) / den;
    const float w = (d00 * d21 - d01 * d20) / den;
    const float u = 1.0f - v - w;
    const float eps = 1e-4f;
    return u >= -eps && v >= -eps && w >= -eps;
}

void ClothSim::collideEdgeMidpoint(uint32_t a, uint32_t b) {
    Vec3 mid = (pos_[a] + pos_[b]) * 0.5f;
    Vec3 d = mid - kSphereC;
    const float dist = length(d);
    if (dist >= kCollideR) return;
    if (dist < 1e-6f) {
        d = {0.0f, 1.0f, 0.0f};
    } else {
        d = d * (1.0f / dist);
    }
    const uint32_t ids[2] = {a, b};
    pushVertices(ids, 2, d, kCollideR - dist);
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

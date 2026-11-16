// Verlet cloth (position-based dynamics) — Hozin Cloth Sim.
// Grid kain jatuh oleh gravitasi, bertabrakan dengan bola + lantai.
// Tanpa self-collision (sengaja: terlalu berat untuk real-time di HP).
#pragma once

#include <cstdint>
#include <vector>

namespace cloth {

struct Vec3 {
    float x, y, z;
};

class ClothSim {
public:
    // segments: jumlah segmen per sisi (1, 2, 4, 8, 16, 32, 64, 256).
    // face (quad) = segments^2. Ukuran kain 3.4 x 3.4, bola r=1 di (0,1,0).
    ClothSim(int segments);
    void reset();
    // dt: detik (tetap 1/60 dari pemanggil). windStrength 0..1, time: detik berjalan.
    void step(float dt, float windStrength, float time);

    int segments() const { return n_; }
    int particleCount() const { return (n_ + 1) * (n_ + 1); }
    int triangleCount() const { return 2 * n_ * n_; }
    int indexCount() const { return triangleCount() * 3; }
    int iterations() const { return iterations_; }
    const float* positions() const { return reinterpret_cast<const float*>(pos_.data()); }
    const float* normals() const { return reinterpret_cast<const float*>(normals_.data()); }
    const uint32_t* indices() const { return indices_.data(); }

private:
    int n_;
    int iterations_;
    float friction_;
    std::vector<Vec3> pos_;
    std::vector<Vec3> prev_;
    std::vector<Vec3> normals_;
    std::vector<uint32_t> indices_;
    struct Constraint {
        uint32_t a, b;
        float rest;
    };
    std::vector<Constraint> cons_;

    void satisfyConstraints();
    void collide();
    void collideTriangles();
    void collideEdgeMidpoint(uint32_t a, uint32_t b);
    bool pointInTriangle(const Vec3& p, const Vec3& a, const Vec3& b,
                         const Vec3& c);
    // Geser vertex keluar sepanjang dir sejauh push + gesekan (cermin
    // perilaku tabrakan partikel).
    void pushVertices(const uint32_t* ids, int count, const Vec3& dir, float push);
    void applyFriction(int id, const Vec3& normal);
    void computeNormals();
};

}  // namespace cloth

package com.hozinking.clothsim

import kotlin.math.sqrt

/**
 * Logika murni (tanpa Android / tanpa JNI) — bisa di-unit-test.
 * FACE_LEVELS = jumlah quad kain, sama seperti angka FACE di video referensi
 * (1, 4, 16, ... 65536). Segmen per sisi = akar kuadrat jumlah face.
 */
object ClothSpec {

    val FACE_LEVELS = intArrayOf(1, 4, 16, 64, 256, 1024, 4096, 65536)

    fun segmentsForFaces(faces: Int): Int = sqrt(faces.toDouble()).toInt()

    fun particleCount(segments: Int): Int = (segments + 1) * (segments + 1)

    fun triangleCount(segments: Int): Int = 2 * segments * segments

    /** Iterasi constraint adaptif — cermin dari cloth.cpp. */
    fun iterationsForSegments(segments: Int): Int =
        if (segments <= 16) 4 else if (segments <= 64) 3 else 2

    // ---- Cermin matematika C++ (untuk unit test) ----

    /** Satu langkah verlet: pos baru dari pos, pos sebelumnya, percepatan. */
    fun verletStep(
        pos: Float, prev: Float, accel: Float, dt: Float, damping: Float
    ): Float {
        val vel = (pos - prev) * damping
        return pos + vel + accel * dt * dt
    }

    /**
     * Dorong titik keluar dari bola (cermin ClothSim::collide).
     * Kembalikan posisi terkoreksi [x, y, z].
     */
    fun spherePushOut(
        px: Float, py: Float, pz: Float,
        cx: Float, cy: Float, cz: Float, r: Float
    ): FloatArray {
        val dx = px - cx
        val dy = py - cy
        val dz = pz - cz
        val dist = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        if (dist >= r) return floatArrayOf(px, py, pz)
        if (dist < 1e-6f) return floatArrayOf(cx, cy + r, cz)
        val s = r / dist
        return floatArrayOf(cx + dx * s, cy + dy * s, cz + dz * s)
    }

    // ---- Preset warna (RGB 0..1) ----

    data class ColorPreset(val name: String, val rgb: FloatArray)

    val CLOTH_COLORS = listOf(
        ColorPreset("Putih", floatArrayOf(0.92f, 0.92f, 0.90f)),
        ColorPreset("Merah", floatArrayOf(0.85f, 0.15f, 0.18f)),
        ColorPreset("Teal", floatArrayOf(0.10f, 0.65f, 0.62f)),
        ColorPreset("Ungu", floatArrayOf(0.55f, 0.30f, 0.85f)),
        ColorPreset("Hitam", floatArrayOf(0.12f, 0.12f, 0.14f))
    )

    val BALL_COLORS = listOf(
        ColorPreset("Emas", floatArrayOf(0.95f, 0.72f, 0.15f)),
        ColorPreset("Perak", floatArrayOf(0.80f, 0.82f, 0.86f)),
        ColorPreset("Tembaga", floatArrayOf(0.85f, 0.45f, 0.22f))
    )
}

package com.hozinking.clothsim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClothMathTest {

    @Test
    fun faceLevels_matchVideoSteps() {
        // Langkah diskrit sama seperti video referensi: 1, 4, 16, ... 65536.
        assertEquals(8, ClothSpec.FACE_LEVELS.size)
        assertEquals(1, ClothSpec.FACE_LEVELS[0])
        assertEquals(65536, ClothSpec.FACE_LEVELS[7])
        for (faces in ClothSpec.FACE_LEVELS) {
            val n = ClothSpec.segmentsForFaces(faces)
            assertEquals(faces, n * n) // face = segmen^2 (quad)
        }
    }

    @Test
    fun gridCounts_areConsistent() {
        for (faces in ClothSpec.FACE_LEVELS) {
            val n = ClothSpec.segmentsForFaces(faces)
            assertEquals((n + 1) * (n + 1), ClothSpec.particleCount(n))
            assertEquals(2 * faces, ClothSpec.triangleCount(n))
        }
        assertEquals(4, ClothSpec.particleCount(1)) // grid 1x1 = 4 partikel
        assertEquals(66049, ClothSpec.particleCount(256)) // 257^2
        assertEquals(131072, ClothSpec.triangleCount(256))
    }

    @Test
    fun iterations_adaptToGridSize() {
        assertEquals(4, ClothSpec.iterationsForSegments(1))
        assertEquals(4, ClothSpec.iterationsForSegments(16))
        assertEquals(3, ClothSpec.iterationsForSegments(64))
        assertEquals(2, ClothSpec.iterationsForSegments(256))
    }

    @Test
    fun verletStep_fallsUnderGravity() {
        // Partikel diam di y=10, dt=1/60, g=-9.8: jatuh sejauh g*dt^2.
        val y = ClothSpec.verletStep(10f, 10f, -9.8f, 1f / 60f, 1f)
        val expected = 10f - 9.8f / 3600f
        assertTrue(abs(y - expected) < 1e-6f)
    }

    @Test
    fun verletStep_keepsVelocity() {
        // Bergerak ke atas 1 unit/frame (prev 1 di bawah pos), tanpa gravitasi.
        val y = ClothSpec.verletStep(5f, 4f, 0f, 1f / 60f, 1f)
        assertTrue(abs(y - 6f) < 1e-6f)
    }

    @Test
    fun spherePushOut_outsideUnchanged() {
        val out = ClothSpec.spherePushOut(3f, 1f, 0f, 0f, 1f, 0f, 1f)
        assertEquals(3f, out[0], 1e-6f)
        assertEquals(1f, out[1], 1e-6f)
        assertEquals(0f, out[2], 1e-6f)
    }

    @Test
    fun spherePushOut_insidePushedToSurface() {
        // Titik di dalam bola (jarak 0.5 dari pusat, r=1) -> ke permukaan.
        val out = ClothSpec.spherePushOut(0.5f, 1f, 0f, 0f, 1f, 0f, 1f)
        assertEquals(1f, out[0], 1e-6f)
        assertEquals(1f, out[1], 1e-6f)
        assertEquals(0f, out[2], 1e-6f)
        // Jarak hasil ke pusat harus tepat r.
        val d = kotlin.math.sqrt(
            (out[0] * out[0] + (out[1] - 1f) * (out[1] - 1f) + out[2] * out[2]).toDouble()
        )
        assertTrue(abs(d - 1.0) < 1e-5)
    }

    @Test
    fun spherePushOut_centerGoesUp() {
        // Titik tepat di pusat (degenerate) -> keluar ke atas.
        val out = ClothSpec.spherePushOut(0f, 1f, 0f, 0f, 1f, 0f, 1f)
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(2f, out[1], 1e-6f)
        assertEquals(0f, out[2], 1e-6f)
    }

    @Test
    fun sphereMesh_counts() {
        val m = SphereMesh.generate(48, 32, 1.0f, 1.0f)
        assertEquals(49 * 33 * 3, m.positions.size)
        assertEquals(49 * 33 * 3, m.normals.size)
        assertEquals(48 * 32 * 6, m.indices.size)
        // Normal pertama (kutub atas) = (0,1,0).
        assertEquals(0f, m.normals[0], 1e-6f)
        assertEquals(1f, m.normals[1], 1e-6f)
        assertEquals(0f, m.normals[2], 1e-6f)
        // Semua vertex ada di permukaan bola (jarak r dari pusat).
        for (v in 0 until 49 * 33) {
            val dx = m.positions[v * 3]
            val dy = m.positions[v * 3 + 1] - 1.0f
            val dz = m.positions[v * 3 + 2]
            val d = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
            assertTrue(abs(d - 1.0) < 1e-4)
        }
    }
}

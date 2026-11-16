package com.hozinking.clothsim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClothMathTest {

    @Test
    fun faceLevels_matchVideoSteps() {
        // 18 tingkat rapat: 1, 4, 9, 16, ... 262144 (grid 1²..512²).
        assertEquals(18, ClothSpec.FACE_LEVELS.size)
        assertEquals(1, ClothSpec.FACE_LEVELS[0])
        assertEquals(262144, ClothSpec.FACE_LEVELS[17])
        assertEquals(
            intArrayOf(
                1, 4, 9, 16, 36, 64, 100, 256, 400, 900,
                1024, 1600, 2500, 4096, 10000, 16384, 65536, 262144
            ).toList(), ClothSpec.FACE_LEVELS.toList()
        )
        for (faces in ClothSpec.FACE_LEVELS) {
            val n = ClothSpec.segmentsForFaces(faces)
            assertEquals(faces, n * n) // face = segmen^2 (quad)
        }
    }

    @Test
    fun faceLabelSuffix_marksHeavyLevels() {
        assertEquals("", ClothSpec.faceLabelSuffix(1024))
        assertEquals("", ClothSpec.faceLabelSuffix(16384))
        assertEquals(" — Ultra, berat", ClothSpec.faceLabelSuffix(65536))
        assertEquals(
            " — Ekstrem, sangat berat", ClothSpec.faceLabelSuffix(262144)
        )
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
        assertEquals(263169, ClothSpec.particleCount(512)) // 513^2
        assertEquals(524288, ClothSpec.triangleCount(512))
    }

    @Test
    fun iterations_adaptToGridSize() {
        assertEquals(4, ClothSpec.iterationsForSegments(1))
        assertEquals(4, ClothSpec.iterationsForSegments(16))
        assertEquals(3, ClothSpec.iterationsForSegments(64))
        assertEquals(4, ClothSpec.iterationsForSegments(100))
        assertEquals(4, ClothSpec.iterationsForSegments(128))
        assertEquals(4, ClothSpec.iterationsForSegments(256))
        assertEquals(4, ClothSpec.iterationsForSegments(512))
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

    @Test
    fun gold_isYellow_notCopperOrange() {
        // v1.0: Emas tampil oranye mirip Tembaga di sisi gelap. Emas harus
        // kuning jelas: hijau tinggi, biru rendah, dan hijau jauh di atas
        // Tembaga supaya beda walau di bayangan.
        val gold = ClothSpec.BALL_COLORS.first { it.name == "Emas" }.rgb
        val copper = ClothSpec.BALL_COLORS.first { it.name == "Tembaga" }.rgb
        assertTrue(gold[0] > 0.95f) // merah penuh
        assertTrue(gold[1] > 0.8f) // hijau tinggi = kuning, bukan oranye
        assertTrue(gold[2] < 0.4f)
        assertTrue(gold[1] - copper[1] > 0.3f) // beda jelas dari Tembaga
        assertTrue(copper[0] - copper[1] > 0.3f) // Tembaga tetap kemerahan
    }

    @Test
    fun caveWall_isClosedRing() {
        val m = CaveMesh.wall()
        assertEquals(49 * 2 * 3, m.positions.size)
        assertEquals(49 * 2 * 3, m.normals.size)
        assertEquals(48 * 6, m.indices.size)
        for (v in 0 until 49 * 2) {
            val x = m.positions[v * 3]
            val y = m.positions[v * 3 + 1]
            val z = m.positions[v * 3 + 2]
            val r = kotlin.math.sqrt((x * x + z * z).toDouble())
            assertTrue("radius dinding $r", r > 11.4 && r < 12.6)
            assertTrue("y dinding $y", y == 0f || y == CaveMesh.CEILING_Y)
            // Normal menghadap ke dalam (berlawanan arah radial keluar).
            val nx = m.normals[v * 3]
            val nz = m.normals[v * 3 + 2]
            assertTrue(nx * x + nz * z < 0)
        }
    }

    @Test
    fun caveCeilingAndFloor_areFlatDiscs() {
        val ceil = CaveMesh.ceiling()
        assertEquals(50 * 3, ceil.positions.size)
        assertEquals(48 * 3, ceil.indices.size)
        for (v in 0 until 50) {
            assertEquals(CaveMesh.CEILING_Y, ceil.positions[v * 3 + 1], 1e-6f)
            assertEquals(0f, ceil.normals[v * 3], 1e-6f)
            assertEquals(-1f, ceil.normals[v * 3 + 1], 1e-6f)
        }
        val floor = CaveMesh.floor()
        assertEquals(50 * 3, floor.positions.size)
        assertEquals(48 * 3, floor.indices.size)
        for (v in 0 until 50) {
            assertEquals(0f, floor.positions[v * 3 + 1], 1e-6f)
            assertEquals(1f, floor.normals[v * 3 + 1], 1e-6f)
        }
        val stones = CaveMesh.stonePatches()
        assertEquals(6 * 14 * 3, stones.positions.size)
        assertEquals(6 * 12 * 3, stones.indices.size)
        for (v in 0 until 6 * 14) {
            assertEquals(0.005f, stones.positions[v * 3 + 1], 1e-6f)
        }
    }

    @Test
    fun caveCones_hangAndStand() {
        val m = CaveMesh.stalactites()
        // 14 kerucut x 8 sisi x 3 vertex (non-indexed, normal per muka).
        assertEquals(14 * 8 * 3 * 3, m.positions.size)
        assertEquals(14 * 8 * 3, m.indices.size)
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var tipLow = 0 // ujung stalaktit (y 6.5..8)
        var tipHigh = 0 // ujung stalagmit (y 1..2.2)
        for (v in 0 until 14 * 8 * 3) {
            val y = m.positions[v * 3 + 1]
            val nx = m.normals[v * 3]
            val ny = m.normals[v * 3 + 1]
            val nz = m.normals[v * 3 + 2]
            assertTrue("posisi finite", y.isFinite())
            assertTrue(
                "normal satuan",
                abs(kotlin.math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()) - 1.0) < 1e-4
            )
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (y >= 6.5f && y <= 8f) tipLow++
            if (y >= 1f && y <= 2.2f) tipHigh++
        }
        assertEquals(0f, minY, 1e-6f) // dasar stalagmit di lantai
        assertEquals(CaveMesh.CEILING_Y, maxY, 1e-6f) // dasar stalaktit
        // 8 stalaktit x 8 sisi = 64 vertex ujung (y 6.5..8);
        // 6 stalagmit x 8 sisi = 48 vertex ujung (y 1..2.2).
        assertEquals(64, tipLow)
        assertEquals(48, tipHigh)
    }
}

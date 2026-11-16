package com.hozinking.clothsim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometri gua bawah tanah — logika murni, bisa di-unit-test.
 * Semua mesh statis (dibangun sekali di onSurfaceCreated), murah:
 * dinding ~200 segitiga, langit-langit 48, lantai ~100, stalaktit/
 * stalagmit ~200. Normal menghadap ke dalam gua (dinding/langit-langit)
 * atau ke atas (lantai) supaya pencahayaan hangat bekerja.
 *
 * Dimensi gua: dinding radius 12, langit-langit y=10. Kamera di mode gua
 * dibatasi jarak <= 10 dan elevasi <= 1.05 supaya tetap di dalam gua.
 */
object CaveMesh {

    data class Mesh(
        val positions: FloatArray, // xyz per vertex
        val normals: FloatArray,   // xyz per vertex
        val indices: IntArray      // 3 per segitiga
    )

    const val WALL_RADIUS = 12f
    const val CEILING_Y = 10f
    private const val WALL_SEGMENTS = 48

    /** Hash deterministik 0..1 (tanpa Random — hasil stabil tiap run). */
    fun hash(i: Int): Float {
        val s = sin(i * 12.9898f) * 43758.5453f
        return s - kotlin.math.floor(s)
    }

    /** Radius dinding per segmen (jitter +-0.6 supaya batu tidak mulus). */
    fun wallRadius(segment: Int): Float =
        WALL_RADIUS + (hash(segment * 7 + 3) - 0.5f) * 1.2f

    /** Dinding silinder: 2 ring (y=0, y=10), normal menghadap ke pusat. */
    fun wall(): Mesh {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val idx = ArrayList<Int>()
        val cols = WALL_SEGMENTS + 1
        for (ring in 0..1) {
            val y = if (ring == 0) 0f else CEILING_Y
            for (i in 0..WALL_SEGMENTS) {
                val phi = 2.0 * PI * i / WALL_SEGMENTS
                val r = wallRadius(i % WALL_SEGMENTS)
                val cx = cos(phi).toFloat()
                val sz = sin(phi).toFloat()
                pos.add(cx * r); pos.add(y); pos.add(sz * r)
                // Normal ke dalam (ke pusat), abaikan jitter.
                nrm.add(-cx); nrm.add(0f); nrm.add(-sz)
            }
        }
        for (i in 0 until WALL_SEGMENTS) {
            val a = i
            val b = a + 1
            val c = a + cols
            val d = c + 1
            idx.add(a); idx.add(c); idx.add(b)
            idx.add(b); idx.add(c); idx.add(d)
        }
        return Mesh(pos.toFloatArray(), nrm.toFloatArray(), idx.toIntArray())
    }

    /** Langit-langit: kipas cakram di y=10, normal menghadap ke bawah. */
    fun ceiling(): Mesh {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val idx = ArrayList<Int>()
        pos.add(0f); pos.add(CEILING_Y); pos.add(0f)
        nrm.add(0f); nrm.add(-1f); nrm.add(0f)
        for (i in 0..WALL_SEGMENTS) {
            val phi = 2.0 * PI * i / WALL_SEGMENTS
            val r = wallRadius(i % WALL_SEGMENTS)
            pos.add(cos(phi).toFloat() * r)
            pos.add(CEILING_Y)
            pos.add(sin(phi).toFloat() * r)
            nrm.add(0f); nrm.add(-1f); nrm.add(0f)
        }
        for (i in 1..WALL_SEGMENTS) {
            idx.add(0); idx.add(i + 1); idx.add(i)
        }
        return Mesh(pos.toFloatArray(), nrm.toFloatArray(), idx.toIntArray())
    }

    /** Lantai tanah: kipas cakram radius 12 di y=0, normal ke atas. */
    fun floor(): Mesh {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val idx = ArrayList<Int>()
        pos.add(0f); pos.add(0f); pos.add(0f)
        nrm.add(0f); nrm.add(1f); nrm.add(0f)
        for (i in 0..WALL_SEGMENTS) {
            val phi = 2.0 * PI * i / WALL_SEGMENTS
            pos.add(cos(phi).toFloat() * WALL_RADIUS)
            pos.add(0f)
            pos.add(sin(phi).toFloat() * WALL_RADIUS)
            nrm.add(0f); nrm.add(1f); nrm.add(0f)
        }
        for (i in 1..WALL_SEGMENTS) {
            idx.add(0); idx.add(i); idx.add(i + 1)
        }
        return Mesh(pos.toFloatArray(), nrm.toFloatArray(), idx.toIntArray())
    }

    /**
     * Petak batu di lantai: 6 cakram kecil (radius 0.8-1.5) di y=0.005,
     * posisi deterministik — memberi tekstur tanpa tekstur gambar.
     */
    fun stonePatches(): Mesh {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val idx = ArrayList<Int>()
        var base = 0
        val spots = listOf(
            Triple(4.5f, 0.6f, 1.4f), Triple(-5.5f, 2.1f, 1.1f),
            Triple(7.0f, 2.8f, 0.9f), Triple(-3.0f, 4.0f, 1.5f),
            Triple(1.5f, 5.2f, 1.0f), Triple(-8.0f, 5.6f, 1.2f)
        )
        for ((cx, cz, r) in spots) {
            pos.add(cx); pos.add(0.005f); pos.add(cz)
            nrm.add(0f); nrm.add(1f); nrm.add(0f)
            for (i in 0..12) {
                val phi = 2.0 * PI * i / 12
                pos.add(cx + cos(phi).toFloat() * r)
                pos.add(0.005f)
                pos.add(cz + sin(phi).toFloat() * r)
                nrm.add(0f); nrm.add(1f); nrm.add(0f)
            }
            for (i in 1..12) {
                idx.add(base); idx.add(base + i); idx.add(base + i + 1)
            }
            base += 14
        }
        return Mesh(pos.toFloatArray(), nrm.toFloatArray(), idx.toIntArray())
    }

    /** Satu kerucut (stalaktit menggantung / stalagmit berdiri). */
    private fun cone(
        cx: Float, cz: Float, baseY: Float, height: Float, radius: Float,
        sides: Int, down: Boolean,
        pos: ArrayList<Float>, nrm: ArrayList<Float>, idx: ArrayList<Int>
    ) {
        val apexY = if (down) baseY - height else baseY + height
        val apex = Triple(cx, apexY, cz)
        val base = ArrayList<Triple<Float, Float, Float>>()
        for (i in 0..sides) {
            val phi = 2.0 * PI * i / sides
            base.add(Triple(
                cx + cos(phi).toFloat() * radius, baseY,
                cz + sin(phi).toFloat() * radius
            ))
        }
        // Sisi kerucut: normal per muka (faceted — cocok untuk batu).
        var vBase = pos.size / 3
        for (i in 0 until sides) {
            val a = apex
            val b = base[i]
            val c = base[i + 1]
            val n = faceNormal(a, b, c)
            // Balik normal bila menghadap ke dalam kerucut.
            val midX = (b.first + c.first) / 2 - cx
            val midZ = (b.third + c.third) / 2 - cz
            val outward = n.first * midX + n.third * midZ
            val nn = if (outward < 0) Triple(-n.first, -n.second, -n.third) else n
            for (v in listOf(a, b, c)) {
                pos.add(v.first); pos.add(v.second); pos.add(v.third)
                nrm.add(nn.first); nrm.add(nn.second); nrm.add(nn.third)
            }
            idx.add(vBase); idx.add(vBase + 1); idx.add(vBase + 2)
            vBase += 3
        }
    }

    private fun faceNormal(
        a: Triple<Float, Float, Float>,
        b: Triple<Float, Float, Float>,
        c: Triple<Float, Float, Float>
    ): Triple<Float, Float, Float> {
        val ux = b.first - a.first
        val uy = b.second - a.second
        val uz = b.third - a.third
        val vx = c.first - a.first
        val vy = c.second - a.second
        val vz = c.third - a.third
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val l = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
        if (l < 1e-9f) return Triple(0f, 1f, 0f)
        nx /= l; ny /= l; nz /= l
        return Triple(nx, ny, nz)
    }

    /** 8 stalaktit (dari langit-langit) + 6 stalagmit (dari lantai). */
    fun stalactites(): Mesh {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val idx = ArrayList<Int>()
        // Stalaktit: sebar di radius 4-10, panjang 2-3.5 (ujung y 6.5-8).
        for (k in 0 until 8) {
            val ang = 2.0 * PI * k / 8 + 0.35
            val rad = 4f + hash(k * 11 + 1) * 6f
            val len = 2f + hash(k * 13 + 5) * 1.5f
            val r = 0.35f + hash(k * 17 + 9) * 0.25f
            cone(
                (cos(ang) * rad).toFloat(), (sin(ang) * rad).toFloat(),
                CEILING_Y, len, r, 8, true, pos, nrm, idx
            )
        }
        // Stalagmit: radius 5-11, tinggi 1-2.2.
        for (k in 0 until 6) {
            val ang = 2.0 * PI * k / 6 + 0.9
            val rad = 5f + hash(k * 19 + 2) * 6f
            val h = 1f + hash(k * 23 + 7) * 1.2f
            val r = 0.4f + hash(k * 29 + 4) * 0.3f
            cone(
                (cos(ang) * rad).toFloat(), (sin(ang) * rad).toFloat(),
                0f, h, r, 8, false, pos, nrm, idx
            )
        }
        return Mesh(pos.toFloatArray(), nrm.toFloatArray(), idx.toIntArray())
    }
}

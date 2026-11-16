package com.hozinking.clothsim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mesh bola UV (sphere) — logika murni, bisa di-unit-test.
 * Bola digambar sekali (statis), pusat (0, cy, 0).
 */
object SphereMesh {

    data class Mesh(
        val positions: FloatArray, // xyz per vertex
        val normals: FloatArray,   // xyz per vertex
        val indices: IntArray      // 3 per segitiga
    )

    fun generate(latSegments: Int, lonSegments: Int, radius: Float, cy: Float): Mesh {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val idx = ArrayList<Int>()
        for (lat in 0..latSegments) {
            val theta = PI * lat / latSegments // 0..PI
            val sinT = sin(theta).toFloat()
            val cosT = cos(theta).toFloat()
            for (lon in 0..lonSegments) {
                val phi = 2.0 * PI * lon / lonSegments
                val sinP = sin(phi).toFloat()
                val cosP = cos(phi).toFloat()
                val nx = sinT * cosP
                val ny = cosT
                val nz = sinT * sinP
                pos.add(nx * radius)
                pos.add(cy + ny * radius)
                pos.add(nz * radius)
                nrm.add(nx)
                nrm.add(ny)
                nrm.add(nz)
            }
        }
        val ring = lonSegments + 1
        for (lat in 0 until latSegments) {
            for (lon in 0 until lonSegments) {
                val a = lat * ring + lon
                val b = a + 1
                val c = a + ring
                val d = c + 1
                idx.add(a); idx.add(c); idx.add(b)
                idx.add(b); idx.add(c); idx.add(d)
            }
        }
        return Mesh(pos.toFloatArray(), nrm.toFloatArray(), idx.toIntArray())
    }
}

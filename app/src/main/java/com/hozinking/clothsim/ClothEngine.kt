package com.hozinking.clothsim

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * JNI wrapper untuk cloth::ClothSim (C++).
 * Posisi + normal dibaca lewat DirectByteBuffer (tanpa salinan per frame).
 * Buffer TIDAK VALID setelah recreate() — panggil refreshBuffers() sesudahnya.
 */
class ClothEngine {

    companion object {
        init {
            System.loadLibrary("clothsim")
        }
    }

    private var handle: Long = 0
    private var posBuffer: FloatBuffer? = null
    private var nrmBuffer: FloatBuffer? = null
    private var idxBuffer: IntBuffer? = null

    var particleCount: Int = 0
        private set
    var indexCount: Int = 0
        private set
    var iterations: Int = 0
        private set

    fun create(segments: Int) {
        destroy()
        handle = nativeCreate(segments)
        particleCount = nativeParticleCount(handle)
        indexCount = nativeIndexCount(handle)
        iterations = nativeIterations(handle)
        refreshBuffers()
    }

    fun destroy() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
        posBuffer = null
        nrmBuffer = null
        idxBuffer = null
    }

    fun reset() {
        if (handle != 0L) nativeReset(handle)
    }

    fun step(dt: Float, windStrength: Float, time: Float) {
        if (handle != 0L) nativeStep(handle, dt, windStrength, time)
    }

    fun refreshBuffers() {
        if (handle == 0L) return
        posBuffer = nativePositions(handle)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        nrmBuffer = nativeNormals(handle)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        idxBuffer = nativeIndices(handle)
            .order(ByteOrder.nativeOrder()).asIntBuffer()
    }

    fun positions(): FloatBuffer? = posBuffer
    fun normals(): FloatBuffer? = nrmBuffer
    fun indices(): IntBuffer? = idxBuffer

    private external fun nativeCreate(segments: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeReset(handle: Long)
    private external fun nativeStep(handle: Long, dt: Float, wind: Float, time: Float)
    private external fun nativePositions(handle: Long): ByteBuffer
    private external fun nativeNormals(handle: Long): ByteBuffer
    private external fun nativeIndices(handle: Long): ByteBuffer
    private external fun nativeParticleCount(handle: Long): Int
    private external fun nativeIndexCount(handle: Long): Int
    private external fun nativeIterations(handle: Long): Int
}

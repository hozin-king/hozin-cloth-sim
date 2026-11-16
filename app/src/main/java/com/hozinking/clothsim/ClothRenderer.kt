package com.hozinking.clothsim

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderer OpenGL ES 3.0 — satu program shader untuk semua objek.
 * Tanpa VBO (client-side array) supaya tahan context loss.
 * Kain: posisi + normal dibaca langsung dari DirectByteBuffer native.
 */
class ClothRenderer(
    private val engine: ClothEngine
) : GLSurfaceView.Renderer {

    // ---- state yang diubah dari UI thread ----
    @Volatile var paused: Boolean = false
    @Volatile var windStrength: Float = 0f
    @Volatile var clothColor: FloatArray = floatArrayOf(0.92f, 0.92f, 0.90f)
    @Volatile var ballColor: FloatArray = floatArrayOf(1.00f, 0.84f, 0.30f)
    @Volatile var azimuth: Float = -0.7f
    @Volatile var elevation: Float = 0.55f
    @Volatile var distance: Float = 7.5f
    @Volatile var caveMode: Boolean = true // default: gua bawah tanah
    var fpsListener: ((Int) -> Unit)? = null

    private var program: Int = 0
    private var uMvp: Int = 0
    private var uColor: Int = 0
    private var uLightDir: Int = 0
    private var uLightColor: Int = 0
    private var uAmbient: Int = 0
    private var uFogColor: Int = 0
    private var uFogNear: Int = 0
    private var uFogFar: Int = 0
    private var uSpec: Int = 0
    private var uCamPos: Int = 0
    private var aPos: Int = 0
    private var aNormal: Int = 0

    private val projM = FloatArray(16)
    private val viewM = FloatArray(16)
    private val mvpM = FloatArray(16)
    private val eye = FloatArray(3)

    private var spherePos: FloatBuffer? = null
    private var sphereNrm: FloatBuffer? = null
    private var sphereIdx: IntBuffer? = null
    private var sphereIdxCount: Int = 0
    private var floorBuf: FloatBuffer? = null
    private var gridBuf: FloatBuffer? = null
    private var gridVertCount: Int = 0
    // Mesh gua (dibangun sekali, statis) — buffer dikonversi sekali di
    // onSurfaceCreated, bukan tiap frame.
    private class StaticDraw(
        val pos: FloatBuffer, val nrm: FloatBuffer,
        val idx: IntBuffer, val count: Int
    )

    private var caveWall: StaticDraw? = null
    private var caveCeiling: StaticDraw? = null
    private var caveFloor: StaticDraw? = null
    private var caveStones: StaticDraw? = null
    private var caveCones: StaticDraw? = null

    private fun toStatic(mesh: CaveMesh.Mesh): StaticDraw = StaticDraw(
        toFloatBuffer(mesh.positions), toFloatBuffer(mesh.normals),
        toIntBuffer(mesh.indices), mesh.indices.size
    )

    private var lastTimeNs: Long = 0
    private var simTime: Float = 0f
    private var acc: Float = 0f
    private var frames: Int = 0
    private var fpsWindowStart: Long = 0

    companion object {
        private const val DT = 1f / 60f
        private val LIGHT_DIR = floatArrayOf(0.5f, 0.8f, 0.4f)
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(sh)
            GLES30.glDeleteShader(sh)
            throw RuntimeException("Shader gagal: $log")
        }
        return sh
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.07f, 0.05f, 0.04f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE) // kain double-sided
        val vs = compile(GLES30.GL_VERTEX_SHADER, """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vWorld;
            void main() {
                vWorld = aPos;
                vNormal = aNormal;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """.trimIndent())
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, """
            precision mediump float;
            uniform vec3 uColor;
            uniform vec3 uLightDir;
            uniform vec3 uLightColor;
            uniform float uAmbient;
            uniform vec3 uFogColor;
            uniform float uFogNear;
            uniform float uFogFar;
            uniform float uSpec;
            uniform vec3 uCamPos;
            varying vec3 vNormal;
            varying vec3 vWorld;
            void main() {
                vec3 n = normalize(vNormal);
                if (!gl_FrontFacing) n = -n;
                vec3 L = normalize(uLightDir);
                float diff = max(dot(n, L), 0.0);
                vec3 col = uColor * uLightColor * (uAmbient + (1.0 - uAmbient) * diff);
                if (uSpec > 0.0) {
                    vec3 v = normalize(uCamPos - vWorld);
                    vec3 h = normalize(L + v);
                    float s = pow(max(dot(n, h), 0.0), 64.0) * uSpec;
                    col += vec3(1.0, 0.95, 0.8) * s;
                }
                float fogF = clamp((distance(uCamPos, vWorld) - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);
                col = mix(col, uFogColor, fogF);
                gl_FragColor = vec4(col, 1.0);
            }
        """.trimIndent())
        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        uMvp = GLES30.glGetUniformLocation(program, "uMVP")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uLightDir = GLES30.glGetUniformLocation(program, "uLightDir")
        uLightColor = GLES30.glGetUniformLocation(program, "uLightColor")
        uAmbient = GLES30.glGetUniformLocation(program, "uAmbient")
        uFogColor = GLES30.glGetUniformLocation(program, "uFogColor")
        uFogNear = GLES30.glGetUniformLocation(program, "uFogNear")
        uFogFar = GLES30.glGetUniformLocation(program, "uFogFar")
        uSpec = GLES30.glGetUniformLocation(program, "uSpec")
        uCamPos = GLES30.glGetUniformLocation(program, "uCamPos")
        aPos = GLES30.glGetAttribLocation(program, "aPos")
        aNormal = GLES30.glGetAttribLocation(program, "aNormal")

        // Bola emas (statis): UV sphere 48x32, pusat (0, 1, 0), r=1.
        val sphere = SphereMesh.generate(48, 32, 1.0f, 1.0f)
        spherePos = toFloatBuffer(sphere.positions)
        sphereNrm = toFloatBuffer(sphere.normals)
        sphereIdx = toIntBuffer(sphere.indices)
        sphereIdxCount = sphere.indices.size

        // Lantai gelap 12x12 di y=0.
        floorBuf = toFloatBuffer(floatArrayOf(
            -6f, 0f, -6f, 6f, 0f, -6f, 6f, 0f, 6f,
            -6f, 0f, -6f, 6f, 0f, 6f, -6f, 0f, 6f
        ))
        // Grid garis tiap 0.5 dari -5..5.
        val lines = ArrayList<Float>()
        var g = -5f
        while (g <= 5.001f) {
            lines.add(g); lines.add(0.002f); lines.add(-5f)
            lines.add(g); lines.add(0.002f); lines.add(5f)
            lines.add(-5f); lines.add(0.002f); lines.add(g)
            lines.add(5f); lines.add(0.002f); lines.add(g)
            g += 0.5f
        }
        gridVertCount = lines.size / 3
        gridBuf = toFloatBuffer(lines.toFloatArray())

        // Gua bawah tanah (statis, dibangun sekali).
        caveWall = toStatic(CaveMesh.wall())
        caveCeiling = toStatic(CaveMesh.ceiling())
        caveFloor = toStatic(CaveMesh.floor())
        caveStones = toStatic(CaveMesh.stonePatches())
        caveCones = toStatic(CaveMesh.stalactites())

        lastTimeNs = System.nanoTime()
        fpsWindowStart = SystemClock.elapsedRealtime()
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projM, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(unused: GL10?) {
        val nowNs = System.nanoTime()
        var frameDt = (nowNs - lastTimeNs) / 1e9f
        lastTimeNs = nowNs
        if (frameDt > 0.25f) frameDt = 0.25f // jeda panjang (mis. kembali dari background)

        if (!paused) {
            acc += frameDt
            var steps = 0
            while (acc >= DT && steps < 2) {
                engine.step(DT, windStrength, simTime)
                simTime += DT
                acc -= DT
                steps++
            }
            if (steps == 2) acc = 0f // buang tunggakan (hindari spiral)
        }

        if (caveMode) {
            GLES30.glClearColor(0.07f, 0.05f, 0.04f, 1.0f)
        } else {
            GLES30.glClearColor(0.05f, 0.06f, 0.08f, 1.0f)
        }
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        // Kamera orbit: target (0, 1, 0). Di mode gua kamera dibatasi
        // supaya tetap di dalam gua (dinding r=12, langit-langit y=10).
        if (caveMode) {
            distance = distance.coerceIn(3f, 10f)
            elevation = elevation.coerceIn(0.08f, 1.05f)
        } else {
            distance = distance.coerceIn(3f, 14f)
            elevation = elevation.coerceIn(0.08f, 1.45f)
        }
        val ce = cos(elevation)
        eye[0] = distance * ce * sin(azimuth)
        eye[1] = 1.0f + distance * sin(elevation)
        eye[2] = distance * ce * cos(azimuth)
        Matrix.setLookAtM(viewM, 0,
            eye[0], eye[1], eye[2],
            0f, 1.0f, 0f,
            0f, 1.0f, 0f)
        Matrix.multiplyMM(mvpM, 0, projM, 0, viewM, 0)

        GLES30.glUseProgram(program)
        GLES30.glUniform3f(uLightDir, LIGHT_DIR[0], LIGHT_DIR[1], LIGHT_DIR[2])
        GLES30.glUniform3f(uCamPos, eye[0], eye[1], eye[2])
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpM, 0)
        if (caveMode) {
            // Gua: cahaya hangat obor + kabut tipis.
            GLES30.glUniform3f(uLightColor, 1.0f, 0.72f, 0.42f)
            GLES30.glUniform3f(uFogColor, 0.09f, 0.06f, 0.04f)
            GLES30.glUniform1f(uFogNear, 8f)
            GLES30.glUniform1f(uFogFar, 24f)
        } else {
            GLES30.glUniform3f(uLightColor, 1.0f, 1.0f, 1.0f)
            GLES30.glUniform3f(uFogColor, 0.05f, 0.06f, 0.08f)
            GLES30.glUniform1f(uFogNear, 1000f)
            GLES30.glUniform1f(uFogFar, 1001f)
        }

        if (caveMode) {
            // Lantai tanah + petak batu + dinding + langit-langit +
            // stalaktit/stalagmit.
            drawMesh(caveFloor, floatArrayOf(0.26f, 0.18f, 0.11f), 0f, 0.35f)
            drawMesh(caveStones, floatArrayOf(0.16f, 0.12f, 0.09f), 0f, 0.35f)
            drawMesh(caveWall, floatArrayOf(0.14f, 0.10f, 0.07f), 0f, 0.35f)
            drawMesh(caveCeiling, floatArrayOf(0.10f, 0.07f, 0.05f), 0f, 0.35f)
            drawMesh(caveCones, floatArrayOf(0.17f, 0.12f, 0.08f), 0f, 0.35f)
        } else {
            // Lantai gelap + grid (mode lama).
            drawArrays(GLES30.GL_TRIANGLES, floorBuf, upNormal(6), 6,
                floatArrayOf(0.09f, 0.10f, 0.12f), 0f, 0.35f)
            drawArrays(GLES30.GL_LINES, gridBuf, upNormal(gridVertCount), gridVertCount,
                floatArrayOf(0.22f, 0.24f, 0.28f), 0f, 0.35f)
        }
        // Bola. Ambient lebih tinggi (0.55) supaya emas tetap kuning di
        // sisi gelap — v1.0 tampil oranye karena ambient 0.35 menekan
        // channel hijau.
        drawElements(spherePos, sphereNrm, sphereIdx, sphereIdxCount, ballColor, 0.9f, 0.55f)
        // Kain.
        val pos = engine.positions()
        val nrm = engine.normals()
        val idx = engine.indices()
        if (pos != null && nrm != null && idx != null) {
            pos.position(0)
            nrm.position(0)
            idx.position(0)
            drawElements(pos, nrm, idx, engine.indexCount, clothColor, 0.05f, 0.35f)
        }

        // FPS (lapor 2x/detik).
        frames++
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - fpsWindowStart >= 500) {
            val fps = (frames * 1000 / (nowMs - fpsWindowStart).coerceAtLeast(1)).toInt()
            frames = 0
            fpsWindowStart = nowMs
            fpsListener?.invoke(fps)
        }
    }

    private fun upNormal(count: Int): FloatBuffer {
        // Normal (0,1,0) per vertex — dibuat baru tiap frame (kecil: lantai+grid).
        val arr = FloatArray(count * 3)
        for (i in 0 until count) arr[i * 3 + 1] = 1f
        return toFloatBuffer(arr)
    }

    private fun drawMesh(
        mesh: StaticDraw?, color: FloatArray, spec: Float, ambient: Float
    ) {
        if (mesh == null) return
        drawElements(mesh.pos, mesh.nrm, mesh.idx, mesh.count, color, spec, ambient)
    }

    private fun drawArrays(
        mode: Int, posBuf: FloatBuffer?, nrmBuf: FloatBuffer?,
        count: Int, color: FloatArray, spec: Float, ambient: Float
    ) {
        if (posBuf == null || nrmBuf == null) return
        posBuf.position(0)
        nrmBuf.position(0)
        GLES30.glUniform3f(uColor, color[0], color[1], color[2])
        GLES30.glUniform1f(uSpec, spec)
        GLES30.glUniform1f(uAmbient, ambient)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 3, GLES30.GL_FLOAT, false, 0, posBuf)
        GLES30.glEnableVertexAttribArray(aNormal)
        GLES30.glVertexAttribPointer(aNormal, 3, GLES30.GL_FLOAT, false, 0, nrmBuf)
        GLES30.glDrawArrays(mode, 0, count)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aNormal)
    }

    private fun drawElements(
        posBuf: FloatBuffer?, nrmBuf: FloatBuffer?, idxBuf: IntBuffer?,
        count: Int, color: FloatArray, spec: Float, ambient: Float
    ) {
        if (posBuf == null || nrmBuf == null || idxBuf == null) return
        posBuf.position(0)
        nrmBuf.position(0)
        idxBuf.position(0)
        GLES30.glUniform3f(uColor, color[0], color[1], color[2])
        GLES30.glUniform1f(uSpec, spec)
        GLES30.glUniform1f(uAmbient, ambient)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 3, GLES30.GL_FLOAT, false, 0, posBuf)
        GLES30.glEnableVertexAttribArray(aNormal)
        GLES30.glVertexAttribPointer(aNormal, 3, GLES30.GL_FLOAT, false, 0, nrmBuf)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_INT, idxBuf)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aNormal)
    }

    private fun toFloatBuffer(data: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(data)
        fb.position(0)
        return fb
    }

    private fun toIntBuffer(data: IntArray): IntBuffer {
        val bb = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
        val ib = bb.asIntBuffer()
        ib.put(data)
        ib.position(0)
        return ib
    }
}

package com.hozinking.clothsim

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var engine: ClothEngine
    private lateinit var renderer: ClothRenderer
    private lateinit var fpsView: TextView
    private lateinit var faceLabel: TextView
    private lateinit var windLabel: TextView
    private lateinit var playButton: Button

    private var faceLevel: Int = 10 // default 1024 face
    private var lastPinchDist: Float = 0f
    private val pointers = HashMap<Int, Pair<Float, Float>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = ClothEngine()
        engine.create(ClothSpec.segmentsForFaces(ClothSpec.FACE_LEVELS[faceLevel]))
        renderer = ClothRenderer(engine)
        renderer.fpsListener = { fps ->
            runOnUiThread { fpsView.text = "$fps FPS" }
        }

        glView = findViewById(R.id.glView)
        glView.setEGLContextClientVersion(3)
        // Depth 24-bit eksplisit: config default bisa 16-bit -> kain yang
        // menempel di bola z-fighting (garis coklat radial di Face 4096).
        glView.setEGLConfigChooser(8, 8, 8, 0, 24, 0)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        glView.setOnTouchListener { _, ev -> onGlTouch(ev) }

        fpsView = findViewById(R.id.fpsView)
        faceLabel = findViewById(R.id.faceLabel)
        windLabel = findViewById(R.id.windLabel)
        playButton = findViewById(R.id.btnPlay)

        playButton.setOnClickListener {
            renderer.paused = !renderer.paused
            playButton.text = if (renderer.paused) "▶ Main" else "⏸ Jeda"
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            engine.reset()
        }
        val bgButton = findViewById<Button>(R.id.btnBg)
        bgButton.setOnClickListener {
            renderer.caveMode = !renderer.caveMode
            bgButton.text = if (renderer.caveMode) "⛏ Gua" else "🌑 Gelap"
        }

        val faceBar = findViewById<SeekBar>(R.id.faceBar)
        faceBar.max = ClothSpec.FACE_LEVELS.size - 1
        faceBar.progress = faceLevel
        updateFaceLabel()
        faceBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                faceLevel = p
                updateFaceLabel()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                applyFaceLevel()
            }
        })

        val windBar = findViewById<SeekBar>(R.id.windBar)
        windBar.max = 100
        windBar.progress = 0
        windBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                renderer.windStrength = p / 100f
                windLabel.text = "Angin: $p%"
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        buildColorRow(R.id.clothColorRow, ClothSpec.CLOTH_COLORS) { preset ->
            renderer.clothColor = preset.rgb
        }
        buildColorRow(R.id.ballColorRow, ClothSpec.BALL_COLORS) { preset ->
            renderer.ballColor = preset.rgb
        }
    }

    private fun updateFaceLabel() {
        val faces = ClothSpec.FACE_LEVELS[faceLevel]
        faceLabel.text = "Face: $faces${ClothSpec.faceLabelSuffix(faces)}"
    }

    private fun applyFaceLevel() {
        val faces = ClothSpec.FACE_LEVELS[faceLevel]
        glView.queueEvent {
            engine.create(ClothSpec.segmentsForFaces(faces))
        }
    }

    private fun buildColorRow(
        rowId: Int,
        presets: List<ClothSpec.ColorPreset>,
        onPick: (ClothSpec.ColorPreset) -> Unit
    ) {
        val row = findViewById<android.widget.LinearLayout>(rowId)
        for (preset in presets) {
            val b = Button(this)
            b.text = preset.name
            b.textSize = 12f
            // Bagi lebar rata (weight 1): v1.0 tombol terakhir (HITAM)
            // terjepit di tepi kanan sampai teksnya vertikal.
            val lp = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            lp.setMargins(2, 0, 2, 0)
            b.layoutParams = lp
            b.minWidth = 0
            b.setPadding(4, 0, 4, 0)
            b.setOnClickListener { onPick(preset) }
            row.addView(b)
        }
    }

    /** Drag 1 jari = orbit kamera; pinch 2 jari = zoom. */
    private fun onGlTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                for (i in 0 until ev.pointerCount) {
                    pointers[ev.getPointerId(i)] = ev.getX(i) to ev.getY(i)
                }
                if (pointers.size == 2) {
                    val pts = pointers.values.toList()
                    lastPinchDist = hypot(pts[0].first - pts[1].first, pts[0].second - pts[1].second)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointers.size == 1) {
                    val id = ev.getPointerId(0)
                    val prev = pointers[id]
                    if (prev != null) {
                        val dx = ev.getX(0) - prev.first
                        val dy = ev.getY(0) - prev.second
                        renderer.azimuth -= dx * 0.006f
                        renderer.elevation = (renderer.elevation + dy * 0.006f)
                            .coerceIn(0.08f, 1.45f)
                    }
                    pointers[id] = ev.getX(0) to ev.getY(0)
                } else if (pointers.size == 2) {
                    val pts = pointers.values.toList()
                    val cur = hypot(pts[0].first - pts[1].first, pts[0].second - pts[1].second)
                    if (lastPinchDist > 0f && cur > 0f) {
                        renderer.distance = (renderer.distance * lastPinchDist / cur)
                            .coerceIn(3f, 14f)
                    }
                    lastPinchDist = cur
                    for (i in 0 until ev.pointerCount) {
                        pointers[ev.getPointerId(i)] = ev.getX(i) to ev.getY(i)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                pointers.remove(ev.getPointerId(ev.actionIndex))
                lastPinchDist = 0f
            }
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }
}

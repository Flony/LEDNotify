package com.flony.lednotify

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class BurnoutProtectionActivity : AppCompatActivity() {

    private var selectedMethod = 2 // Default: Subpixel-Dithering
    private lateinit var cardNone: LinearLayout
    private lateinit var cardPixelShift: LinearLayout
    private lateinit var cardDithering: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        selectedMethod = prefs.getInt("pref_burnout_method", 2)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.burnout_title)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }

        // 1. Bez ochrany
        cardNone = createCard(getString(R.string.burnout_none_title), getString(R.string.burnout_none_desc)) {
            selectedMethod = 0
            saveAndHighlight()
        }

        // 2. Pixel-Shift
        val pixelShiftAnim = PixelShiftAnimationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(160, 160).apply { setMargins(0, 12, 0, 12) }
        }
        cardPixelShift = createCard(getString(R.string.burnout_ps_title), getString(R.string.burnout_ps_desc), pixelShiftAnim) {
            selectedMethod = 1
            saveAndHighlight()
        }

        // 3. Subpixel-Dithering (Šachovnice)
        val ditheringAnim = DitheringAnimationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(160, 160).apply { setMargins(0, 12, 0, 12) }
        }
        val ditherDesc = "${getString(R.string.burnout_dither_desc)}\n\n${getString(R.string.burnout_dither_note)}"
        cardDithering = createCard(getString(R.string.burnout_dither_title), ditherDesc, ditheringAnim) {
            selectedMethod = 2
            saveAndHighlight()
        }

        layout.addView(titleView)
        layout.addView(cardNone)
        layout.addView(cardPixelShift)
        layout.addView(cardDithering)
        scrollView.addView(layout)

        setContentView(scrollView)

        updateCardHighlights()

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left + 48, systemBars.top + 24, systemBars.right + 48, systemBars.bottom + 24)
            insets
        }
    }

    private fun createCard(title: String, desc: String, customView: View? = null, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            setBackgroundColor(Color.parseColor("#1A000000"))

            val titleTv = TextView(this@BurnoutProtectionActivity).apply {
                text = title
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            }
            addView(titleTv)

            if (customView != null) {
                addView(customView)
            }

            val descTv = TextView(this@BurnoutProtectionActivity).apply {
                text = desc
                textSize = 14f
                setPadding(0, 8, 0, 0)
            }
            addView(descTv)

            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
        }
    }

    private fun saveAndHighlight() {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        prefs.edit().putInt("pref_burnout_method", selectedMethod).apply()
        updateCardHighlights()
    }

    private fun updateCardHighlights() {
        cardNone.alpha = if (selectedMethod == 0) 1.0f else 0.5f
        cardPixelShift.alpha = if (selectedMethod == 1) 1.0f else 0.5f
        cardDithering.alpha = if (selectedMethod == 2) 1.0f else 0.5f
    }
}

// Pixel-Shift Animation with Fast Fade Heatmap/Wear Trail
class PixelShiftAnimationView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridRows = 6
    private val gridCols = 6
    private val wearGrid = Array(gridRows) { FloatArray(gridCols) }
    private var step = 0
    private val handler = Handler(Looper.getMainLooper())

    private val runnable = object : Runnable {
        override fun run() {
            step = (step + 1) % 4

            for (r in 0 until gridRows) {
                for (c in 0 until gridCols) {
                    wearGrid[r][c] = (wearGrid[r][c] - 0.35f).coerceAtLeast(0f)
                }
            }

            val (sr, sc) = getShapeCoords(step)
            for (r in sr until sr + 3) {
                for (c in sc until sc + 3) {
                    if (r in 0 until gridRows && c in 0 until gridCols) {
                        wearGrid[r][c] = (wearGrid[r][c] + 0.7f).coerceAtMost(1.0f)
                    }
                }
            }
            invalidate()
            handler.postDelayed(this, 700L)
        }
    }

    init {
        handler.post(runnable)
    }

    private fun getShapeCoords(s: Int): Pair<Int, Int> {
        return when (s) {
            0 -> Pair(1, 1)
            1 -> Pair(1, 2)
            2 -> Pair(2, 2)
            else -> Pair(2, 1)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellSize = width / (gridCols + 2f)
        val startX = cellSize
        val startY = cellSize

        val (currSr, currSc) = getShapeCoords(step)

        for (row in 0 until gridRows) {
            for (col in 0 until gridCols) {
                val x = startX + col * cellSize
                val y = startY + row * cellSize

                val wear = wearGrid[row][col]
                val red = (255 * wear).toInt()
                val green = (60 * (1f - wear)).toInt()
                paint.color = Color.rgb(red, green, 10)

                canvas.drawRect(x, y, x + cellSize - 2f, y + cellSize - 2f, paint)

                val isInsideShape = row in currSr until currSr + 3 && col in currSc until currSc + 3
                if (isInsideShape) {
                    paint.color = Color.GREEN
                    canvas.drawRect(x + 2f, y + 2f, x + cellSize - 4f, y + cellSize - 4f, paint)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(runnable)
    }
}

// 5x5 Grid Animation for Subpixel-Dithering
class DitheringAnimationView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var inverted = false
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            inverted = !inverted
            invalidate()
            handler.postDelayed(this, 700L)
        }
    }

    init {
        handler.post(runnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellSize = width / 7f
        val startX = cellSize
        val startY = cellSize

        for (row in 0 until 5) {
            for (col in 0 until 5) {
                val isEven = (row + col) % 2 == 0
                val active = if (inverted) !isEven else isEven
                paint.color = if (active) Color.GREEN else Color.DKGRAY
                val x = startX + col * cellSize
                val y = startY + row * cellSize
                canvas.drawRect(x, y, x + cellSize - 2f, y + cellSize - 2f, paint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(runnable)
    }
}

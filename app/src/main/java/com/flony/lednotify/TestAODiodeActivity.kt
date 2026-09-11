package com.flony.lednotify

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class TestAODiodeActivity : AppCompatActivity() {

    private lateinit var ledView: LedView
    private val handler = Handler(Looper.getMainLooper())

    private var offsetX = 0f
    private var offsetY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var pillWidthRatio = 2.5f
    private var pillHeightRatio = 1.5f
    private var lastSpanX = 0f
    private var lastSpanY = 0f

    private val shiftRunnable = object : Runnable {
        override fun run() {
            ledView.shiftPixels()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Povolení vykreslování do oblasti výřezu/průstřelu displeje (Display Cutout) pro Xiaomi a moderní OLED displeje
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Skrytí systémových lišt pro plný fullscreen náhled
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val brightness = intent.getIntExtra("AOD_BRIGHTNESS", 100)
        setupBrightness(brightness)

        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val isEco1Hz = intent.getBooleanExtra("AOD_ECO_1HZ", prefs.getBoolean("pref_eco_1hz", false))

        applyEco1HzRefreshRate(isEco1Hz)

        offsetX = prefs.getFloat("pref_offset_x", 0f)
        offsetY = prefs.getFloat("pref_offset_y", 0f)
        pillWidthRatio = prefs.getFloat("pref_pill_width_ratio", 2.5f)
        pillHeightRatio = prefs.getFloat("pref_pill_height_ratio", 1.5f)

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        ledView = LedView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val defaultShape = if (prefs.getBoolean("pref_is_dot", false)) 0 else 1
        val shapeType = intent.getIntExtra("AOD_SHAPE_TYPE", prefs.getInt("pref_shape_type", defaultShape))
        val color = intent.getIntExtra("AOD_COLOR", Color.RED)
        val radius = intent.getFloatExtra("AOD_RADIUS", prefs.getFloat("pref_radius", 38f))
        val onDuration = intent.getLongExtra("AOD_ON_DURATION", 300L)
        val offDuration = intent.getLongExtra("AOD_OFF_DURATION", 3500L)
        val fadeDuration = if (isEco1Hz) 0L else intent.getLongExtra("AOD_FADE_DURATION", 2000L)
        val burnout = prefs.getInt("pref_burnout_method", 2)

        val asciiText = intent.getStringExtra("AOD_ASCII_TEXT") ?: prefs.getString("pref_ascii_text", "♥") ?: "♥"

        var currentRadius = radius
        var lastSpan = 0f

        ledView.setPillRatios(pillWidthRatio, pillHeightRatio)
        ledView.setAsciiText(asciiText)
        ledView.setConfig(color, shapeType, currentRadius, offsetX, offsetY, brightness, onDuration, offDuration, fadeDuration, burnout)

        var isMultiTouchActive = false

        // Touch listener pro pohyb 1 prstem a měnění velikosti / tvaru 2 prsty (multi-touch stretch/pinch)
        ledView.setOnTouchListener { _, event ->
            val action = event.actionMasked
            val pointerCount = event.pointerCount

            if (pointerCount >= 2) {
                isMultiTouchActive = true
                val x0 = event.getX(0)
                val y0 = event.getY(0)
                val x1 = event.getX(1)
                val y1 = event.getY(1)

                val currentSpanX = Math.abs(x0 - x1)
                val currentSpanY = Math.abs(y0 - y1)
                val currentSpan = Math.hypot((x0 - x1).toDouble(), (y0 - y1).toDouble()).toFloat()

                when (action) {
                    MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                        lastSpan = currentSpan
                        lastSpanX = currentSpanX
                        lastSpanY = currentSpanY
                        ledView.setDragging(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (lastSpan > 0f) {
                            val deltaSpan = (currentSpan - lastSpan) / (resources.displayMetrics.density * 8f)
                            currentRadius = (currentRadius + deltaSpan).coerceIn(2f, 75f)
                            saveRadius(prefs, currentRadius)

                            if (shapeType == 2 || shapeType == 3) {
                                if (lastSpanX > 0f && lastSpanY > 0f) {
                                    val deltaX = (currentSpanX - lastSpanX) / (resources.displayMetrics.density * 100f)
                                    val deltaY = (currentSpanY - lastSpanY) / (resources.displayMetrics.density * 100f)

                                    pillWidthRatio = (pillWidthRatio + deltaX).coerceIn(1.0f, 6.0f)
                                    pillHeightRatio = (pillHeightRatio + deltaY).coerceIn(0.5f, 4.0f)
                                    savePillRatios(prefs)
                                }
                            }

                            ledView.setPillRatios(pillWidthRatio, pillHeightRatio)
                            ledView.setConfig(color, shapeType, currentRadius, offsetX, offsetY, brightness, onDuration, offDuration, fadeDuration, burnout)
                        }
                        lastSpan = currentSpan
                        lastSpanX = currentSpanX
                        lastSpanY = currentSpanY
                    }
                }
                true
            } else {
                if (action == MotionEvent.ACTION_POINTER_UP) {
                    lastSpan = 0f
                    lastSpanX = 0f
                    lastSpanY = 0f
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return@setOnTouchListener true
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    lastSpan = 0f
                    lastSpanX = 0f
                    lastSpanY = 0f
                    isMultiTouchActive = false
                    ledView.setDragging(false)
                    ledView.performClick()
                    return@setOnTouchListener true
                }

                if (isMultiTouchActive) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return@setOnTouchListener true
                }

                // Pohyb AODiode po obrazovce 1 prstem
                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        ledView.setDragging(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        offsetX += dx
                        offsetY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y

                        ledView.setOffset(offsetX, offsetY)
                        saveOffset(prefs)
                        true
                    }
                    else -> false
                }
            }
        }

        rootLayout.addView(ledView)

        // Control overlay at the bottom with transparent borderless buttons (only arrows and text visible)
        val controlLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                setMargins(0, 0, 0, 120)
            }
        }

        val btnUp = Button(this).apply {
            text = "▲"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 28f
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                offsetY -= 1f
                ledView.setOffset(offsetX, offsetY)
                saveOffset(prefs)
            }
        }

        val horizontalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val btnLeft = Button(this).apply {
            text = "◄"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 28f
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                offsetX -= 1f
                ledView.setOffset(offsetX, offsetY)
                saveOffset(prefs)
            }
        }
        val btnCloseTest = Button(this).apply {
            text = getString(R.string.btn_close_test)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 16f
            setOnClickListener {
                finish()
            }
        }
        val btnRight = Button(this).apply {
            text = "►"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 28f
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                offsetX += 1f
                ledView.setOffset(offsetX, offsetY)
                saveOffset(prefs)
            }
        }
        horizontalRow.addView(btnLeft)
        horizontalRow.addView(btnCloseTest)
        horizontalRow.addView(btnRight)

        val btnDown = Button(this).apply {
            text = "▼"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 28f
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                offsetY += 1f
                ledView.setOffset(offsetX, offsetY)
                saveOffset(prefs)
            }
        }

        val swipeClickText = TextView(this).apply {
            text = getString(R.string.swipe_click_hint)
            textSize = 13f
            setTextColor(Color.parseColor("#80FFFFFF")) // slabý bílý text
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val pinchSizeText = TextView(this).apply {
            text = getString(R.string.pinch_size_hint)
            textSize = 13f
            setTextColor(Color.parseColor("#80FFFFFF")) // slabý bílý text
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 0)
        }

        controlLayout.addView(btnUp)
        controlLayout.addView(horizontalRow)
        controlLayout.addView(btnDown)
        controlLayout.addView(swipeClickText)
        controlLayout.addView(pinchSizeText)

        rootLayout.addView(controlLayout)

        setContentView(rootLayout)
    }

    private fun setupBrightness(brightnessPercent: Int) {
        val lp = window.attributes
        lp.screenBrightness = (brightnessPercent / 100f).coerceIn(0.01f, 1.0f)
        window.attributes = lp
    }

    private fun applyEco1HzRefreshRate(isEco1Hz: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val lp = window.attributes
            try {
                lp.preferredDisplayModeId = 0
                val targetRate = if (isEco1Hz) 1.0f else 0.0f

                val minSetter = WindowManager.LayoutParams::class.java.getMethod("setPreferredMinDisplayRefreshRate", Float::class.javaPrimitiveType)
                minSetter.invoke(lp, targetRate)

                val maxSetter = WindowManager.LayoutParams::class.java.getMethod("setPreferredMaxDisplayRefreshRate", Float::class.javaPrimitiveType)
                maxSetter.invoke(lp, if (isEco1Hz) 1.0f else 0.0f)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val frameRateSetter = WindowManager.LayoutParams::class.java.getMethod("setPreferredFrameRate", Float::class.javaPrimitiveType)
                    frameRateSetter.invoke(lp, targetRate)
                }
                window.attributes = lp
            } catch (_: Exception) {}
        }
    }

    private fun saveOffset(prefs: SharedPreferences) {
        prefs.edit().apply {
            putFloat("pref_offset_x", offsetX)
            putFloat("pref_offset_y", offsetY)
            apply()
        }
    }

    private fun savePillRatios(prefs: SharedPreferences) {
        prefs.edit().apply {
            putFloat("pref_pill_width_ratio", pillWidthRatio)
            putFloat("pref_pill_height_ratio", pillHeightRatio)
            apply()
        }
    }

    private fun saveRadius(prefs: SharedPreferences, radius: Float) {
        prefs.edit().putFloat("pref_radius", radius).apply()
    }

    override fun onResume() {
        super.onResume()
        handler.post(shiftRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(shiftRunnable)
    }
}

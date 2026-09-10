package com.flony.lednotify

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class LedActivity : AppCompatActivity() {

    private lateinit var ledView: LedView
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    // Pixel-shift interval: 60 sekund
    private val shiftRunnable = object : Runnable {
        override fun run() {
            ledView.shiftPixels()
            handler.postDelayed(this, 60_000)
        }
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    // Receiver pro zachycení odemčení zařízení (otisk, PIN, gesto, tvář)
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                finish()
            }
        }
    }

    // Receiver pro zachycení stisknutí tlačítka napájení / zhasnutí obrazovky
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        super.onCreate(savedInstanceState)

        // Povolení vykreslování do oblasti výřezu/průstřelu displeje (Display Cutout) pro Xiaomi a moderní OLED displeje
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Úplné skrytí systémových lišt i spodní gestové lišty (žádná bílá čára)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setupLockscreenFlags()

        val brightness = intent.getIntExtra("AOD_BRIGHTNESS", 100)
        setupBrightness(brightness)

        ledView = LedView(this)

        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val isEco1Hz = intent.getBooleanExtra("AOD_ECO_1HZ", prefs.getBoolean("pref_eco_1hz", false))

        applyEco1HzRefreshRate(isEco1Hz)

        val defaultShape = if (prefs.getBoolean("pref_is_dot", false)) 0 else 1
        val shapeType = intent.getIntExtra("AOD_SHAPE_TYPE", prefs.getInt("pref_shape_type", defaultShape))
        val pillWidthRatio = prefs.getFloat("pref_pill_width_ratio", 2.5f)
        val pillHeightRatio = prefs.getFloat("pref_pill_height_ratio", 1.5f)
        val color = intent.getIntExtra("AOD_COLOR", Color.RED)
        val radius = intent.getFloatExtra("AOD_RADIUS", 38f)
        val offsetX = intent.getFloatExtra("AOD_OFFSET_X", 0f)
        val offsetY = intent.getFloatExtra("AOD_OFFSET_Y", 0f)
        val onDuration = intent.getLongExtra("AOD_ON_DURATION", 300L)
        val offDuration = intent.getLongExtra("AOD_OFF_DURATION", 3500L)
        val fadeDuration = if (isEco1Hz) 0L else intent.getLongExtra("AOD_FADE_DURATION", 2000L)
        val burnout = intent.getIntExtra("AOD_BURNOUT", prefs.getInt("pref_burnout_method", 2))

        ledView.setPillRatios(pillWidthRatio, pillHeightRatio)
        ledView.setConfig(color, shapeType, radius, offsetX, offsetY, brightness, onDuration, offDuration, fadeDuration, burnout)

        // Poklepáním nebo klikem kdekoli se AODiode zhasne a aktivita ukončí
        ledView.setOnClickListener {
            finish()
        }

        setContentView(ledView)

        val filter = IntentFilter("com.flony.lednotify.ACTION_CLEAR_LED")
        val userPresentFilter = IntentFilter(Intent.ACTION_USER_PRESENT)
        val screenOffFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)

        ContextCompat.registerReceiver(this, dismissReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, userPresentReceiver, userPresentFilter, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, screenOffReceiver, screenOffFilter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    override fun onResume() {
        super.onResume()
        // Pokud je zařízení již odemčené, okamžitě ukončit aktivitu
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) {
            finish()
            return
        }

        handler.post(shiftRunnable)

        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val timeoutMin = prefs.getInt("pref_aodiode_timeout_min", 60)
        if (timeoutMin > 0) {
            timeoutRunnable?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                finish()
            }
            timeoutRunnable = runnable
            handler.postDelayed(runnable, timeoutMin * 60 * 1000L)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(shiftRunnable)
        timeoutRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dismissReceiver)
            unregisterReceiver(userPresentReceiver)
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
    }
}

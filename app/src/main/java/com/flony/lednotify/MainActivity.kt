package com.flony.lednotify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ShapeIconView(context: Context, val shapeType: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        when (shapeType) {
            0 -> { // DOT (Solid Circle)
                paint.style = Paint.Style.FILL
                val radius = (Math.min(w, h) / 2f) - 16f
                canvas.drawCircle(cx, cy, radius, paint)
            }
            1 -> { // RING (Stroke Circle)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f
                val radius = (Math.min(w, h) / 2f) - 16f
                canvas.drawCircle(cx, cy, radius, paint)
            }
            2 -> { // SOLID PILL (Solid Pill - narrowed by 50% for optimal ratio)
                paint.style = Paint.Style.FILL
                val pillWidth = (w - 16f) * 0.50f
                val pillHeight = (h / 2f) - 4f
                val left = cx - (pillWidth / 2f)
                val top = cy - (pillHeight / 2f)
                val right = cx + (pillWidth / 2f)
                val bottom = cy + (pillHeight / 2f)
                val rx = pillHeight / 2f
                val ry = pillHeight / 2f
                canvas.drawRoundRect(left, top, right, bottom, rx, ry, paint)
            }
            3 -> { // EMPTY PILL (Stroke/Hollow Pill - narrowed)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f
                val pillWidth = (w - 16f) * 0.50f
                val pillHeight = (h / 2f) - 4f
                val left = cx - (pillWidth / 2f)
                val top = cy - (pillHeight / 2f)
                val right = cx + (pillWidth / 2f)
                val bottom = cy + (pillHeight / 2f)
                val rx = pillHeight / 2f
                val ry = pillHeight / 2f
                canvas.drawRoundRect(left, top, right, bottom, rx, ry, paint)
            }
            4 -> { // ASCII (Text Icon)
                paint.style = Paint.Style.FILL
                paint.textSize = 10f * resources.displayMetrics.density
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                paint.textAlign = Paint.Align.CENTER
                val yPos = cy - ((paint.descent() + paint.ascent()) / 2f)
                canvas.drawText("ASCII", cx, yPos, paint)
            }
        }
    }
}

class MainActivity : AppCompatActivity() {

    private var previewDrawable: AODiodePreviewDrawable? = null
    private lateinit var previewImageView: ImageView

    private var currentColor = Color.RED
    private var currentShapeType = 1 // 0 = DOT, 1 = RING, 2 = PILL, 3 = EMPTY_PILL
    private var currentRadius = 38f
    private var currentBrightness = 100
    private var currentOnDuration = 300L
    private var currentOffDuration = 3500L
    private var currentFadeDuration = 2000L
    private var isEco1Hz = false

    private lateinit var brightnessValueText: TextView
    private lateinit var onDurationValueText: TextView
    private lateinit var offDurationValueText: TextView
    private lateinit var fadeDurationValueText: TextView
    private lateinit var labelFade: TextView
    private lateinit var switchEco1Hz: SwitchCompat

    private lateinit var iconDot: ShapeIconView
    private lateinit var iconRing: ShapeIconView
    private lateinit var iconPill: ShapeIconView
    private lateinit var iconEmptyPill: ShapeIconView
    private lateinit var iconAscii: ShapeIconView

    private lateinit var seekBarBrightness: SeekBar
    private lateinit var seekBarOnDur: SeekBar
    private lateinit var seekBarOffDur: SeekBar
    private lateinit var seekBarFade: SeekBar

    private val colorOptions = listOf(
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
        Color.CYAN, Color.WHITE, Color.MAGENTA, Color.rgb(255, 128, 0)
    )
    private val colorViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        currentColor = prefs.getInt("pref_color", Color.RED)
        val defaultShape = if (prefs.getBoolean("pref_is_dot", false)) 0 else 1
        currentShapeType = prefs.getInt("pref_shape_type", defaultShape)
        currentRadius = prefs.getFloat("pref_radius", 38f)
        currentBrightness = prefs.getInt("pref_brightness", 100)
        currentOnDuration = prefs.getLong("pref_on_duration", 300L)
        currentOffDuration = prefs.getLong("pref_off_duration", 3500L)
        currentFadeDuration = prefs.getLong("pref_fade_duration", 2000L)
        isEco1Hz = prefs.getBoolean("pref_eco_1hz", false)

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

        // System AOD Warning Card (displayed ONCE per ON cycle in MainActivity)
        val isSystemAodOn = isSystemAodEnabled()
        val hasBeenWarned = prefs.getBoolean("pref_system_aod_warned_on_cycle", false)

        if (!isSystemAodOn) {
            if (hasBeenWarned) {
                prefs.edit().putBoolean("pref_system_aod_warned_on_cycle", false).apply()
            }
        } else if (!hasBeenWarned) {
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val cardBg = if (isNight) Color.parseColor("#33FF9800") else Color.parseColor("#1AFF9800")

            val systemAodCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                setBackgroundColor(cardBg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 32) }
            }

            val tvWarningTitle = TextView(this).apply {
                text = getString(R.string.warning_system_aod_title)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#FFA500"))
                setPadding(0, 0, 0, 8)
            }

            val tvWarningDesc = TextView(this).apply {
                text = getString(R.string.warning_system_aod_desc)
                textSize = 12f
                setPadding(0, 0, 0, 12)
            }

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val btnOpenDisplaySettings = Button(this).apply {
                text = getString(R.string.btn_open_display_settings)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 8, 0)
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    try {
                        startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                    } catch (_: Exception) {}
                }
            }

            val btnDismiss = Button(this).apply {
                text = "OK"
                textSize = 13f
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    prefs.edit().putBoolean("pref_system_aod_warned_on_cycle", true).apply()
                    systemAodCard.visibility = View.GONE
                }
            }

            btnRow.addView(btnOpenDisplaySettings)
            btnRow.addView(btnDismiss)

            systemAodCard.addView(tvWarningTitle)
            systemAodCard.addView(tvWarningDesc)
            systemAodCard.addView(btnRow)

            layout.addView(systemAodCard)
            prefs.edit().putBoolean("pref_system_aod_warned_on_cycle", true).apply()
        }

        // Header layout containing Title and large 1:1 Live Preview ImageView
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 32) }
        }

        val titleCustomize = TextView(this).apply {
            text = getString(R.string.title_customize)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val previewSizePx = (72 * resources.displayMetrics.density).toInt()
        val pillWidthRatio = prefs.getFloat("pref_pill_width_ratio", 2.5f)
        val pillHeightRatio = prefs.getFloat("pref_pill_height_ratio", 1.5f)
        previewDrawable = AODiodePreviewDrawable(currentColor, currentShapeType, currentRadius, pillWidthRatio, pillHeightRatio)

        previewImageView = ImageView(this).apply {
            setImageDrawable(previewDrawable)
            layoutParams = LinearLayout.LayoutParams(previewSizePx, previewSizePx)
            setBackgroundColor(Color.BLACK)
            setPadding(12, 12, 12, 12)
        }

        headerLayout.addView(titleCustomize)
        headerLayout.addView(previewImageView)

        // 1. Color Selection
        val labelColor = TextView(this).apply {
            text = getString(R.string.label_color)
            textSize = 16f
            setPadding(0, 0, 0, 12)
        }
        val colorScroll = HorizontalScrollView(this).apply {
            isFillViewport = false
            setPadding(0, 0, 0, 16)
        }
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val squareSize = (31 * resources.displayMetrics.density).toInt()
        val squareMargin = 1

        colorOptions.forEach { colorInt ->
            val colorSquare = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(squareSize, squareSize).apply {
                    setMargins(squareMargin, squareMargin, squareMargin, squareMargin)
                }
                background = createColorSquareDrawable(colorInt, colorInt == currentColor)
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    currentColor = colorInt
                    updateColorSelectionUI()
                    savePreferences()
                }
            }
            colorViews.add(colorSquare)
            colorRow.addView(colorSquare)
        }

        // Tlačítko se třemi tečkami (•••) pro otevření spektra barev
        val customColorButton = TextView(this).apply {
            text = "•••"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(squareSize, squareSize).apply {
                setMargins(squareMargin, squareMargin, squareMargin, squareMargin)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#333333"))
                setStroke(1, Color.GRAY)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                showAdvancedColorPickerDialog { newColor ->
                    currentColor = newColor
                    updateColorSelectionUI()
                    savePreferences()
                }
            }
        }
        colorRow.addView(customColorButton)
        colorScroll.addView(colorRow)

        // Tlačítko pro Správu barev aplikací těsně pod výběrem barev
        val btnAppColors = Button(this).apply {
            text = getString(R.string.btn_app_colors)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                startActivity(Intent(this@MainActivity, AppColorMappingActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        // 2. Shape Selection (DOT, RING, PILL)
        val labelShape = TextView(this).apply {
            text = getString(R.string.label_shape)
            textSize = 16f
            setPadding(0, 16, 0, 12)
        }
        val shapeToggleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (52 * resources.displayMetrics.density).toInt()
            ).apply { setMargins(0, 0, 0, 24) }
        }

        iconDot = ShapeIconView(this, 0).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentShapeType = 0
                updateShapeToggleUI()
                savePreferences()
            }
        }

        iconRing = ShapeIconView(this, 1).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentShapeType = 1
                updateShapeToggleUI()
                savePreferences()
            }
        }

        iconPill = ShapeIconView(this, 2).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentShapeType = 2
                updateShapeToggleUI()
                savePreferences()
            }
        }

        iconEmptyPill = ShapeIconView(this, 3).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentShapeType = 3
                updateShapeToggleUI()
                savePreferences()
            }
        }

        iconAscii = ShapeIconView(this, 4).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentShapeType = 4
                updateShapeToggleUI()
                savePreferences()
                showAsciiCustomInputDialog()
            }
        }

        shapeToggleLayout.addView(iconDot)
        shapeToggleLayout.addView(iconRing)
        shapeToggleLayout.addView(iconPill)
        shapeToggleLayout.addView(iconEmptyPill)
        shapeToggleLayout.addView(iconAscii)
        updateShapeToggleUI()

        // 3. Brightness Slider (0% to 100%)
        val labelBrightness = TextView(this).apply {
            text = getString(R.string.label_brightness)
            textSize = 16f
            setPadding(0, 8, 0, 4)
        }
        brightnessValueText = TextView(this).apply {
            text = getString(R.string.brightness_format, currentBrightness)
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        seekBarBrightness = SeekBar(this).apply {
            max = 100
            progress = currentBrightness.coerceIn(0, 100)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentBrightness = progress
                    brightnessValueText.text = getString(R.string.brightness_format, currentBrightness)
                    if (fromUser) {
                        seekBar?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        savePreferences()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 5. On Duration Slider (50ms to 5000ms)
        val labelOnDur = TextView(this).apply {
            text = getString(R.string.label_on_dur)
            textSize = 16f
            setPadding(0, 8, 0, 4)
        }
        onDurationValueText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        seekBarOnDur = SeekBar(this).apply {
            max = 4950
            progress = (currentOnDuration - 50L).toInt().coerceIn(0, 4950)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val rawOn = (progress + 50).toLong()
                    currentOnDuration = if (isEco1Hz) {
                        (((rawOn + 500L) / 1000L) * 1000L).coerceIn(1000L, 5000L)
                    } else {
                        rawOn
                    }
                    updateDurationsDisplay()
                    if (fromUser) {
                        seekBar?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        savePreferences()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 6. Off Duration / Interval Slider (1000ms to 30000ms / 1s to 30s)
        val labelOffDur = TextView(this).apply {
            text = getString(R.string.label_off_dur)
            textSize = 16f
            setPadding(0, 8, 0, 4)
        }
        offDurationValueText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        seekBarOffDur = SeekBar(this).apply {
            max = 29000
            progress = (currentOffDuration - 1000L).toInt().coerceIn(0, 29000)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val rawOff = (progress + 1000).toLong()
                    currentOffDuration = if (isEco1Hz) {
                        (((rawOff + 500L) / 1000L) * 1000L).coerceIn(1000L, 30000L)
                    } else {
                        rawOff
                    }
                    updateDurationsDisplay()
                    if (fromUser) {
                        seekBar?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        savePreferences()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 7. Fade / Transition Duration Slider (0ms to 2500ms)
        labelFade = TextView(this).apply {
            text = getString(R.string.label_fade)
            textSize = 16f
            setPadding(0, 8, 0, 4)
        }
        fadeDurationValueText = TextView(this).apply {
            text = getString(R.string.fade_dur_format, currentFadeDuration)
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        seekBarFade = SeekBar(this).apply {
            max = 2500
            progress = currentFadeDuration.toInt().coerceIn(0, 2500)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentFadeDuration = progress.toLong()
                    fadeDurationValueText.text = getString(R.string.fade_dur_format, currentFadeDuration)
                    if (fromUser) {
                        seekBar?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        savePreferences()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 8. 1Hz ECO Mode Switch (LTPO / Variable Refresh Rate)
        val eco1hzContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 24)
        }

        val eco1hzHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val labelEco1Hz = TextView(this).apply {
            text = getString(R.string.label_eco_1hz)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        switchEco1Hz = SwitchCompat(this).apply {
            isChecked = isEco1Hz
        }

        eco1hzHeaderRow.addView(labelEco1Hz)
        eco1hzHeaderRow.addView(switchEco1Hz)

        val descEco1Hz = TextView(this).apply {
            text = getString(R.string.desc_eco_1hz)
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 4, 0, 0)
        }

        eco1hzContainer.addView(eco1hzHeaderRow)
        eco1hzContainer.addView(descEco1Hz)

        val (is1HzSupported, infoStr) = checkDisplay1HzSupport()
        val infoEco1Hz = TextView(this).apply {
            if (is1HzSupported) {
                text = getString(R.string.status_1hz_supported, infoStr)
                textSize = 12f
                setTextColor(Color.parseColor("#4CAF50"))
            } else {
                text = getString(R.string.warning_1hz_unsupported)
                textSize = 12f
                setTextColor(Color.parseColor("#FFA500"))
            }
            setPadding(0, 4, 0, 0)
        }
        eco1hzContainer.addView(infoEco1Hz)

        updateFadeControlsState(isEco1Hz)
        updateDurationsDisplay()

        switchEco1Hz.setOnCheckedChangeListener { _, isChecked ->
            switchEco1Hz.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            isEco1Hz = isChecked
            if (isChecked) {
                currentOnDuration = (((currentOnDuration + 500L) / 1000L) * 1000L).coerceIn(1000L, 5000L)
                currentOffDuration = (((currentOffDuration + 500L) / 1000L) * 1000L).coerceIn(1000L, 30000L)
            }
            updateFadeControlsState(isChecked)
            updateDurationsDisplay()
            savePreferences()
        }

        // Buttons
        val btnSaveAndTest = Button(this).apply {
            text = getString(R.string.btn_test)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                savePreferences()
                val intent = Intent(this@MainActivity, TestAODiodeActivity::class.java).apply {
                    putExtra("AOD_COLOR", currentColor)
                    putExtra("AOD_SHAPE_TYPE", currentShapeType)
                    putExtra("AOD_RADIUS", currentRadius)
                    putExtra("AOD_BRIGHTNESS", currentBrightness)
                    putExtra("AOD_ON_DURATION", currentOnDuration)
                    putExtra("AOD_OFF_DURATION", currentOffDuration)
                    putExtra("AOD_FADE_DURATION", currentFadeDuration)
                    putExtra("AOD_ECO_1HZ", isEco1Hz)
                    putExtra("AOD_BURNOUT", prefs.getInt("pref_burnout_method", 2))
                }
                startActivity(intent)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 32, 0, 12) }
        }

        val btnAdvancedSettings = Button(this).apply {
            text = getString(R.string.btn_advanced_settings)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                startActivity(Intent(this@MainActivity, AdvancedSettingsActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
        }

        val btnBurnout = Button(this).apply {
            text = getString(R.string.btn_burnout)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                startActivity(Intent(this@MainActivity, BurnoutProtectionActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
        }

        val btnPermissions = Button(this).apply {
            text = getString(R.string.nav_permissions_lang)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                startActivity(Intent(this@MainActivity, PermissionSetupActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val btnAboutGitHub = TextView(this).apply {
            text = getString(R.string.btn_about_github)
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(16, 24, 16, 24)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Flony/LEDNotify"))
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 16) }
        }

        layout.addView(headerLayout)
        layout.addView(labelColor)
        layout.addView(colorScroll)
        layout.addView(btnAppColors)
        layout.addView(labelShape)
        layout.addView(shapeToggleLayout)
        layout.addView(labelBrightness)
        layout.addView(brightnessValueText)
        layout.addView(seekBarBrightness)
        layout.addView(labelOnDur)
        layout.addView(onDurationValueText)
        layout.addView(seekBarOnDur)
        layout.addView(labelOffDur)
        layout.addView(offDurationValueText)
        layout.addView(seekBarOffDur)
        layout.addView(labelFade)
        layout.addView(fadeDurationValueText)
        layout.addView(seekBarFade)
        layout.addView(eco1hzContainer)
        layout.addView(btnSaveAndTest)
        layout.addView(btnAdvancedSettings)
        layout.addView(btnBurnout)
        layout.addView(btnPermissions)
        layout.addView(btnAboutGitHub)
        scrollView.addView(layout)

        setContentView(scrollView)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left + 48, systemBars.top + 24, systemBars.right + 48, systemBars.bottom + 24)
            insets
        }
    }

    private fun showAdvancedColorPickerDialog(onChosen: (Int) -> Unit) {
        var tempColor = currentColor
        val wheelView = ColorPickerWheelView(this, tempColor) { newColor ->
            tempColor = newColor
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            addView(wheelView, LinearLayout.LayoutParams(600, 600))
        }

        AlertDialog.Builder(this)
            .setTitle("Vlastní spektrum barev")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                onChosen(tempColor)
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
                resetToDefaults(prefs)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateDurationsDisplay() {
        if (isEco1Hz) {
            val secOn = (currentOnDuration / 1000L).coerceIn(1L, 5L)
            currentOnDuration = secOn * 1000L
            if (::onDurationValueText.isInitialized) {
                onDurationValueText.text = getString(R.string.on_dur_sec_format, secOn.toInt())
            }

            val secOff = (currentOffDuration / 1000L).coerceIn(1L, 30L)
            currentOffDuration = secOff * 1000L
            if (::offDurationValueText.isInitialized) {
                offDurationValueText.text = getString(R.string.off_dur_sec_format, secOff.toInt())
            }
        } else {
            if (::onDurationValueText.isInitialized) {
                onDurationValueText.text = getString(R.string.on_dur_format, currentOnDuration)
            }
            if (::offDurationValueText.isInitialized) {
                offDurationValueText.text = getString(R.string.off_dur_format, currentOffDuration / 1000.0, currentOffDuration)
            }
        }
    }

    private fun checkDisplay1HzSupport(): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentDisplay = display
            if (currentDisplay != null) {
                val minRate = currentDisplay.supportedModes.map { it.refreshRate }.minOrNull() ?: 60f
                val statusText = if (minRate <= 30f) "${minRate.toInt()} Hz" else "LTPO (1 Hz)"
                return Pair(true, statusText)
            }
        }
        return Pair(false, "Android < 11")
    }

    private fun updateFadeControlsState(ecoEnabled: Boolean) {
        val enabled = !ecoEnabled
        if (::labelFade.isInitialized) {
            labelFade.isEnabled = enabled
            labelFade.alpha = if (enabled) 1.0f else 0.4f
        }
        if (::fadeDurationValueText.isInitialized) {
            fadeDurationValueText.isEnabled = enabled
            fadeDurationValueText.alpha = if (enabled) 1.0f else 0.4f
        }
        if (::seekBarFade.isInitialized) {
            seekBarFade.isEnabled = enabled
            seekBarFade.alpha = if (enabled) 1.0f else 0.4f
        }
    }

    private fun resetToDefaults(prefs: SharedPreferences) {
        currentColor = Color.RED
        currentShapeType = 1
        currentRadius = 38f
        currentBrightness = 100
        currentOnDuration = 300L
        currentOffDuration = 3500L
        currentFadeDuration = 2000L
        isEco1Hz = false

        prefs.edit().apply {
            putInt("pref_color", currentColor)
            putInt("pref_shape_type", currentShapeType)
            putBoolean("pref_is_dot", false)
            putFloat("pref_radius", currentRadius)
            putInt("pref_brightness", currentBrightness)
            putLong("pref_on_duration", currentOnDuration)
            putLong("pref_off_duration", currentOffDuration)
            putLong("pref_fade_duration", currentFadeDuration)
            putBoolean("pref_eco_1hz", isEco1Hz)
            putInt("pref_burnout_method", 2)
            apply()
        }

        updateColorSelectionUI()
        updateShapeToggleUI()
        if (::switchEco1Hz.isInitialized) {
            switchEco1Hz.isChecked = false
        }
        updateFadeControlsState(false)
        seekBarBrightness.progress = currentBrightness
        seekBarOnDur.progress = (currentOnDuration - 50L).toInt()
        seekBarOffDur.progress = (currentOffDuration - 1000L).toInt()
        seekBarFade.progress = currentFadeDuration.toInt()

        previewDrawable?.updateConfig(currentColor, currentShapeType, currentRadius)
        previewImageView.invalidate()
    }

    private fun showAsciiCustomInputDialog() {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val currentText = prefs.getString("pref_ascii_text", "♥") ?: "♥"

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 32, 32, 32)
        }

        val hintTv = TextView(this).apply {
            text = getString(R.string.hint_ascii_input)
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 24)
        }

        val inputEt = EditText(this).apply {
            setText(currentText)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(currentColor)
            gravity = Gravity.CENTER
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(15))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                setStroke(3, Color.WHITE)
                cornerRadius = 16f
            }
            setPadding(24, 24, 24, 24)
        }

        val presetScroll = HorizontalScrollView(this).apply {
            isFillViewport = false
            setPadding(0, 24, 0, 16)
        }

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val presets = listOf("♥", "★", "✚", "⚡", "✿", "✦", "◆", "●", "▲", "⊂(◉‿◉)つ", "(•‿•)")

        presets.forEach { symbol ->
            val chipBtn = TextView(this).apply {
                text = symbol
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(20, 12, 20, 12)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor("#333333"))
                    setStroke(1, Color.GRAY)
                    cornerRadius = 12f
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(6, 0, 6, 0) }

                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    inputEt.setText(symbol)
                    inputEt.setSelection(symbol.length)
                }
            }
            presetRow.addView(chipBtn)
        }

        presetScroll.addView(presetRow)

        container.addView(hintTv)
        container.addView(inputEt)
        container.addView(presetScroll)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_ascii_input))
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val newText = inputEt.text.toString().ifEmpty { "♥" }
                prefs.edit().putString("pref_ascii_text", newText).apply()
                updateLivePreview()
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun updateLivePreview() {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val pillWidthRatio = prefs.getFloat("pref_pill_width_ratio", 2.5f)
        val pillHeightRatio = prefs.getFloat("pref_pill_height_ratio", 1.5f)
        val asciiText = prefs.getString("pref_ascii_text", "♥") ?: "♥"
        previewDrawable?.setPillRatios(pillWidthRatio, pillHeightRatio)
        previewDrawable?.setAsciiText(asciiText)
        previewDrawable?.updateConfig(currentColor, currentShapeType, currentRadius)
        if (::previewImageView.isInitialized) {
            previewImageView.invalidate()
        }
    }

    private fun createColorSquareDrawable(colorInt: Int, isSelected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(colorInt)
            if (isSelected) {
                setStroke(6, Color.WHITE)
            } else {
                setStroke(1, Color.DKGRAY)
            }
        }
    }

    private fun updateColorSelectionUI() {
        colorOptions.forEachIndexed { index, colorInt ->
            colorViews[index].background = createColorSquareDrawable(colorInt, colorInt == currentColor)
        }
        updateLivePreview()
    }

    private fun updateShapeToggleUI() {
        if (::iconDot.isInitialized && ::iconRing.isInitialized && ::iconPill.isInitialized && ::iconEmptyPill.isInitialized && ::iconAscii.isInitialized) {
            iconDot.alpha = if (currentShapeType == 0) 1.0f else 0.35f
            iconRing.alpha = if (currentShapeType == 1) 1.0f else 0.35f
            iconPill.alpha = if (currentShapeType == 2) 1.0f else 0.35f
            iconEmptyPill.alpha = if (currentShapeType == 3) 1.0f else 0.35f
            iconAscii.alpha = if (currentShapeType == 4) 1.0f else 0.35f
        }
        updateLivePreview()
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        prefs.edit().apply {
            putInt("pref_color", currentColor)
            putInt("pref_shape_type", currentShapeType)
            putBoolean("pref_is_dot", currentShapeType == 0)
            putFloat("pref_radius", currentRadius)
            putInt("pref_brightness", currentBrightness)
            putLong("pref_on_duration", currentOnDuration)
            putLong("pref_off_duration", currentOffDuration)
            putLong("pref_fade_duration", currentFadeDuration)
            putBoolean("pref_eco_1hz", isEco1Hz)
            apply()
        }

        updateLivePreview()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        currentRadius = prefs.getFloat("pref_radius", 38f)
        updateLivePreview()
        val appEnabled = prefs.getBoolean("pref_app_enabled", true)
        val notifGranted = isNotificationServiceEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)

        title = when {
            !appEnabled -> getString(R.string.title_disabled)
            notifGranted && overlayGranted -> getString(R.string.title_active)
            notifGranted -> getString(R.string.title_missing_overlay)
            else -> getString(R.string.title_waiting_permission)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, LedNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun isSystemAodEnabled(): Boolean {
        val resolver = contentResolver
        val secureKeys = listOf("doze_always_on", "aod_using", "aod_enable", "secure_gesture_aod_enable")
        for (key in secureKeys) {
            try {
                if (Settings.Secure.getInt(resolver, key, 0) == 1) return true
            } catch (_: Exception) {}
        }
        val systemKeys = listOf("aod_mode", "aod_mode_state", "aod_switch", "ambient_display_enabled")
        for (key in systemKeys) {
            try {
                if (Settings.System.getInt(resolver, key, 0) != 0) return true
            } catch (_: Exception) {}
        }
        return false
    }
}

package com.flony.lednotify

import android.app.TimePickerDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AdvancedSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("led_notify_prefs", MODE_PRIVATE) }

    private var timeoutMin = 60
    private var isDndEnabled = false
    private var isUseSystemDnd = false
    private var dndStartHour = 23
    private var dndStartMin = 0
    private var dndEndHour = 7
    private var dndEndMin = 0

    private var isLowBatteryEnabled = true
    private var lowBatteryPercent = 20

    private var isChargingLedEnabled = false

    private lateinit var tvTimeoutValue: TextView
    private lateinit var seekBarTimeout: SeekBar

    private lateinit var switchDnd: SwitchCompat
    private lateinit var switchUseSystemDnd: SwitchCompat
    private lateinit var btnDndStart: Button
    private lateinit var btnDndEnd: Button

    private lateinit var switchLowBattery: SwitchCompat
    private lateinit var tvLowBatteryValue: TextView
    private lateinit var seekBarLowBattery: SeekBar

    private lateinit var switchChargingLed: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.title_settings)

        timeoutMin = prefs.getInt("pref_aodiode_timeout_min", 60)
        isDndEnabled = prefs.getBoolean("pref_dnd_enabled", false)
        isUseSystemDnd = prefs.getBoolean("pref_use_system_dnd", false)
        dndStartHour = prefs.getInt("pref_dnd_start_hour", 23)
        dndStartMin = prefs.getInt("pref_dnd_start_min", 0)
        dndEndHour = prefs.getInt("pref_dnd_end_hour", 7)
        dndEndMin = prefs.getInt("pref_dnd_end_min", 0)

        isLowBatteryEnabled = prefs.getBoolean("pref_low_battery_enabled", true)
        lowBatteryPercent = prefs.getInt("pref_low_battery_percent", 20)

        isChargingLedEnabled = prefs.getBoolean("pref_charging_led_enabled", false)

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

        val mainTitle = TextView(this).apply {
            text = getString(R.string.title_advanced_settings)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        layout.addView(mainTitle)

        // ==========================================
        // 1. AODiode Timeout Setting
        // ==========================================
        val sectionTimeout = createCardContainer()

        val tvTimeoutHeader = TextView(this).apply {
            text = getString(R.string.label_timeout_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        tvTimeoutValue = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }

        seekBarTimeout = SeekBar(this).apply {
            max = 48 // 48 * 15 min = 720 min = 12 hours
            progress = (timeoutMin / 15).coerceIn(0, 48)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    timeoutMin = progress * 15
                    updateTimeoutText()
                    if (fromUser) {
                        seekBar?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        savePreferences()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val descTimeout = TextView(this).apply {
            text = getString(R.string.desc_timeout)
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 0)
        }

        updateTimeoutText()

        sectionTimeout.addView(tvTimeoutHeader)
        sectionTimeout.addView(tvTimeoutValue)
        sectionTimeout.addView(seekBarTimeout)
        sectionTimeout.addView(descTimeout)
        layout.addView(sectionTimeout)

        // ==========================================
        // 2. Quiet Hours / DND Setting
        // ==========================================
        val sectionDnd = createCardContainer()

        val tvDndSectionTitle = TextView(this).apply {
            text = getString(R.string.label_dnd_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }

        // Option 2A: System DND
        val sysDndRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 4)
        }

        val tvSysDndHeader = TextView(this).apply {
            text = getString(R.string.label_use_system_dnd)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        switchUseSystemDnd = SwitchCompat(this).apply {
            isChecked = isUseSystemDnd
            setOnCheckedChangeListener { _, isChecked ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isUseSystemDnd = isChecked
                if (isChecked) {
                    isDndEnabled = false
                    switchDnd.isChecked = false
                }
                updateDndControlsState()
                savePreferences()
            }
        }

        sysDndRow.addView(tvSysDndHeader)
        sysDndRow.addView(switchUseSystemDnd)

        val descSysDnd = TextView(this).apply {
            text = getString(R.string.desc_system_dnd)
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 16)
        }

        // Option 2B: Custom Quiet Hours
        val dndHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }

        val tvDndCustomHeader = TextView(this).apply {
            text = getString(R.string.label_dnd_title)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        switchDnd = SwitchCompat(this).apply {
            isChecked = isDndEnabled
            setOnCheckedChangeListener { _, isChecked ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isDndEnabled = isChecked
                if (isChecked) {
                    isUseSystemDnd = false
                    switchUseSystemDnd.isChecked = false
                }
                updateDndControlsState()
                savePreferences()
            }
        }

        dndHeaderRow.addView(tvDndCustomHeader)
        dndHeaderRow.addView(switchDnd)

        val dndTimesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }

        btnDndStart = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                TimePickerDialog(this@AdvancedSettingsActivity, { _, hourOfDay, minute ->
                    dndStartHour = hourOfDay
                    dndStartMin = minute
                    updateDndButtonsText()
                    savePreferences()
                }, dndStartHour, dndStartMin, true).show()
            }
        }

        btnDndEnd = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                TimePickerDialog(this@AdvancedSettingsActivity, { _, hourOfDay, minute ->
                    dndEndHour = hourOfDay
                    dndEndMin = minute
                    updateDndButtonsText()
                    savePreferences()
                }, dndEndHour, dndEndMin, true).show()
            }
        }

        dndTimesRow.addView(btnDndStart)
        dndTimesRow.addView(btnDndEnd)

        val descDnd = TextView(this).apply {
            text = getString(R.string.desc_dnd)
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 4, 0, 0)
        }

        updateDndButtonsText()

        sectionDnd.addView(tvDndSectionTitle)
        sectionDnd.addView(sysDndRow)
        sectionDnd.addView(descSysDnd)
        sectionDnd.addView(dndHeaderRow)
        sectionDnd.addView(dndTimesRow)
        sectionDnd.addView(descDnd)
        updateDndControlsState()

        layout.addView(sectionDnd)

        // ==========================================
        // 3. Low Battery Auto-Disable Setting
        // ==========================================
        val sectionLowBattery = createCardContainer()

        val lowBatHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }

        val tvLowBatHeader = TextView(this).apply {
            text = getString(R.string.label_low_battery_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        switchLowBattery = SwitchCompat(this).apply {
            isChecked = isLowBatteryEnabled
            setOnCheckedChangeListener { _, isChecked ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isLowBatteryEnabled = isChecked
                updateLowBatteryControlsState()
                savePreferences()
            }
        }

        lowBatHeaderRow.addView(tvLowBatHeader)
        lowBatHeaderRow.addView(switchLowBattery)

        tvLowBatteryValue = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }

        seekBarLowBattery = SeekBar(this).apply {
            max = 45 // range 5% to 50%
            progress = (lowBatteryPercent - 5).coerceIn(0, 45)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    lowBatteryPercent = progress + 5
                    updateLowBatteryText()
                    if (fromUser) {
                        seekBar?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        savePreferences()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val descLowBattery = TextView(this).apply {
            text = getString(R.string.desc_low_battery)
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 0)
        }

        updateLowBatteryText()
        updateLowBatteryControlsState()

        sectionLowBattery.addView(lowBatHeaderRow)
        sectionLowBattery.addView(tvLowBatteryValue)
        sectionLowBattery.addView(seekBarLowBattery)
        sectionLowBattery.addView(descLowBattery)
        layout.addView(sectionLowBattery)

        // ==========================================
        // 4. Charging LED Indicator Setting
        // ==========================================
        val sectionCharging = createCardContainer()

        val chargingHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }

        val tvChargingHeader = TextView(this).apply {
            text = getString(R.string.label_charging_led_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        switchChargingLed = SwitchCompat(this).apply {
            isChecked = isChargingLedEnabled
            setOnCheckedChangeListener { _, isChecked ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isChargingLedEnabled = isChecked
                savePreferences()
            }
        }

        chargingHeaderRow.addView(tvChargingHeader)
        chargingHeaderRow.addView(switchChargingLed)

        val descCharging = TextView(this).apply {
            text = getString(R.string.desc_charging_led)
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 4, 0, 0)
        }

        sectionCharging.addView(chargingHeaderRow)
        sectionCharging.addView(descCharging)
        layout.addView(sectionCharging)

        scrollView.addView(layout)
        setContentView(scrollView)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left + 48, systemBars.top + 24, systemBars.right + 48, systemBars.bottom + 24)
            insets
        }
    }

    private fun createCardContainer(): LinearLayout {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cardBgColor = if (isNight) Color.parseColor("#1AFFFFFF") else Color.parseColor("#0D000000")

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 32) }
            setBackgroundColor(cardBgColor)
        }
    }

    private fun updateTimeoutText() {
        if (timeoutMin == 0) {
            tvTimeoutValue.text = getString(R.string.label_timeout_off)
        } else if (timeoutMin < 60) {
            tvTimeoutValue.text = getString(R.string.label_timeout_min_fmt, timeoutMin)
        } else {
            val hrs = timeoutMin / 60
            val mins = timeoutMin % 60
            tvTimeoutValue.text = getString(R.string.label_timeout_hrs_fmt, hrs, mins)
        }
    }

    private fun updateDndButtonsText() {
        btnDndStart.text = getString(R.string.label_dnd_from, dndStartHour, dndStartMin)
        btnDndEnd.text = getString(R.string.label_dnd_to, dndEndHour, dndEndMin)
    }

    private fun updateDndControlsState() {
        val customDndAllowed = !isUseSystemDnd
        switchDnd.isEnabled = customDndAllowed
        switchDnd.alpha = if (customDndAllowed) 1.0f else 0.4f

        val timeActive = customDndAllowed && isDndEnabled
        btnDndStart.isEnabled = timeActive
        btnDndEnd.isEnabled = timeActive
        val alpha = if (timeActive) 1.0f else 0.4f
        btnDndStart.alpha = alpha
        btnDndEnd.alpha = alpha
    }

    private fun updateLowBatteryText() {
        tvLowBatteryValue.text = getString(R.string.label_low_battery_fmt, lowBatteryPercent)
    }

    private fun updateLowBatteryControlsState() {
        tvLowBatteryValue.isEnabled = isLowBatteryEnabled
        seekBarLowBattery.isEnabled = isLowBatteryEnabled
        val alpha = if (isLowBatteryEnabled) 1.0f else 0.4f
        tvLowBatteryValue.alpha = alpha
        seekBarLowBattery.alpha = alpha
    }

    private fun savePreferences() {
        prefs.edit().apply {
            putInt("pref_aodiode_timeout_min", timeoutMin)
            putBoolean("pref_dnd_enabled", isDndEnabled)
            putBoolean("pref_use_system_dnd", isUseSystemDnd)
            putInt("pref_dnd_start_hour", dndStartHour)
            putInt("pref_dnd_start_min", dndStartMin)
            putInt("pref_dnd_end_hour", dndEndHour)
            putInt("pref_dnd_end_min", dndEndMin)
            putBoolean("pref_low_battery_enabled", isLowBatteryEnabled)
            putInt("pref_low_battery_percent", lowBatteryPercent)
            putBoolean("pref_charging_led_enabled", isChargingLedEnabled)
            apply()
        }
    }
}

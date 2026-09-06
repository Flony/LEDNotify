package com.flony.lednotify

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AppFilterActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("led_notify_prefs", MODE_PRIVATE) }
    private val blacklistedPkgs = mutableSetOf<String>()
    private val appRowsMap = mutableMapOf<String, Pair<TextView, () -> Unit>>()

    private lateinit var containerLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.title_app_filter)

        // Load saved blacklisted packages
        val savedSet = prefs.getStringSet("pref_blacklisted_pkgs", emptySet()) ?: emptySet()
        blacklistedPkgs.addAll(savedSet)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
        }

        containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Header Section
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val titleTv = TextView(this).apply {
            text = getString(R.string.title_app_filter)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val subtitleTv = TextView(this).apply {
            text = getString(R.string.subtitle_app_filter)
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 16)
        }

        val btnInvert = Button(this).apply {
            text = getString(R.string.btn_invert_selection)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                invertSelection()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        headerLayout.addView(titleTv)
        headerLayout.addView(subtitleTv)
        headerLayout.addView(btnInvert)
        containerLayout.addView(headerLayout)

        buildAppList()
        scrollView.addView(containerLayout)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bottomBgColor = if (isNight) Color.parseColor("#1F1F1F") else Color.parseColor("#E0E0E0")

        // Bottom Save Button Container
        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 32)
            setBackgroundColor(bottomBgColor)
        }

        val btnSave = Button(this).apply {
            text = getString(R.string.btn_save)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                saveAndExit()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        bottomContainer.addView(btnSave)

        rootLayout.addView(scrollView)
        rootLayout.addView(bottomContainer)

        setContentView(rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun buildAppList() {
        val pm = packageManager

        // 1. System Events (Calls & SMS)
        val systemHeader = TextView(this).apply {
            text = getString(R.string.section_system)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 12)
        }
        containerLayout.addView(systemHeader)

        val callsRow = createAppFilterRow("system_calls", getString(R.string.event_calls), "system.calls", null)
        val smsRow = createAppFilterRow("system_sms", getString(R.string.event_sms), "system.sms", null)
        containerLayout.addView(callsRow)
        containerLayout.addView(smsRow)

        // 2. Installed Launcher Apps
        val appsHeader = TextView(this).apply {
            text = getString(R.string.section_installed)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 24, 0, 12)
        }
        containerLayout.addView(appsHeader)

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        val priorityKeywords = listOf("whatsapp", "signal", "telegram", "gmail", "messenger", "instagram", "facebook", "discord", "slack", "teams")

        val sortedApps = resolveInfos.map {
            val appName = it.loadLabel(pm).toString()
            val pkgName = it.activityInfo.packageName
            val icon = it.loadIcon(pm)
            Triple(appName, pkgName, icon)
        }.sortedWith(compareBy({ appInfo ->
            val lower = appInfo.second.lowercase()
            val index = priorityKeywords.indexOfFirst { lower.contains(it) }
            if (index == -1) 999 else index
        }, { it.first }))

        sortedApps.forEach { (appName, pkgName, icon) ->
            val appRow = createAppFilterRow(pkgName, appName, pkgName, icon)
            containerLayout.addView(appRow)
        }
    }

    private fun createAppFilterRow(key: String, appName: String, pkgName: String, icon: Drawable?): View {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cardBgColor = if (isNight) Color.parseColor("#1AFFFFFF") else Color.parseColor("#0D000000")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            setBackgroundColor(cardBgColor)
        }

        if (icon != null) {
            val iconIv = ImageView(this).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(72, 72).apply { setMargins(0, 0, 24, 0) }
            }
            row.addView(iconIv)
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameTv = TextView(this).apply {
            text = appName
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }
        val pkgTv = TextView(this).apply {
            text = pkgName
            textSize = 12f
            setTextColor(Color.GRAY)
        }

        textLayout.addView(nameTv)
        textLayout.addView(pkgTv)

        val statusTv = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(16, 0, 0, 0)
        }

        val updateStatusUI = {
            val isBlacklisted = blacklistedPkgs.contains(key)
            if (isBlacklisted) {
                statusTv.text = "❌"
            } else {
                statusTv.text = "✅"
            }
        }

        updateStatusUI()

        val toggleAction = {
            if (blacklistedPkgs.contains(key)) {
                blacklistedPkgs.remove(key)
            } else {
                blacklistedPkgs.add(key)
            }
            updateStatusUI()
        }

        appRowsMap[key] = Pair(statusTv, toggleAction)

        row.addView(textLayout)
        row.addView(statusTv)

        row.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            toggleAction()
        }

        return row
    }

    private fun invertSelection() {
        appRowsMap.forEach { (_, pair) ->
            val toggleAction = pair.second
            toggleAction()
        }
    }

    private fun saveAndExit() {
        prefs.edit().putStringSet("pref_blacklisted_pkgs", blacklistedPkgs).apply()
        finish()
    }
}

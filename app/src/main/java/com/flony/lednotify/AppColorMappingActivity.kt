package com.flony.lednotify

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AppColorMappingActivity : AppCompatActivity() {

    private lateinit var containerLayout: LinearLayout
    private val prefs by lazy { getSharedPreferences("led_notify_prefs", MODE_PRIVATE) }

    private val colorPalette = listOf(
        Color.GREEN, Color.BLUE, Color.RED, Color.YELLOW,
        Color.CYAN, Color.WHITE, Color.MAGENTA, Color.rgb(255, 128, 0), Color.rgb(128, 0, 128)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        scrollView.addView(containerLayout)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bottomBgColor = if (isNight) Color.parseColor("#1F1F1F") else Color.parseColor("#E0E0E0")

        // Bottom Sticky Action Bar (Plovoucí spodní lišta)
        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 32)
            setBackgroundColor(bottomBgColor)
        }

        val btnFilter = Button(this).apply {
            text = getString(R.string.btn_whitelist_blacklist)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                startActivity(Intent(this@AppColorMappingActivity, AppFilterActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        bottomContainer.addView(btnFilter)

        rootLayout.addView(scrollView)
        rootLayout.addView(bottomContainer)

        setContentView(rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        containerLayout.removeAllViews()

        val titleView = TextView(this).apply {
            text = getString(R.string.app_colors_title)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        val subtitleView = TextView(this).apply {
            text = getString(R.string.app_colors_subtitle)
            textSize = 15f
            setPadding(0, 0, 0, 32)
        }

        containerLayout.addView(titleView)
        containerLayout.addView(subtitleView)

        buildAppList()
    }

    private fun buildAppList() {
        val blacklistedPkgs = prefs.getStringSet("pref_blacklisted_pkgs", emptySet()) ?: emptySet()

        // 1. System Defaults Section: Zmeškané hovory & SMS Zprávy
        val systemHeader = TextView(this).apply {
            text = getString(R.string.section_system)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        containerLayout.addView(systemHeader)

        val systemRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 32) }
        }

        val globalColor = prefs.getInt("pref_color", Color.RED)
        val callColor = prefs.getInt("app_color_system_calls", globalColor)
        val smsColor = prefs.getInt("app_color_system_sms", globalColor)

        val isCallsBlacklisted = blacklistedPkgs.contains("system_calls")
        val isSmsBlacklisted = blacklistedPkgs.contains("system_sms")

        val btnCalls = createSystemAppButton(getString(R.string.event_calls), callColor, isCallsBlacklisted) {
            showColorPickerDialog("system_calls", getString(R.string.event_calls))
        }
        val btnSms = createSystemAppButton(getString(R.string.event_sms), smsColor, isSmsBlacklisted) {
            showColorPickerDialog("system_sms", getString(R.string.event_sms))
        }

        systemRow.addView(btnCalls)
        systemRow.addView(btnSms)
        containerLayout.addView(systemRow)

        // 2. Installed Apps Section
        val appsHeader = TextView(this).apply {
            text = getString(R.string.section_installed)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        containerLayout.addView(appsHeader)

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)

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
            val isBlacklisted = blacklistedPkgs.contains(pkgName)
            val appRow = createInstalledAppRow(appName, pkgName, icon, isBlacklisted)
            containerLayout.addView(appRow)
        }
    }

    private fun createSystemAppButton(label: String, color: Int, isBlacklisted: Boolean, onClick: () -> Unit): View {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cardBgColor = if (isNight) Color.parseColor("#1AFFFFFF") else Color.parseColor("#0D000000")

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 8, 0)
            }
            setBackgroundColor(cardBgColor)

            val tv = TextView(context).apply {
                text = label
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            }
            val indicator = if (isBlacklisted) {
                TextView(context).apply {
                    text = getString(R.string.label_blacklisted)
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FFA500"))
                }
            } else {
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(color)
                    }
                }
            }

            addView(tv)
            addView(indicator)

            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
        }
    }

    private fun createInstalledAppRow(appName: String, pkgName: String, icon: Drawable?, isBlacklisted: Boolean): View {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cardBgColor = if (isNight) Color.parseColor("#1AFFFFFF") else Color.parseColor("#0D000000")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            setBackgroundColor(cardBgColor)
        }

        val iconIv = ImageView(this).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(0, 0, 24, 0) }
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameTv = TextView(this).apply {
            text = appName
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        val pkgTv = TextView(this).apply {
            text = pkgName
            textSize = 12f
            setTextColor(Color.GRAY)
        }

        textLayout.addView(nameTv)
        textLayout.addView(pkgTv)

        val rightIndicator = if (isBlacklisted) {
            TextView(this).apply {
                text = getString(R.string.label_blacklisted)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#FFA500"))
                setPadding(8, 0, 0, 0)
            }
        } else {
            val globalColor = prefs.getInt("pref_color", Color.RED)
            val savedColor = prefs.getInt("app_color_$pkgName", -1)
            val assignedColor = if (savedColor != -1) savedColor else globalColor

            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(assignedColor)
                    setStroke(2, Color.DKGRAY)
                }
            }
        }

        row.addView(iconIv)
        row.addView(textLayout)
        row.addView(rightIndicator)

        row.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            showColorPickerDialog(pkgName, appName)
        }

        return row
    }

    private fun showColorPickerDialog(key: String, title: String) {
        val horizontalScroll = HorizontalScrollView(this).apply {
            isFillViewport = false
        }
        val colorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        horizontalScroll.addView(colorLayout)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.color_picker_title, title))
            .setView(horizontalScroll)
            .setNeutralButton(getString(R.string.btn_use_global)) { _, _ ->
                prefs.edit().remove("app_color_$key").apply()
                recreate()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()

        val squareSize = (31 * resources.displayMetrics.density).toInt()
        val squareMargin = 1

        colorPalette.forEach { colorInt ->
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(squareSize, squareSize).apply {
                    setMargins(squareMargin, squareMargin, squareMargin, squareMargin)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(colorInt)
                    setStroke(2, Color.WHITE)
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    prefs.edit().putInt("app_color_$key", colorInt).apply()
                    dialog.dismiss()
                    recreate()
                }
            }
            colorLayout.addView(swatch)
        }

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
                showAdvancedColorPickerDialog(key, title) { newColor ->
                    prefs.edit().putInt("app_color_$key", newColor).apply()
                    dialog.dismiss()
                    recreate()
                }
            }
        }
        colorLayout.addView(customColorButton)

        dialog.show()
    }

    private fun showAdvancedColorPickerDialog(key: String, title: String, onChosen: (Int) -> Unit) {
        val globalColor = prefs.getInt("pref_color", Color.RED)
        val savedColor = prefs.getInt("app_color_$key", -1)
        var tempColor = if (savedColor != -1) savedColor else globalColor

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
            .setTitle(getString(R.string.custom_spectrum_title, title))
            .setView(container)
            .setPositiveButton(getString(R.string.btn_ok)) { _, _ ->
                onChosen(tempColor)
            }
            .setNeutralButton(getString(R.string.btn_use_global)) { _, _ ->
                prefs.edit().remove("app_color_$key").apply()
                recreate()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}

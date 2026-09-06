package com.flony.lednotify

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PermissionSetupActivity : AppCompatActivity() {

    private lateinit var statusNotification: TextView
    private lateinit var statusOverlay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            text = getString(R.string.perm_title)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        val subtitleView = TextView(this).apply {
            text = getString(R.string.perm_subtitle)
            textSize = 15f
            setPadding(0, 0, 0, 32)
        }

        statusNotification = TextView(this).apply {
            textSize = 14f
        }
        val btnNotification = Button(this).apply {
            text = getString(R.string.btn_perm_notif)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 24) }
        }

        statusOverlay = TextView(this).apply {
            textSize = 14f
        }
        val btnOverlay = Button(this).apply {
            text = getString(R.string.btn_perm_overlay)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@PermissionSetupActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 32) }
        }

        // Language Section
        val labelLang = TextView(this).apply {
            text = getString(R.string.lang_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 32, 0, 16)
        }

        val languages = listOf(
            getString(R.string.lang_system) to "",
            getString(R.string.lang_czech) to "cs",
            getString(R.string.lang_english) to "en",
            getString(R.string.lang_german) to "de",
            getString(R.string.lang_slovak) to "sk",
            getString(R.string.lang_polish) to "pl",
            getString(R.string.lang_spanish) to "es",
            getString(R.string.lang_portuguese) to "pt",
            getString(R.string.lang_chinese) to "zh"
        )

        val radioGroupLang = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()

        languages.forEach { (name, tag) ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = name
                this.tag = tag
                isChecked = if (tag.isEmpty()) currentTag.isEmpty() else currentTag.contains(tag)
            }
            radioGroupLang.addView(rb)
        }

        radioGroupLang.setOnCheckedChangeListener { group, checkedId ->
            group.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            val rb = findViewById<RadioButton>(checkedId)
            val tag = rb?.tag as? String ?: ""
            val appLocale = LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(appLocale)
            recreate()
        }

        val btnContinue = Button(this).apply {
            text = getString(R.string.btn_continue)
            setOnClickListener {
                val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
                prefs.edit().putBoolean("setup_completed", true).apply()
                startActivity(Intent(this@PermissionSetupActivity, MainActivity::class.java))
                finish()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 32, 0, 16) }
        }

        layout.addView(titleView)
        layout.addView(subtitleView)
        layout.addView(statusNotification)
        layout.addView(btnNotification)
        layout.addView(statusOverlay)
        layout.addView(btnOverlay)
        layout.addView(labelLang)
        layout.addView(radioGroupLang)
        layout.addView(btnContinue)
        scrollView.addView(layout)

        setContentView(scrollView)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left + 48, systemBars.top + 24, systemBars.right + 48, systemBars.bottom + 24)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        val notifGranted = isNotificationServiceEnabled()
        val overlayGranted = Settings.canDrawOverlays(this)

        statusNotification.text = if (notifGranted) getString(R.string.status_notif_granted) else getString(R.string.status_notif_denied)
        statusOverlay.text = if (overlayGranted) getString(R.string.status_overlay_granted) else getString(R.string.status_overlay_denied)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, LedNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}

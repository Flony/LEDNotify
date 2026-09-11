package com.flony.lednotify

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("privacy_accepted", false)) {
            val target = if (prefs.getBoolean("setup_completed", false)) {
                MainActivity::class.java
            } else {
                PermissionSetupActivity::class.java
            }
            startActivity(Intent(this, target))
            finish()
            return
        }

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
            text = getString(R.string.welcome_title)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }

        val bodyView = TextView(this).apply {
            text = getString(R.string.welcome_body)
            textSize = 16f
            setPadding(0, 0, 0, 48)
        }

        val btnAgree = Button(this).apply {
            text = getString(R.string.btn_agree)
            setOnClickListener {
                prefs.edit().putBoolean("privacy_accepted", true).apply()
                startActivity(Intent(this@WelcomeActivity, PermissionSetupActivity::class.java))
                finish()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 16) }
        }

        val btnDisagree = Button(this).apply {
            text = getString(R.string.btn_disagree)
            setOnClickListener {
                finishAffinity()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 32) }
        }

        val warning1 = TextView(this).apply {
            text = getString(R.string.warning_oled)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.RED)
            setPadding(0, 16, 0, 8)
        }

        val warning2 = TextView(this).apply {
            text = getString(R.string.warning_lcd)
            textSize = 13f
            setTextColor(Color.RED)
            setPadding(0, 0, 0, 24)
        }

        layout.addView(titleView)
        layout.addView(bodyView)
        layout.addView(btnAgree)
        layout.addView(btnDisagree)
        layout.addView(warning1)
        layout.addView(warning2)
        scrollView.addView(layout)

        setContentView(scrollView)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left + 48, systemBars.top + 24, systemBars.right + 48, systemBars.bottom + 24)
            insets
        }
    }
}

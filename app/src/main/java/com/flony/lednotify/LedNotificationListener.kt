package com.flony.lednotify

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Calendar

class LedNotificationListener : NotificationListenerService() {

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
            val appEnabled = prefs.getBoolean("pref_app_enabled", true)
            val chargingLedEnabled = prefs.getBoolean("pref_charging_led_enabled", false)
            if (!appEnabled || !chargingLedEnabled) return

            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (action == Intent.ACTION_POWER_CONNECTED || action == Intent.ACTION_BATTERY_CHANGED) {
                if (!pm.isInteractive) {
                    val batteryStatus: Intent? = context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                    if (isCharging) {
                        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

                        val color = if (pct >= 100) Color.GREEN else Color.rgb(255, 128, 0)
                        startChargingLed(color)
                    }
                }
            } else if (action == Intent.ACTION_POWER_DISCONNECTED) {
                val active = activeNotifications?.filter { !it.isOngoing } ?: emptyList()
                if (active.isEmpty()) {
                    sendBroadcast(Intent("com.flony.lednotify.ACTION_CLEAR_LED"))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // Ignorovat probíhající/trvalé notifikace (přehrávač hudby, stahování)
        if (sbn.isOngoing) return

        val flags = sbn.notification.flags
        if ((flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val appEnabled = prefs.getBoolean("pref_app_enabled", true)
        if (!appEnabled) return

        val blacklistedPkgs = prefs.getStringSet("pref_blacklisted_pkgs", emptySet()) ?: emptySet()
        val pkg = sbn.packageName

        if (blacklistedPkgs.contains(pkg)) return
        if ((pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("telecom")) && blacklistedPkgs.contains("system_calls")) return
        if ((pkg.contains("mms") || pkg.contains("messaging") || pkg.contains("sms")) && blacklistedPkgs.contains("system_sms")) return

        // Spustit pouze pokud je obrazovka v danou chvíli zhasnutá a nejsou aktivní omezení
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            if (isBatteryTooLow() || isInQuietHours() || isSystemDndActive()) return
            checkProximityAndStartLed(sbn)
        }
    }

    private fun isBatteryTooLow(): Boolean {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val lowBatteryEnabled = prefs.getBoolean("pref_low_battery_enabled", true)
        if (!lowBatteryEnabled) return false

        val threshold = prefs.getInt("pref_low_battery_percent", 20)
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        if (level >= 0 && scale > 0) {
            val batteryPct = (level * 100f / scale.toFloat()).toInt()
            if (batteryPct <= threshold) {
                return true
            }
        }
        return false
    }

    private fun isInQuietHours(): Boolean {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val dndEnabled = prefs.getBoolean("pref_dnd_enabled", false)
        if (!dndEnabled) return false

        val startHour = prefs.getInt("pref_dnd_start_hour", 23)
        val startMin = prefs.getInt("pref_dnd_start_min", 0)
        val endHour = prefs.getInt("pref_dnd_end_hour", 7)
        val endMin = prefs.getInt("pref_dnd_end_min", 0)

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMin
        val endMinutes = endHour * 60 + endMin

        return if (startMinutes < endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else if (startMinutes > endMinutes) {
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        } else {
            false
        }
    }

    private fun isSystemDndActive(): Boolean {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val useSystemDnd = prefs.getBoolean("pref_use_system_dnd", false)
        if (!useSystemDnd) return false

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val filter = nm.currentInterruptionFilter
        return filter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                filter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }

    private fun checkProximityAndStartLed(sbn: StatusBarNotification) {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (proximitySensor == null) {
            startLedActivity(sbn)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var listenerRegistered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                if (!listenerRegistered) return
                listenerRegistered = false
                try {
                    sensorManager.unregisterListener(this)
                } catch (_: Exception) {}
                handler.removeCallbacksAndMessages(null)

                val distance = event.values[0]
                val maxRange = proximitySensor.maximumRange
                // Pokud je vzdálenost menší než maxRange a menší než 5cm, telefon je v kapse / displejem dolů
                val isCovered = distance < maxRange && distance < 5.0f

                if (!isCovered) {
                    startLedActivity(sbn)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Timeout 200ms pro případ, že senzor neodpoví okamžitě
        handler.postDelayed({
            if (listenerRegistered) {
                listenerRegistered = false
                try {
                    sensorManager.unregisterListener(listener)
                } catch (_: Exception) {}
                startLedActivity(sbn)
            }
        }, 200L)

        listenerRegistered = true
        sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun startLedActivity(sbn: StatusBarNotification) {
        val color = resolveColorForPackage(sbn.packageName)
        startChargingLed(color)
    }

    private fun startChargingLed(ledColor: Int) {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val defaultShape = if (prefs.getBoolean("pref_is_dot", false)) 0 else 1
        val shapeType = prefs.getInt("pref_shape_type", defaultShape)
        val radius = prefs.getFloat("pref_radius", 38f)
        val offsetX = prefs.getFloat("pref_offset_x", 0f)
        val offsetY = prefs.getFloat("pref_offset_y", 0f)
        val brightness = prefs.getInt("pref_brightness", 100)
        val onDuration = prefs.getLong("pref_on_duration", 300L)
        val offDuration = prefs.getLong("pref_off_duration", 3500L)
        val fadeDuration = prefs.getLong("pref_fade_duration", 2000L)
        val burnout = prefs.getInt("pref_burnout_method", 2)
        val isEco1Hz = prefs.getBoolean("pref_eco_1hz", false)

        val intent = Intent(this, LedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("AOD_COLOR", ledColor)
            putExtra("AOD_SHAPE_TYPE", shapeType)
            putExtra("AOD_RADIUS", radius)
            putExtra("AOD_OFFSET_X", offsetX)
            putExtra("AOD_OFFSET_Y", offsetY)
            putExtra("AOD_BRIGHTNESS", brightness)
            putExtra("AOD_ON_DURATION", onDuration)
            putExtra("AOD_OFF_DURATION", offDuration)
            putExtra("AOD_FADE_DURATION", fadeDuration)
            putExtra("AOD_ECO_1HZ", isEco1Hz)
            putExtra("AOD_BURNOUT", burnout)
        }
        startActivity(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Pokud uživatel smaže notifikaci na jiném zařízení / hodinkách, ukončí se LedActivity
        val active = activeNotifications?.filter { !it.isOngoing } ?: emptyList()
        if (active.isEmpty()) {
            sendBroadcast(Intent("com.flony.lednotify.ACTION_CLEAR_LED"))
        }
    }

    private fun resolveColorForPackage(pkg: String): Int {
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val globalColor = prefs.getInt("pref_color", Color.RED)

        // Speciální systémové události (hovory / SMS)
        if (pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("telecom")) {
            val callColor = prefs.getInt("app_color_system_calls", -1)
            if (callColor != -1) return callColor
        }
        if (pkg.contains("mms") || pkg.contains("messaging") || pkg.contains("sms")) {
            val smsColor = prefs.getInt("app_color_system_sms", -1)
            if (smsColor != -1) return smsColor
        }

        // Vlastní nastavení pro daný balíček aplikace
        val customColor = prefs.getInt("app_color_$pkg", -1)
        if (customColor != -1) return customColor

        // Všechny ostatní nenastavené aplikace se řídí globálním nastavením z MainActivity
        return globalColor
    }
}

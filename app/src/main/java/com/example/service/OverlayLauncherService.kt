package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlin.math.abs

class OverlayLauncherService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this) || bubble != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        bubble = TextView(this).apply {
            text = "WiFi"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(35, 99, 235))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(8).toFloat()
            setOnClickListener { togglePanel() }
            setOnLongClickListener {
                stopSelf()
                true
            }

            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        val lp = v.layoutParams as WindowManager.LayoutParams
                        startX = lp.x
                        startY = lp.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        val lp = v.layoutParams as WindowManager.LayoutParams
                        lp.x = startX + dx
                        lp.y = startY + dy
                        windowManager.updateViewLayout(v, lp)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val moved = abs(event.rawX - downX) > dp(12) || abs(event.rawY - downY) > dp(12)
                        if (!moved) v.performClick()
                        true
                    }
                    else -> true
                }
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            dp(58),
            dp(58),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(14)
            y = dp(220)
        }

        windowManager.addView(bubble, params)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun togglePanel() {
        if (panel != null) {
            runCatching { windowManager.removeView(panel) }
            panel = null
            return
        }
        if (!Settings.canDrawOverlays(this)) return

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.LTGRAY)
            }
            elevation = dp(12).toFloat()
        }

        fun addButton(label: String, onClick: () -> Unit) {
            val b = android.widget.Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { onClick() }
            }
            box.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { bottomMargin = dp(8) })
        }

        val title = TextView(this).apply {
            text = "WiFi Manager"
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        box.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ))

        addButton("📄  Import TXT") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "text/plain"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        addButton("📶  Select Wi-Fi") {
            openApp()
        }

        addButton("▶  Start Test") {
            android.widget.Toast.makeText(
                this,
                "Select an authorized Wi-Fi and imported test data first.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        addButton("✕  Close") {
            togglePanel()
        }

        panel = box

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(120)
        }

        windowManager.addView(box, params)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "WiFi Manager Overlay",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WiFi Manager overlay active")
            .setContentText("Floating button is available over other apps")
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        bubble?.let {
            runCatching { windowManager.removeView(it) }
        }
        bubble = null
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "wifi_overlay"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, OverlayLauncherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayLauncherService::class.java))
        }

        fun isEnabled(context: Context): Boolean =
            Settings.canDrawOverlays(context)
    }
}

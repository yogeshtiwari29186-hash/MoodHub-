package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.WifiManagerApp
import com.example.data.repository.PasswordBatchRepository
import com.example.model.ImportProgressStats
import com.example.model.PasswordBatchStatus
import com.example.receiver.NotificationActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.NumberFormat

class FileProcessingService : Service() {

    companion object {
        const val CHANNEL_ID = "file_processing_channel"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START_IMPORT = "com.example.service.ACTION_START_IMPORT"
        const val ACTION_RESUME_IMPORT = "com.example.service.ACTION_RESUME_IMPORT"
        const val ACTION_PAUSE_IMPORT = "com.example.service.ACTION_PAUSE_IMPORT"
        const val ACTION_STOP_IMPORT = "com.example.service.ACTION_STOP_IMPORT"

        const val EXTRA_FILE_URI = "extra_file_uri"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_RESUME = "extra_resume"

        fun startImport(context: Context, uri: Uri, fileName: String) {
            val intent = Intent(context, FileProcessingService::class.java).apply {
                action = ACTION_START_IMPORT
                putExtra(EXTRA_FILE_URI, uri.toString())
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_RESUME, false)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun resumeImport(context: Context, uri: Uri, fileName: String) {
            val intent = Intent(context, FileProcessingService::class.java).apply {
                action = ACTION_RESUME_IMPORT
                putExtra(EXTRA_FILE_URI, uri.toString())
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_RESUME, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pauseImport(context: Context) {
            val intent = Intent(context, FileProcessingService::class.java).apply {
                action = ACTION_PAUSE_IMPORT
            }
            context.startService(intent)
        }

        fun stopImport(context: Context) {
            val intent = Intent(context, FileProcessingService::class.java).apply {
                action = ACTION_STOP_IMPORT
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var processingJob: Job? = null
    private lateinit var batchRepo: PasswordBatchRepository
    private lateinit var notificationManager: NotificationManager
    private val numberFormat = NumberFormat.getNumberInstance()

    private var currentFileName = "passwords.txt"
    private var lastNotificationUpdateTime = 0L

    override fun onCreate() {
        super.onCreate()
        batchRepo = (application as WifiManagerApp).passwordBatchRepository
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_IMPORT -> {
                val uriStr = intent.getStringExtra(EXTRA_FILE_URI) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "passwords.txt"
                currentFileName = fileName
                startForegroundWithNotification(buildInitialNotification(fileName))
                startProcessing(Uri.parse(uriStr), resumeFromCheckpoint = false)
            }
            ACTION_RESUME_IMPORT -> {
                val uriStr = intent.getStringExtra(EXTRA_FILE_URI) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "passwords.txt"
                currentFileName = fileName
                startForegroundWithNotification(buildNotification(
                    title = "Resuming Indexing: $fileName",
                    contentText = "Restoring checkpoint and resuming...",
                    isOngoing = true,
                    isPaused = false
                ))
                startProcessing(Uri.parse(uriStr), resumeFromCheckpoint = true)
            }
            ACTION_PAUSE_IMPORT -> {
                batchRepo.requestPause()
                updateNotificationPaused()
            }
            ACTION_STOP_IMPORT -> {
                batchRepo.requestStop()
                processingJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProcessing(uri: Uri, resumeFromCheckpoint: Boolean) {
        processingJob?.cancel()
        processingJob = serviceScope.launch {
            val success = batchRepo.processFile(
                uri = uri,
                resumeFromCheckpoint = resumeFromCheckpoint,
                onProgressUpdate = { stats ->
                    throttleNotificationUpdate(stats)
                }
            )

            val currentState = batchRepo.batchUiState.value
            when (currentState.status) {
                PasswordBatchStatus.READY -> {
                    val formattedTotal = numberFormat.format(currentState.totalEntries)
                    val doneNotification = buildNotification(
                        title = "Indexing Complete: $currentFileName",
                        contentText = "$formattedTotal entries processed and ready.",
                        isOngoing = false,
                        isPaused = false,
                        progressPercent = 100,
                        isComplete = true
                    )
                    notificationManager.notify(NOTIFICATION_ID, doneNotification)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
                PasswordBatchStatus.PAUSED -> {
                    updateNotificationPaused()
                }
                PasswordBatchStatus.STOPPED -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                PasswordBatchStatus.ERROR -> {
                    val errNotification = buildNotification(
                        title = "Indexing Error: $currentFileName",
                        contentText = currentState.errorMessage ?: "Failed to process file.",
                        isOngoing = false,
                        isPaused = false
                    )
                    notificationManager.notify(NOTIFICATION_ID, errNotification)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
                else -> {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    private fun throttleNotificationUpdate(stats: ImportProgressStats) {
        val now = System.currentTimeMillis()
        // Update notification at most every 400ms to stay responsive while adhering to system limits
        if (now - lastNotificationUpdateTime >= 400L) {
            lastNotificationUpdateTime = now
            val formattedProcessed = numberFormat.format(stats.processedEntries)
            val formattedTotal = if (stats.totalEntries > 0) numberFormat.format(stats.totalEntries) else "calculating..."

            // Exact format required by prompt: "2,450,000 / 10,000,000 processed"
            val progressText = "$formattedProcessed / $formattedTotal processed"

            val speedText = if (stats.speedEntriesPerSec > 0) {
                "${numberFormat.format(stats.speedEntriesPerSec)} entries/sec"
            } else ""

            val etaText = if (stats.etaSeconds > 0) {
                formatEta(stats.etaSeconds)
            } else ""

            val detailLine = listOf(speedText, etaText).filter { it.isNotBlank() }.joinToString(" • ")

            val notification = buildNotification(
                title = "Processing $currentFileName",
                contentText = progressText,
                subText = detailLine.ifBlank { null },
                isOngoing = true,
                isPaused = false,
                progressPercent = stats.percentage.toInt().coerceIn(0, 100),
                isIndeterminate = stats.totalEntries <= stats.processedEntries && stats.percentage <= 0f
            )
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationPaused() {
        val stats = batchRepo.batchUiState.value.importProgress
        val formattedProcessed = if (stats != null) numberFormat.format(stats.processedEntries) else "0"
        val formattedTotal = if (stats != null && stats.totalEntries > 0) numberFormat.format(stats.totalEntries) else ""
        val progressText = if (formattedTotal.isNotBlank()) {
            "$formattedProcessed / $formattedTotal processed (Paused)"
        } else {
            "$formattedProcessed processed (Paused)"
        }

        val notification = buildNotification(
            title = "Import Paused: $currentFileName",
            contentText = progressText,
            isOngoing = true,
            isPaused = true,
            progressPercent = stats?.percentage?.toInt() ?: 0
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildInitialNotification(fileName: String): Notification {
        return buildNotification(
            title = "Starting Import: $fileName",
            contentText = "0 processed (streaming lines...)",
            isOngoing = true,
            isPaused = false,
            isIndeterminate = true
        )
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        subText: String? = null,
        isOngoing: Boolean,
        isPaused: Boolean,
        progressPercent: Int = 0,
        isIndeterminate: Boolean = false,
        isComplete: Boolean = false
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_tab", 2) // Navigate to Passwords screen
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.wifi_manager_icon_1790612086167)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isOngoing)
            .setContentIntent(openAppPendingIntent)
            .setOnlyAlertOnce(true)

        if (subText != null) {
            builder.setSubText(subText)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\n$subText"))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        }

        if (isOngoing) {
            if (isIndeterminate) {
                builder.setProgress(100, 0, true)
            } else {
                builder.setProgress(100, progressPercent, false)
            }

            if (isPaused) {
                // Add Resume and Stop actions
                val resumeIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_RESUME_IMPORT
                }
                val resumePending = PendingIntent.getBroadcast(
                    this,
                    201,
                    resumeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePending)

                val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_STOP_IMPORT
                }
                val stopPending = PendingIntent.getBroadcast(
                    this,
                    202,
                    stopIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            } else {
                // Add Pause and Stop actions
                val pauseIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_PAUSE_IMPORT
                }
                val pausePending = PendingIntent.getBroadcast(
                    this,
                    203,
                    pauseIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePending)

                val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_STOP_IMPORT
                }
                val stopPending = PendingIntent.getBroadcast(
                    this,
                    204,
                    stopIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Password File Import & Indexing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and controls for background password file processing"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatEta(seconds: Long): String {
        return if (seconds < 60) {
            "ETA: ${seconds}s"
        } else {
            val min = seconds / 60
            val sec = seconds % 60
            "ETA: ${min}m ${sec}s"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

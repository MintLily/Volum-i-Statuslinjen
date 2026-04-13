package dev.mintylabs.volumistatuslinjen.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.graphics.createBitmap
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class VolumeService : Service() {

    companion object {
        var isRunning = false
            private set
    }

    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager

    private val channelId = "Volumsporingskanal"
    private val notificationId = 1

    // Listens for volume changes broadcasted by the Android OS
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                val type = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                // We only care about media volume (STREAM_MUSIC)
                if (type == AudioManager.STREAM_MUSIC) {
                    updateNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()

        // Start the service in the foreground immediately
        startForeground(notificationId, buildNotification())

        // Register the volume listener
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keeps the service running until explicitly stopped
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        unregisterReceiver(volumeReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need bound components for this
    }

    // --- Core Logic ---

    private fun getCurrentVolumePercentage(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100) / max else 0
    }

    // Dynamically generates a text icon as an alpha mask
    private fun createVolumeIcon(volume: Int): Icon {
        val size = 128 // Canvas size
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE // Must be solid white for the OS alpha-mask tinting to work
            textSize = 90f      // Adjust text size to fit the canvas
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val text = volume.toString()

        // Center the text vertically
        val textHeight = paint.descent() - paint.ascent()
        val textOffset = (textHeight / 2) - paint.descent()

        // Draw the text onto the canvas
        canvas.drawText(text, size / 2f, (size / 2f) + textOffset, paint)

        return Icon.createWithBitmap(bitmap)
    }

    private fun buildNotification(): Notification {
        val volume = getCurrentVolumePercentage()
        val icon = createVolumeIcon(volume)

        return Notification.Builder(this, channelId)
            .setSmallIcon(icon)
            .setContentTitle("Medievolum")
            .setContentText("Nivå: $volume%")
            .setOngoing(false) // Can be swiped away
            .build()
    }

    private fun updateNotification() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasPermission) {
            notificationManager.notify(notificationId, buildNotification())
        }
    }

    private fun createNotificationChannel() {
        // Notification Channels are mandatory for API 26+
        val channel = NotificationChannel(
            channelId,
            "Volumsporing",
            NotificationManager.IMPORTANCE_LOW // Low priority ensures no sound/pop-up, just the icon
        )
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }
}
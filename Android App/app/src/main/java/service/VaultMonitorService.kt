package com.vault.commandcenter.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vault.commandcenter.ui.DashboardActivity

class VaultMonitorService : Service() {

    companion object {
        const val PERSISTENT_ID = 1001
        const val ALERT_ID      = 1002
        const val CH_PERSISTENT = "vault_persistent"
        const val CH_ALERTS     = "vault_alerts"
        const val CH_WARNINGS   = "vault_warnings"
    }

    private lateinit var statusRef: DatabaseReference
    private lateinit var statusListener: ValueEventListener

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForegroundNow()
        listenToStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (::statusRef.isInitialized) statusRef.removeEventListener(statusListener)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(CH_PERSISTENT, "Vault Monitor",
                NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )

        val sirenUri = android.net.Uri.parse("android.resource://$packageName/${com.vault.commandcenter.R.raw.siren}")
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Vault Alerts",
                NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 800)
                setSound(sirenUri, audioAttr)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(CH_WARNINGS, "Vault Warnings",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun startForegroundNow() {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CH_PERSISTENT)
            .setContentTitle("Vault Monitor: Active")
            .setContentText("Watching vault status in real-time")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PERSISTENT_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(PERSISTENT_ID, notif)
        }
    }

    private fun listenToStatus() {
        statusRef = Firebase.database.reference.child("Vault").child("Status")
        statusListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val v = snap.getValue(String::class.java) ?: ""
                when {
                    v.startsWith("CRITICAL") -> fireAlert("⚠ VAULT CRITICAL",
                        "CRITICAL: Vault security compromised!", true)
                    v.startsWith("BREACH")   -> fireAlert("🚨 BREACH DETECTED",
                        "BREACH: Intruder inside!", true)
                    v.startsWith("WARNING")  -> fireAlert("⚠️ VAULT WARNING",
                        "WARNING: Potential security risk detected.", false)
                    else -> NotificationManagerCompat.from(
                        this@VaultMonitorService).cancel(ALERT_ID)
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        statusRef.addValueEventListener(statusListener)
    }

    private fun fireAlert(title: String, body: String, useSiren: Boolean) {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (useSiren) CH_ALERTS else CH_WARNINGS
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setPriority(if (useSiren) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (useSiren) Notification.CATEGORY_ALARM else Notification.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        if (useSiren) {
            builder.setAutoCancel(false)
            val sirenUri = android.net.Uri.parse("android.resource://$packageName/${com.vault.commandcenter.R.raw.siren}")
            builder.setVibrate(longArrayOf(0, 400, 200, 400, 200, 800))
                .setSound(sirenUri)
                .setFullScreenIntent(pi, true)
        }

        val notif = builder.build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(ALERT_ID, notif)
        }
    }
}
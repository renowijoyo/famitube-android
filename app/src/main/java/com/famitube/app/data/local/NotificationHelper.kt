package com.famitube.app.data.local

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.famitube.app.MainActivity
import com.famitube.app.data.model.NotificationItemDto

object NotificationHelper {
    private const val CHANNEL_ID = "famitube_alerts"
    private const val CHANNEL_NAME = "FamiTube Alerts"
    private const val PREFS_NAME = "famitube_notif_prefs"
    private const val KEY_SHOWN_IDS = "shown_notification_ids"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Requests, approvals, and safety alerts from FamiTube"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showNewNotifications(context: Context, items: List<NotificationItemDto>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shown = (prefs.getStringSet(KEY_SHOWN_IDS, emptySet()) ?: emptySet()).toMutableSet()

        val unreadNew = items.filter { !it.isRead && !shown.contains(it.id) }.take(5)
        if (unreadNew.isEmpty()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nm = NotificationManagerCompat.from(context)
        unreadNew.forEach { item ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(item.title)
                .setContentText(item.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(item.id.hashCode(), notification)
            shown.add(item.id)
        }

        // Keep set bounded to latest 150 IDs
        val bounded = if (shown.size > 150) shown.toList().takeLast(150).toSet() else shown
        prefs.edit().putStringSet(KEY_SHOWN_IDS, bounded).apply()
    }
}

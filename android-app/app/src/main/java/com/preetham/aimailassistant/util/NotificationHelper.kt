package com.preetham.aimailassistant.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.preetham.aimailassistant.MainActivity
import com.preetham.aimailassistant.R
import com.preetham.aimailassistant.data.BriefingData

object NotificationHelper {
    const val CHANNEL_ID = "briefing_channel"
    const val CHANNEL_NAME = "BriefingOS Alerts"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val descriptionText = "Notifications for BriefingOS daily briefings and pending action items"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showBriefingNotification(
        context: Context,
        title: String,
        content: String,
        actionCount: Int = 0
    ) {
        // Ensure channel exists
        createNotificationChannel(context)

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (actionCount > 0) {
            builder.setSubText("$actionCount action items")
            builder.setNumber(actionCount)
        }

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // In case permission check passed but system still threw SecurityException
            e.printStackTrace()
        }
    }

    fun triggerBriefingNotification(
        context: Context,
        data: BriefingData,
        completedActionIds: Set<String> = emptySet()
    ) {
        val pendingActions = data.morning_briefing.actions.count { !completedActionIds.contains(it.id) }

        // Find critical security/urgent item if any
        val securityAlert = (data.morning_briefing.important + data.morning_briefing.needs_attention)
            .firstOrNull { item ->
                item.subject.contains("security", ignoreCase = true) ||
                item.subject.contains("alert", ignoreCase = true) ||
                item.subject.contains("verification", ignoreCase = true) ||
                item.subject.contains("2fa", ignoreCase = true) ||
                item.summary.contains("security", ignoreCase = true) ||
                item.summary.contains("suspicious", ignoreCase = true) ||
                item.summary.contains("critical", ignoreCase = true)
            }

        val isEvening = data.briefing_type.equals("evening", ignoreCase = true)
        val defaultTitle = if (isEvening) {
            "BriefingOS: Evening Wrap-Up Ready"
        } else {
            "BriefingOS: Morning Pulse Ready"
        }

        val title = if (securityAlert != null) {
            "BriefingOS Alert: Security Notice"
        } else {
            defaultTitle
        }

        val slotPrefix = if (isEvening) "Evening Wrap-Up" else "Morning Briefing"
        val content = if (securityAlert != null) {
            "${securityAlert.subject} - ${securityAlert.summary}"
        } else if (pendingActions > 0) {
            "$slotPrefix: $pendingActions action items need attention"
        } else if (data.morning_briefing.informational.isNotEmpty()) {
            val topicCount = data.morning_briefing.informational.size
            "$slotPrefix: $topicCount digest topics organized"
        } else {
            "$slotPrefix synced. No urgent action items."
        }

        showBriefingNotification(
            context = context,
            title = title,
            content = content,
            actionCount = pendingActions
        )
    }
}

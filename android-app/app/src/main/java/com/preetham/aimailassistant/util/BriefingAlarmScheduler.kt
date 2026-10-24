package com.preetham.aimailassistant.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.preetham.aimailassistant.MainActivity
import java.util.Calendar

object BriefingAlarmScheduler {
    private const val TAG = "BriefingScheduler"
    private const val REQUEST_CODE_ALARM = 4200
    const val ACTION_SYNC_BRIEFING = "com.preetham.aimailassistant.ACTION_SYNC_BRIEFING"

    fun getNextBriefingTimeShort(): String {
        val now = System.currentTimeMillis()

        val morningCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val eveningCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 40)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when {
            now < morningCal.timeInMillis -> "08:00 AM IST"
            now < eveningCal.timeInMillis -> "18:40 IST"
            else -> "08:00 AM IST"
        }
    }

    fun getNextBriefingTime(): Pair<Long, String> {
        val now = System.currentTimeMillis()

        // 1. Morning Target: 08:00 AM
        val morningCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 2. Evening Target: 06:40 PM (18:40) - synchronized with 18:35 cloud job
        val eveningCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 40)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when {
            now < morningCal.timeInMillis -> {
                morningCal.timeInMillis to "Morning Briefing (08:00 AM IST)"
            }
            now < eveningCal.timeInMillis -> {
                eveningCal.timeInMillis to "Evening Wrap-Up (06:40 PM IST)"
            }
            else -> {
                // Next day morning
                morningCal.add(Calendar.DAY_OF_YEAR, 1)
                morningCal.timeInMillis to "Tomorrow Morning (08:00 AM IST)"
            }
        }
    }

    fun scheduleNextBriefing(context: Context) {
        val (nextTriggerMs, description) = getNextBriefingTime()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, BriefingAlarmReceiver::class.java).apply {
            action = ACTION_SYNC_BRIEFING
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(nextTriggerMs, showIntent),
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMs,
                    pendingIntent
                )
            }
            Log.d(TAG, "Successfully scheduled next background sync for $description [timestamp: $nextTriggerMs]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm: ${e.message}. Using inexact fallback.")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                nextTriggerMs,
                pendingIntent
            )
        }
    }

    /**
     * If GitHub Actions has not finished running yet (runner queue delay),
     * schedule an exact retry in [delayMinutes] without skipping the cycle.
     */
    fun scheduleRetry(context: Context, delayMinutes: Int = 5, retryCount: Int = 1) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)

        val intent = Intent(context, BriefingAlarmReceiver::class.java).apply {
            action = ACTION_SYNC_BRIEFING
            putExtra("retry_count", retryCount)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                pendingIntent
            )
            Log.d(TAG, "⏰ Scheduled cloud retry #$retryCount in $delayMinutes min (at timestamp $triggerAtMillis)")
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }
}

package com.preetham.aimailassistant.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.preetham.aimailassistant.data.BriefingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BriefingAlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BriefingAlarmReceiver"
        private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 1000L // 3 minutes safety timeout
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val retryCount = intent?.getIntExtra("retry_count", 0) ?: 0
        Log.d(TAG, "⏰ Automated Briefing Alarm received [attempt=$retryCount]! Acquiring Samsung WakeLock...")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BriefingOS:SyncWakeLock"
        )?.apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            var shouldScheduleNextMainBriefing = true
            try {
                val repository = BriefingRepository(context)
                val result = repository.syncOnDevice(mode = "auto", query = "auto")

                if (result.isSuccess) {
                    val incomingData = result.getOrThrow()
                    val completed = repository.getCompletedActionIds()
                    Log.d(TAG, "✅ Fresh automated briefing received (${incomingData.generated_at})! Firing lock-screen notification...")
                    NotificationHelper.triggerBriefingNotification(context, incomingData, completed)
                    shouldScheduleNextMainBriefing = true
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.w(TAG, "⚠️ Automated sync failed: $err")
                    if (retryCount < 3) {
                        Log.i(TAG, "Scheduling retry in 5 minutes (attempt ${retryCount + 1}/3)...")
                        BriefingAlarmScheduler.scheduleRetry(context, delayMinutes = 5, retryCount = retryCount + 1)
                        shouldScheduleNextMainBriefing = false
                    } else {
                        Log.w(TAG, "Max retries reached. Scheduling next regular briefing cycle.")
                        shouldScheduleNextMainBriefing = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during automated background sync execution", e)
                if (retryCount < 3) {
                    BriefingAlarmScheduler.scheduleRetry(context, delayMinutes = 5, retryCount = retryCount + 1)
                    shouldScheduleNextMainBriefing = false
                }
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing WakeLock: ${e.message}")
                }

                if (shouldScheduleNextMainBriefing) {
                    BriefingAlarmScheduler.scheduleNextBriefing(context)
                }
                pendingResult.finish()
            }
        }
    }
}

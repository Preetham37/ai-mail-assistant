package com.preetham.aimailassistant.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.preetham.aimailassistant.data.BriefingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BriefingAlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BriefingAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val retryCount = intent?.getIntExtra("retry_count", 0) ?: 0
        Log.d(TAG, "⏰ Briefing Alarm received [attempt=$retryCount]! Waking up for automated background sync...")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            var shouldScheduleNextMainBriefing = true
            try {
                val repository = BriefingRepository(context)
                val result = if (repository.getConnectedAccounts().any { it.is_active }) {
                    repository.syncOnDevice().recoverCatching { onDeviceErr ->
                        Log.w(TAG, "On-device sync failed: ${onDeviceErr.message}. Trying remote fallback...", onDeviceErr)
                        repository.refreshFromServer().getOrThrow()
                    }
                } else {
                    repository.refreshFromServer()
                }

                if (result.isSuccess) {
                    val incomingData = result.getOrThrow()
                    val isFresh = repository.isDataFreshForSlot(incomingData)

                    if (isFresh) {
                        // Data from GitHub is genuinely fresh!
                        repository.setLastIngestedGeneratedAt(incomingData.generated_at)
                        val completed = repository.getCompletedActionIds()
                        Log.d(TAG, "✅ Fresh briefing received (${incomingData.generated_at})! Firing notification to lock screen...")
                        NotificationHelper.triggerBriefingNotification(context, incomingData, completed)
                        shouldScheduleNextMainBriefing = true
                    } else {
                        // Data on GitHub has not been updated yet by GitHub Actions (e.g. runner queue delay)
                        if (retryCount < 4) {
                            Log.w(TAG, "⚠️ GitHub data not updated yet (stale timestamp: ${incomingData.generated_at}). Retrying in 5 minutes (attempt ${retryCount + 1}/4)...")
                            BriefingAlarmScheduler.scheduleRetry(context, delayMinutes = 5, retryCount = retryCount + 1)
                            shouldScheduleNextMainBriefing = false
                        } else {
                            Log.w(TAG, "⚠️ Max retries reached (attempt 4/4). GitHub Actions likely delayed or skipped. Scheduling next main cycle.")
                            shouldScheduleNextMainBriefing = true
                        }
                    }
                } else {
                    Log.w(TAG, "Background sync failed: ${result.exceptionOrNull()?.message}")
                    if (retryCount < 4) {
                        BriefingAlarmScheduler.scheduleRetry(context, delayMinutes = 5, retryCount = retryCount + 1)
                        shouldScheduleNextMainBriefing = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during background sync execution", e)
            } finally {
                if (shouldScheduleNextMainBriefing) {
                    BriefingAlarmScheduler.scheduleNextBriefing(context)
                }
                pendingResult.finish()
            }
        }
    }
}

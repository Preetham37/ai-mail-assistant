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
        Log.d(TAG, "⏰ Briefing Alarm received! Waking up for automated background sync...")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = BriefingRepository(context)
                val result = repository.refreshFromServer()

                if (result.isSuccess) {
                    val freshData = result.getOrThrow()
                    val completed = repository.getCompletedActionIds()
                    Log.d(TAG, "✅ Background sync successful! Firing notification to lock screen...")
                    NotificationHelper.triggerBriefingNotification(context, freshData, completed)
                } else {
                    Log.w(TAG, "Background sync did not fetch new data: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during background sync execution", e)
            } finally {
                // Automatically schedule the next briefing time
                BriefingAlarmScheduler.scheduleNextBriefing(context)
                pendingResult.finish()
            }
        }
    }
}

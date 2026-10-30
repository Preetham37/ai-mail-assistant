package com.preetham.aimailassistant.data.gmail

import java.util.Calendar

/**
 * Calculates incremental, intelligent time-window search queries for Gmail API.
 * Ensures the engine fetches only new/unseen messages since the last briefing cycle,
 * avoiding re-summarizing entire weeks of old communications.
 */
object SyncWindowPolicy {

    const val MAX_FALLBACK_QUERY = "newer_than:24h"
    const val MORNING_SLOT_QUERY = "newer_than:14h" // Covers overnight from 18:00 to 08:00
    const val EVENING_SLOT_QUERY = "newer_than:10h" // Covers workday from 08:00 to 18:00
    private const val BUFFER_SECONDS = 300L         // 5-minute safety overlap

    /**
     * Computes the Gmail query string based on the last successful sync timestamp.
     *
     * @param lastSyncEpochSeconds The epoch timestamp (in seconds) of the previous successful sync.
     * @param currentTimeMillis The current system time in milliseconds (defaults to now).
     * @return Gmail search query string (e.g., "after:1730280000" or "newer_than:14h").
     */
    fun calculateQuery(
        lastSyncEpochSeconds: Long?,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): String {
        val nowSec = currentTimeMillis / 1000L

        // If we synced within the last 36 hours, strictly query for messages received AFTER the last sync
        if (lastSyncEpochSeconds != null && lastSyncEpochSeconds > 0) {
            val elapsedSec = nowSec - lastSyncEpochSeconds
            // If elapsed time is between 5 minutes and 36 hours, use the exact epoch after: filter
            if (elapsedSec in 300..(36 * 3600)) {
                val boundary = (lastSyncEpochSeconds - BUFFER_SECONDS).coerceAtLeast(0L)
                return "after:$boundary"
            }
        }

        // If no recent sync exists or the gap is large, fallback to slot-based relative windows
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
        }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return if (hour < 14) {
            MORNING_SLOT_QUERY
        } else {
            EVENING_SLOT_QUERY
        }
    }

    /**
     * Resolves the briefing mode ("morning" or "evening") for synthesis.
     */
    fun resolveBriefingMode(currentTimeMillis: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeMillis
        }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return if (hour < 14) "morning" else "evening"
    }
}

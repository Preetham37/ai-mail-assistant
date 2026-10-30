package com.preetham.aimailassistant.data.gmail

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class SyncWindowPolicyTest {

    @Test
    fun `test morning slot query when no previous sync exists`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }
        val query = SyncWindowPolicy.calculateQuery(null, cal.timeInMillis)
        assertEquals("newer_than:14h", query)
        assertEquals("morning", SyncWindowPolicy.resolveBriefingMode(cal.timeInMillis))
    }

    @Test
    fun `test evening slot query when no previous sync exists`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 30)
        }
        val query = SyncWindowPolicy.calculateQuery(null, cal.timeInMillis)
        assertEquals("newer_than:10h", query)
        assertEquals("evening", SyncWindowPolicy.resolveBriefingMode(cal.timeInMillis))
    }

    @Test
    fun `test incremental sync uses after timestamp with 5-minute safety buffer`() {
        val nowMs = 1730290000_000L
        val nowSec = nowMs / 1000L
        val lastSyncSec = nowSec - 7200L // synced 2 hours ago

        val query = SyncWindowPolicy.calculateQuery(lastSyncSec, nowMs)
        val expectedBoundary = lastSyncSec - 300L
        assertEquals("after:$expectedBoundary", query)
    }

    @Test
    fun `test large gap falls back to slot window without repeating whole week`() {
        val nowMs = 1730290000_000L
        val nowSec = nowMs / 1000L
        val lastSyncSec = nowSec - (50 * 3600L) // synced 50 hours ago (device off)

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 8)
        }
        val query = SyncWindowPolicy.calculateQuery(lastSyncSec, cal.timeInMillis)
        assertEquals("newer_than:14h", query)
    }

    @Test
    fun `test manual refresh query is strictly 24 hours`() {
        assertEquals("newer_than:24h", SyncWindowPolicy.MANUAL_REFRESH_QUERY)
    }
}

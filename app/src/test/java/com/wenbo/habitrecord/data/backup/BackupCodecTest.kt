package com.wenbo.habitrecord.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val backup = BackupV1(
        exportedAt = 1762400000000L,
        habits = listOf(
            HabitBackup(id = 1, name = "阅读", emoji = "📚", frequencyType = "WEEKDAYS", weekdays = "1,3,5"),
            HabitBackup(id = 2, name = "跑步", frequencyType = "TIMES_PER_WEEK", timesPerWeek = 3),
        ),
        checkIns = listOf(CheckInBackup(id = 1, habitId = 1, date = 20000, status = 1)),
        ledger = listOf(LedgerBackup(id = 1, amount = 10, reason = "CHECKIN", habitId = 1, date = 20000)),
        rewards = listOf(RewardBackup(id = 1, name = "电影", pointsCost = 50)),
    )

    @Test
    fun `encode then decode round trip`() {
        val json = BackupCodec.encode(backup)
        val parsed = BackupCodec.decode(json)
        assertNotNull(parsed)
        parsed!!
        assertEquals(backup, parsed)
        assertEquals("1,3,5", parsed.habits[0].weekdays)
        assertEquals(50, parsed.rewards[0].pointsCost)
    }

    @Test
    fun `decode invalid json returns null`() {
        assertNull(BackupCodec.decode("not a json {"))
        assertNull(BackupCodec.decode("{\"foo\": 1}"))
    }

    @Test
    fun `decode newer version returns null`() {
        val json = BackupCodec.encode(backup).replace("\"version\": 1", "\"version\": 99")
        assertNull(BackupCodec.decode(json))
    }

    @Test
    fun `decode tolerates unknown fields`() {
        val json = BackupCodec.encode(backup)
            .replace("{\n  \"version\"", "{\n  \"futureField\": true,\n  \"version\"")
        assertTrue(BackupCodec.decode(json) != null)
    }
}

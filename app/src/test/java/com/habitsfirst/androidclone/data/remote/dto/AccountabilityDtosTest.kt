package com.habitsfirst.androidclone.data.remote.dto

import com.habitsfirst.androidclone.domain.model.BuddyConnectionStatus
import com.habitsfirst.androidclone.domain.model.DailySummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JSON (de)serialization tests for the accountability-buddy backend's DTOs -- no
 * networking involved, matching [com.habitsfirst.androidclone.data.billing.StubEntitlementRepositoryTest]'s
 * plain-JUnit style.
 */
class AccountabilityDtosTest {

    @Test
    fun `DailySummary round trips through JSON`() {
        val summary = DailySummary(date = "2026-09-05", habitsCompleted = 3, totalHabits = 5, currentStreak = 12)

        val parsed = summary.toJson().toDailySummary()

        assertEquals(summary, parsed)
    }

    @Test
    fun `toPairingCode reads the code field`() {
        val json = JSONObject().put("code", "ABC123")

        assertEquals("ABC123", json.toPairingCode().code)
    }

    @Test
    fun `toAccountabilityBuddy maps connected status`() {
        val json = JSONObject()
            .put("id", "buddy-1")
            .put("displayName", "Alex")
            .put("pairingCode", "XYZ")
            .put("status", "connected")

        val buddy = json.toAccountabilityBuddy()

        assertEquals("buddy-1", buddy.id)
        assertEquals("Alex", buddy.displayName)
        assertEquals(BuddyConnectionStatus.Connected, buddy.status)
        assertNull(buddy.lastSummary)
    }

    @Test
    fun `toAccountabilityBuddy maps error status with its message`() {
        val json = JSONObject()
            .put("id", "buddy-2")
            .put("displayName", "Sam")
            .put("status", "error")
            .put("statusMessage", "Timed out")

        val status = json.toAccountabilityBuddy().status

        assertTrue(status is BuddyConnectionStatus.Error)
        assertEquals("Timed out", (status as BuddyConnectionStatus.Error).message)
    }

    @Test
    fun `toAccountabilityBuddy defaults to pending for an unrecognized or missing status`() {
        val json = JSONObject().put("id", "buddy-3").put("displayName", "Jo")

        assertEquals(BuddyConnectionStatus.Pending, json.toAccountabilityBuddy().status)
    }

    @Test
    fun `toAccountabilityBuddy parses a nested lastSummary`() {
        val summaryJson = JSONObject()
            .put("date", "2026-09-04")
            .put("habitsCompleted", 2)
            .put("totalHabits", 4)
            .put("currentStreak", 7)
        val json = JSONObject()
            .put("id", "buddy-4")
            .put("displayName", "Riley")
            .put("status", "connected")
            .put("lastSummary", summaryJson)

        val lastSummary = json.toAccountabilityBuddy().lastSummary

        assertEquals(DailySummary("2026-09-04", 2, 4, 7), lastSummary)
    }

    @Test
    fun `toAccountabilityBuddy falls back to a default display name when blank`() {
        val json = JSONObject().put("id", "buddy-5").put("displayName", "")

        assertEquals("Buddy", json.toAccountabilityBuddy().displayName)
    }
}

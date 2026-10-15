package com.stepwriter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StepSessionPlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val date = LocalDate.of(2026, 10, 15)

    @Test
    fun plannedSessionsSumExactlyToRequestedCount() {
        val result = StepSessionPlanner.plan(
            count = 10_000L,
            date = date,
            zone = zone,
            now = Instant.parse("2026-10-15T20:30:00Z")
        )
        assertEquals(10_000L, result.sumOf { it.count })
    }

    @Test
    fun generatedSessionsHavePositiveDurationAndAreNotFutureDated() {
        val now = Instant.parse("2026-10-15T20:30:00Z")
        val result = StepSessionPlanner.plan(1_000L, date, zone, now)
        assertTrue(result.all { it.startTime < it.endTime })
        assertTrue(result.all { it.endTime <= now })
    }

    @Test
    fun countIsRedistributedWhenFutureSlotsAreExcluded() {
        val now = Instant.parse("2026-10-15T06:00:00Z")
        val result = StepSessionPlanner.plan(999L, date, zone, now)
        assertEquals(999L, result.sumOf { it.count })
        assertTrue(result.all { it.count > 0L })
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCountIsRejected() {
        StepSessionPlanner.plan(0L, date, zone, Instant.parse("2026-10-15T20:30:00Z"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun failsClearlyWhenNoSessionIsEligible() {
        StepSessionPlanner.plan(
            10L,
            date.minusDays(1),
            zone,
            Instant.parse("2026-10-15T20:30:00Z")
        )
    }
}

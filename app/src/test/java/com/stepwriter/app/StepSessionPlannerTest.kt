package com.stepwriter.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StepInputValidatorTest {
    @Test
    fun acceptsCountsInsideAllowedRange() {
        assertTrue(StepInputValidator.isValidCount(1L))
        assertTrue(StepInputValidator.isValidCount(10_000L))
        assertTrue(StepInputValidator.isValidCount(StepInputValidator.MAX_STEPS_PER_WRITE))
    }

    @Test
    fun rejectsNullNonPositiveAndExcessiveCounts() {
        assertFalse(StepInputValidator.isValidCount(null))
        assertFalse(StepInputValidator.isValidCount(0L))
        assertFalse(StepInputValidator.isValidCount(-1L))
        assertFalse(StepInputValidator.isValidCount(StepInputValidator.MAX_STEPS_PER_WRITE + 1))
    }

    @Test
    fun acceptsOnlyPositiveTimeIntervals() {
        val start = Instant.parse("2026-10-15T10:00:00Z")
        assertTrue(StepInputValidator.isValidInterval(start, start.plusSeconds(1)))
        assertFalse(StepInputValidator.isValidInterval(start, start))
        assertFalse(StepInputValidator.isValidInterval(start, start.minusSeconds(1)))
    }
}

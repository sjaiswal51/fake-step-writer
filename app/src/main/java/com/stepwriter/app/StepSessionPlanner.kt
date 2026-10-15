package com.stepwriter.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Produces deterministic, non-overlapping step-record time windows for test/import workflows.
 *
 * This code intentionally does not attempt to make generated data appear sensor-originated.
 * Callers should clearly identify synthetic/test data in their own workflows and UI.
 */
internal object StepSessionPlanner {
    data class PlannedSession(
        val count: Long,
        val startTime: Instant,
        val endTime: Instant,
        val zoneOffset: ZoneOffset
    )

    private data class Template(
        val hour: Int,
        val minute: Int,
        val durationMinutes: Int,
        val weight: Int
    )

    private val templates = listOf(
        Template(6, 30, 25, 10),
        Template(7, 15, 20, 8),
        Template(8, 0, 15, 5),
        Template(9, 30, 10, 4),
        Template(10, 45, 12, 5),
        Template(12, 0, 20, 8),
        Template(13, 30, 10, 4),
        Template(15, 0, 15, 6),
        Template(16, 30, 12, 5),
        Template(17, 30, 25, 12),
        Template(18, 30, 30, 15),
        Template(20, 0, 20, 10),
        Template(21, 0, 15, 8)
    )

    /**
     * Distributes count evenly across session slots on the selected local date.
     * Slots that cannot fit entirely before [now] are skipped; the remaining count is
     * re-distributed over eligible slots instead of being silently dropped or appended
     * to an existing record. Every returned interval is positive and ends at or before now.
     */
    fun plan(
        count: Long,
        date: LocalDate,
        zone: ZoneId,
        now: Instant,
        maxSessions: Int = templates.size
    ): List<PlannedSession> {
        require(count > 0) { "Step count must be positive." }
        require(maxSessions > 0) { "At least one session slot is required." }

        val offset = zone.rules.getOffset(now)
        val eligible = templates.take(maxSessions).mapNotNull { template ->
            val start = date.atTime(template.hour, template.minute).atZone(zone).toInstant()
            val intendedEnd = start.plusSeconds(template.durationMinutes * 60L)
            val end = if (intendedEnd <= now) intendedEnd else now
            if (start < now && end > start) {
                Triple(start, end, template.weight)
            } else {
                null
            }
        }

        require(eligible.isNotEmpty()) {
            "No valid session windows remain for the selected date and time."
        }

        val totalWeight = eligible.sumOf { it.third }.toLong()
        var allocated = 0L
        return eligible.mapIndexed { index, (start, end, weight) ->
            val sessionCount = if (index == eligible.lastIndex) {
                count - allocated
            } else {
                ((count / totalWeight) * weight) +
                    (((count % totalWeight) * weight) / totalWeight)
            }
            allocated += sessionCount
            require(sessionCount > 0) { "Generated session count must be positive." }
            require(end > start && end <= now) { "Invalid session interval." }
            PlannedSession(sessionCount, start, end, zone.rules.getOffset(start))
        }.also { sessions ->
            check(sessions.sumOf { it.count } == count) {
                "Planned session total does not match requested count."
            }
        }
    }
}

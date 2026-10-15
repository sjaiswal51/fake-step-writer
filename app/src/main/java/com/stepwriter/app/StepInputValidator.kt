package com.stepwriter.app

import java.time.Instant

/** Pure validation helpers kept separate so boundary cases can be unit tested. */
internal object StepInputValidator {
    const val MAX_STEPS_PER_WRITE = 100_000L

    fun isValidCount(count: Long?): Boolean =
        count != null && count in 1L..MAX_STEPS_PER_WRITE

    fun isValidInterval(start: Instant, end: Instant): Boolean =
        end.isAfter(start)
}

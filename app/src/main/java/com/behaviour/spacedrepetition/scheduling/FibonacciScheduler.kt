package com.behaviour.spacedrepetition.scheduling

import com.behaviour.spacedrepetition.data.local.entity.Revision

/**
 * Generates Fibonacci-spaced revision schedules.
 *
 * Schedule from the moment a note is created:
 *   Revision 0: +5 hours    (same day quick review)
 *   Revision 1: +2 days     (fib: 1+1)
 *   Revision 2: +3 days     (fib: 1+2)
 *   Revision 3: +5 days     (fib: 2+3)
 *   Revision 4: +8 days     (fib: 3+5)
 *   Revision 5: +13 days    (fib: 5+8)
 *   Revision 6: +21 days    (fib: 8+13)
 *   Revision 7: +34 days    (fib: 13+21)
 */
object FibonacciScheduler {

    private const val FIVE_HOURS_MS = 5L * 60 * 60 * 1000
    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

    // Fibonacci day offsets starting from revision index 1
    // Index 0 is special (5 hours)
    private val FIBONACCI_DAYS = listOf(0, 2, 3, 5, 8, 13, 21, 34, 55, 89)

    /**
     * Calculate the scheduled time for a specific revision index.
     */
    fun getRevisionTime(createdAt: Long, revisionIndex: Int): Long {
        return if (revisionIndex == 0) {
            createdAt + FIVE_HOURS_MS
        } else {
            val dayOffset = if (revisionIndex < FIBONACCI_DAYS.size) {
                FIBONACCI_DAYS[revisionIndex].toLong()
            } else {
                (revisionIndex - 8).toLong() * 89L
            }
            createdAt + (dayOffset * ONE_DAY_MS)
        }
    }

    /**
     * Generate all revision records for a newly created note.
     */
    fun generateAllRevisions(
        noteId: Long,
        createdAt: Long,
        count: Int = 400
    ): List<Revision> {
        return (0 until count).map { index ->
            Revision(
                noteId = noteId,
                revisionIndex = index,
                scheduledAt = getRevisionTime(createdAt, index)
            )
        }
    }

    /**
     * Get the spaced repetition interval in milliseconds for a specific revision index relative to its predecessor.
     */
    fun getRevisionIntervalMs(revisionIndex: Int): Long {
        return when {
            revisionIndex == 0 -> FIVE_HOURS_MS
            revisionIndex == 1 -> (2L * ONE_DAY_MS) - FIVE_HOURS_MS
            revisionIndex < FIBONACCI_DAYS.size -> {
                val days = FIBONACCI_DAYS[revisionIndex] - FIBONACCI_DAYS[revisionIndex - 1]
                days.toLong() * ONE_DAY_MS
            }
            else -> {
                89L * ONE_DAY_MS
            }
        }
    }
}

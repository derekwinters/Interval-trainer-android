package com.derekwinters.intervaltrainer

/**
 * The kinds of interval a preset can hold (`CONTEXT.md` glossary; `TIMER-001`). There are exactly
 * these four in v1.
 */
enum class IntervalKind {
    WARM_UP,
    WORK,
    RECOVERY,
    COOL_DOWN,
}

/**
 * One timed segment of a preset or a schedule: a [kind] and a [durationSeconds] (`TIMER-001`).
 *
 * This type carries nothing else — no round count, no generated group, no reference to whatever
 * produced it (`TIMER-004`) — because a preset stores rows and nothing else. Enforcing the
 * five-second minimum an interval may be authored with (`TIMER-070`) is the editor's job
 * (`TIMER-072`), not this constructor's; nothing here validates [durationSeconds].
 */
data class Interval(
    val kind: IntervalKind,
    val durationSeconds: Int,
)

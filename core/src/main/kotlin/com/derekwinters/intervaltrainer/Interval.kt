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

/**
 * Cycles [IntervalKind] through a fixed order — warm-up, work, recovery, cool-down, then back to
 * warm-up (`docs/spec/screens.md` `SCREEN-014`) — the preset editor's own mechanism for setting a
 * row's kind: tapping a row's colour dot/name while it is open for editing advances it one step
 * along this cycle, rather than the editor needing a separate picker control for a four-value
 * enum. Four taps from any starting kind return to that same kind.
 */
fun IntervalKind.next(): IntervalKind = when (this) {
    IntervalKind.WARM_UP -> IntervalKind.WORK
    IntervalKind.WORK -> IntervalKind.RECOVERY
    IntervalKind.RECOVERY -> IntervalKind.COOL_DOWN
    IntervalKind.COOL_DOWN -> IntervalKind.WARM_UP
}

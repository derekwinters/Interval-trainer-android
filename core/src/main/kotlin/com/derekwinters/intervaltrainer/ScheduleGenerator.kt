package com.derekwinters.intervaltrainer

/**
 * The shortest duration, in seconds, that any interval reaching a schedule may have (`TIMER-070`).
 *
 * Nothing in `:core` enforces this against every entry point that can produce a row — the preset
 * editor is what refuses it at authoring time (`TIMER-072`, `docs/spec/schema.md` `SCHEMA-026`),
 * and that enforcement is the editor's own, not this module's. [generateRounds] is named
 * separately by the rule (`TIMER-073`) and enforces it on its own inputs below; this constant is
 * the one place both components should read the value from.
 */
const val MINIMUM_INTERVAL_DURATION_SECONDS = 5

/**
 * The round generator (`TIMER-080`–`085`): a pure function that produces a uniform block of
 * work/recovery rows from exactly four inputs, for the preset editor to append to the end of a
 * preset's interval list (`TIMER-083`).
 *
 * It takes no state beyond [roundCount], [workDurationSeconds], [recoveryDurationSeconds] and
 * [trailingRecovery] — no preset, no existing list, no id or clock — because nothing about a
 * generated row is remembered afterward (`TIMER-004`, `TIMER-085`). Appending its result to an
 * existing list, and nothing fancier, is what `TIMER-083`'s "always appends" and `TIMER-084`'s "no
 * replace mode" describe: there is no parameter here to ask for anything else.
 *
 * Every row returned is an ordinary [Interval] (`TIMER-085`) — the same two-field data class a
 * hand-authored row is, with nothing recording that it came from here or which round it belonged
 * to, per the invariant that nothing about the generator is stored.
 *
 * @param roundCount how many rounds to emit. Zero emits an empty list.
 * @param workDurationSeconds the duration of each round's work interval.
 * @param recoveryDurationSeconds the duration of each round's recovery interval.
 * @param trailingRecovery whether the last round's recovery is emitted. Defaults to `false`
 * (`TIMER-082`): a cool-down usually follows the rounds, and ending on a recovery right before one
 * stacks two low-effort intervals back to back.
 * @throws IllegalArgumentException if [workDurationSeconds] or [recoveryDurationSeconds] is below
 * [MINIMUM_INTERVAL_DURATION_SECONDS]. The generator is one of the components the minimum-interval
 * rule names by name (`TIMER-073`): it rejects an invalid duration rather than emitting a row that
 * violates the rule.
 */
fun generateRounds(
    roundCount: Int,
    workDurationSeconds: Int,
    recoveryDurationSeconds: Int,
    trailingRecovery: Boolean = false,
): List<Interval> {
    require(workDurationSeconds >= MINIMUM_INTERVAL_DURATION_SECONDS) {
        "workDurationSeconds must be at least $MINIMUM_INTERVAL_DURATION_SECONDS seconds, " +
            "was $workDurationSeconds"
    }
    require(recoveryDurationSeconds >= MINIMUM_INTERVAL_DURATION_SECONDS) {
        "recoveryDurationSeconds must be at least $MINIMUM_INTERVAL_DURATION_SECONDS seconds, " +
            "was $recoveryDurationSeconds"
    }

    return buildList {
        for (round in 0 until roundCount) {
            add(Interval(IntervalKind.WORK, workDurationSeconds))
            val isLastRound = round == roundCount - 1
            if (!isLastRound || trailingRecovery) {
                add(Interval(IntervalKind.RECOVERY, recoveryDurationSeconds))
            }
        }
    }
}

/**
 * The preset editor's own splice (`docs/spec/screens.md` `SCREEN-017`): [generateRounds]'s output
 * appended onto the end of [existing] — the editor's current, in-memory interval list — never
 * replacing or reordering what was already there (`TIMER-083`, `TIMER-084`).
 *
 * [existing] is read, not mutated: the returned list is a fresh one, so a caller holding its own
 * reference to [existing] (an editor's undo point, a snapshot for a test) never sees it change out
 * from under it.
 *
 * The rows appended are exactly what [generateRounds] itself returns for the same four
 * arguments — plain [Interval] values, with nothing marking where the split between "existing" and
 * "generated" falls once they are in the one list (`TIMER-085`). A caller that wants only the
 * newly generated rows — to give each one its own identity for a UI list, for instance — reads
 * `result.drop(existing.size)`.
 *
 * @throws IllegalArgumentException exactly as [generateRounds] does, for the same reason
 * (`TIMER-070`, `TIMER-073`): a below-minimum work or recovery duration is refused rather than
 * spliced in.
 */
fun appendGeneratedRounds(
    existing: List<Interval>,
    roundCount: Int,
    workDurationSeconds: Int,
    recoveryDurationSeconds: Int,
    trailingRecovery: Boolean = false,
): List<Interval> =
    existing + generateRounds(roundCount, workDurationSeconds, recoveryDurationSeconds, trailingRecovery)

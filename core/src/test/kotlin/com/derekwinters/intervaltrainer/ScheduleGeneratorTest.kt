package com.derekwinters.intervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JVM unit tests for [generateRounds], the round generator (`TIMER-080`–`085`), and for
 * [appendGeneratedRounds], the preset editor's own splice of the generator's output onto the end
 * of the list already open for editing (`docs/spec/screens.md` `SCREEN-017`, `TIMER-083`). They
 * need no device, no emulator and no simulated Android runtime, matching every other `:core` test
 * (ADR 0005).
 *
 * This file also covers `TIMER-070` and `TIMER-073` — the minimum-interval rule as it applies to
 * the generator specifically. `TIMER-072` ("the editor rejects a duration below the minimum") is
 * **not** covered here: it is the preset editor's own behaviour, gating its "Add" action rather
 * than calling this function with an invalid input (`docs/spec/screens.md` `SCREEN-017`,
 * `docs/spec/schema.md` `SCHEMA-026`, which names the editor's refusal, not the generator's, as
 * that requirement's test). `TIMER-071` (three countdown ticks) is `CueSelectionTest.kt`'s, per
 * the traceability table; it is about playback, not authoring, and the generator does not touch
 * it.
 */
class ScheduleGeneratorTest {

    /**
     * TIMER-081: a work interval followed by a recovery interval, for every round, except that the
     * last recovery is dropped when the trailing-recovery flag is off. Four rounds with the flag
     * off give seven rows, ending on work.
     */
    @Test
    fun `emits work then recovery per round, dropping the trailing recovery by default`() {
        val rows = generateRounds(
            roundCount = 4,
            workDurationSeconds = 30,
            recoveryDurationSeconds = 15,
            trailingRecovery = false,
        )

        assertEquals(
            listOf(
                Interval(IntervalKind.WORK, 30),
                Interval(IntervalKind.RECOVERY, 15),
                Interval(IntervalKind.WORK, 30),
                Interval(IntervalKind.RECOVERY, 15),
                Interval(IntervalKind.WORK, 30),
                Interval(IntervalKind.RECOVERY, 15),
                Interval(IntervalKind.WORK, 30),
            ),
            rows,
        )
    }

    /** TIMER-081: with the flag on, the last round's recovery is kept — four rounds give eight rows. */
    @Test
    fun `keeps the trailing recovery when the flag is set`() {
        val rows = generateRounds(
            roundCount = 4,
            workDurationSeconds = 30,
            recoveryDurationSeconds = 15,
            trailingRecovery = true,
        )

        assertEquals(8, rows.size)
        assertEquals(Interval(IntervalKind.RECOVERY, 15), rows.last())
    }

    /**
     * TIMER-082: trailing recovery defaults off. Calling the generator without naming the
     * parameter behaves exactly as passing `trailingRecovery = false` explicitly.
     */
    @Test
    fun `defaults trailing recovery to off`() {
        val withoutTheParameter = generateRounds(
            roundCount = 3,
            workDurationSeconds = 20,
            recoveryDurationSeconds = 10,
        )
        val withTheFlagOffExplicitly = generateRounds(
            roundCount = 3,
            workDurationSeconds = 20,
            recoveryDurationSeconds = 10,
            trailingRecovery = false,
        )

        assertEquals(withTheFlagOffExplicitly, withoutTheParameter)
        assertEquals(IntervalKind.WORK, withoutTheParameter.last().kind)
    }

    /** TIMER-080: a single round is one work interval, plus its recovery only when the flag is set. */
    @Test
    fun `a single round is one work interval, with or without its recovery`() {
        val roundOnly = generateRounds(
            roundCount = 1,
            workDurationSeconds = 45,
            recoveryDurationSeconds = 20,
            trailingRecovery = false,
        )
        val roundWithRecovery = generateRounds(
            roundCount = 1,
            workDurationSeconds = 45,
            recoveryDurationSeconds = 20,
            trailingRecovery = true,
        )

        assertEquals(listOf(Interval(IntervalKind.WORK, 45)), roundOnly)
        assertEquals(
            listOf(Interval(IntervalKind.WORK, 45), Interval(IntervalKind.RECOVERY, 20)),
            roundWithRecovery,
        )
    }

    /**
     * TIMER-085: a generated row is an ordinary [Interval] — the same data class a hand-authored
     * row is, with no extra field recording that it came from the generator or which round it
     * belonged to, and equal by value to a hand-built one with the same kind and duration.
     */
    @Test
    fun `generated rows are ordinary intervals, equal by value to hand-authored ones`() {
        val generated = generateRounds(
            roundCount = 1,
            workDurationSeconds = 45,
            recoveryDurationSeconds = 20,
            trailingRecovery = true,
        )

        val handAuthored = listOf(
            Interval(kind = IntervalKind.WORK, durationSeconds = 45),
            Interval(kind = IntervalKind.RECOVERY, durationSeconds = 20),
        )

        assertEquals(handAuthored, generated)
        // Nothing distinguishes a generated row once it exists: it is the same data class, with
        // the same two fields, so putting it through any other :core function (here, a schedule
        // copy) is indistinguishable from a hand-authored row going through the same function.
        val preset = Preset(id = "preset-1", name = "Generated", intervals = generated)
        assertEquals(handAuthored, scheduleFrom(preset))
    }

    /**
     * TIMER-083: the generator's own contract is to produce the block that gets appended — it
     * always lands after whatever the list already held, never replacing it (TIMER-084: there is
     * no replace mode to invoke instead).
     */
    @Test
    fun `the generated block appends after existing rows rather than replacing them`() {
        val warmUp = Interval(IntervalKind.WARM_UP, 180)
        val existing = listOf(warmUp)

        val appended = existing + generateRounds(
            roundCount = 2,
            workDurationSeconds = 30,
            recoveryDurationSeconds = 15,
        )

        assertEquals(
            listOf(
                warmUp,
                Interval(IntervalKind.WORK, 30),
                Interval(IntervalKind.RECOVERY, 15),
                Interval(IntervalKind.WORK, 30),
            ),
            appended,
        )
    }

    /**
     * Acceptance criterion: the function takes no state beyond its four inputs. Two calls with
     * identical arguments, from otherwise-independent invocations, produce equal results — nothing
     * hidden (a counter, a clock, a random id) leaks between them.
     */
    @Test
    fun `is pure, taking no state beyond its four inputs`() {
        val first = generateRounds(
            roundCount = 5,
            workDurationSeconds = 40,
            recoveryDurationSeconds = 20,
            trailingRecovery = true,
        )
        val second = generateRounds(
            roundCount = 5,
            workDurationSeconds = 40,
            recoveryDurationSeconds = 20,
            trailingRecovery = true,
        )

        assertEquals(first, second)
        assertNotSame(first, second)
    }

    /** TIMER-080: a round count of zero emits nothing — there is no round to fill in. */
    @Test
    fun `zero rounds emits an empty list`() {
        val rows = generateRounds(
            roundCount = 0,
            workDurationSeconds = 30,
            recoveryDurationSeconds = 15,
        )

        assertEquals(emptyList<Interval>(), rows)
    }

    /**
     * TIMER-070, TIMER-073: five seconds is the minimum, and the generator is one of the
     * components the rule binds by name — it must not emit a row shorter than that, so it rejects
     * a work duration below it rather than emitting an invalid row.
     */
    @Test
    fun `rejects a work duration below the five-second minimum`() {
        assertThrows(IllegalArgumentException::class.java) {
            generateRounds(roundCount = 1, workDurationSeconds = 4, recoveryDurationSeconds = 15)
        }
        assertThrows(IllegalArgumentException::class.java) {
            generateRounds(roundCount = 1, workDurationSeconds = 0, recoveryDurationSeconds = 15)
        }
    }

    /** TIMER-070, TIMER-073: the same rule applies to the recovery duration. */
    @Test
    fun `rejects a recovery duration below the five-second minimum`() {
        assertThrows(IllegalArgumentException::class.java) {
            generateRounds(roundCount = 1, workDurationSeconds = 30, recoveryDurationSeconds = 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            generateRounds(roundCount = 1, workDurationSeconds = 30, recoveryDurationSeconds = 0)
        }
    }

    /** TIMER-070: five seconds itself is accepted — the minimum is inclusive, not exclusive. */
    @Test
    fun `accepts exactly the five-second minimum for both durations`() {
        val rows = generateRounds(
            roundCount = 1,
            workDurationSeconds = 5,
            recoveryDurationSeconds = 5,
            trailingRecovery = true,
        )

        assertEquals(
            listOf(Interval(IntervalKind.WORK, 5), Interval(IntervalKind.RECOVERY, 5)),
            rows,
        )
    }

    // ---- appendGeneratedRounds (SCREEN-017) ------------------------------------------------

    /**
     * SCREEN-017, TIMER-083: the generator's rows land after whatever the editor's list already
     * held, in the same order [generateRounds] itself would produce them, never replacing or
     * reordering the existing rows.
     */
    @Test
    fun `splices the generated block onto the end of the existing list`() {
        val existing = listOf(Interval(IntervalKind.WARM_UP, 180))

        val spliced = appendGeneratedRounds(
            existing = existing,
            roundCount = 2,
            workDurationSeconds = 30,
            recoveryDurationSeconds = 15,
        )

        assertEquals(
            listOf(
                Interval(IntervalKind.WARM_UP, 180),
                Interval(IntervalKind.WORK, 30),
                Interval(IntervalKind.RECOVERY, 15),
                Interval(IntervalKind.WORK, 30),
            ),
            spliced,
        )
    }

    /** SCREEN-017: an empty editor list is a valid starting point — the block simply becomes the
     * whole list. */
    @Test
    fun `splicing onto an empty list yields just the generated block`() {
        val spliced = appendGeneratedRounds(
            existing = emptyList(),
            roundCount = 1,
            workDurationSeconds = 20,
            recoveryDurationSeconds = 10,
            trailingRecovery = true,
        )

        assertEquals(
            listOf(Interval(IntervalKind.WORK, 20), Interval(IntervalKind.RECOVERY, 10)),
            spliced,
        )
    }

    /** SCREEN-017: [existing] itself is never mutated or reordered — splicing reads it, and
     * returns a new list, rather than changing the caller's own list in place. */
    @Test
    fun `never mutates the existing list it is given`() {
        val existing = listOf(Interval(IntervalKind.WARM_UP, 180), Interval(IntervalKind.WORK, 45))

        val spliced = appendGeneratedRounds(
            existing = existing,
            roundCount = 1,
            workDurationSeconds = 30,
            recoveryDurationSeconds = 15,
            trailingRecovery = true,
        )

        assertEquals(listOf(Interval(IntervalKind.WARM_UP, 180), Interval(IntervalKind.WORK, 45)), existing)
        assertEquals(4, spliced.size)
    }

    /**
     * TIMER-085, SCREEN-017's own "indistinguishable from hand-authored" acceptance criterion:
     * the rows [appendGeneratedRounds] adds are plain [Interval] values, equal by value to a
     * hand-typed row with the same kind and duration — nothing about the tail it appended marks
     * where the split between "existing" and "generated" was.
     */
    @Test
    fun `the appended rows are ordinary intervals, indistinguishable from hand-authored ones`() {
        val handAuthored = listOf(Interval(IntervalKind.WARM_UP, 180))

        val spliced = appendGeneratedRounds(
            existing = handAuthored,
            roundCount = 1,
            workDurationSeconds = 30,
            recoveryDurationSeconds = 15,
            trailingRecovery = true,
        )
        val generatedTail = spliced.drop(handAuthored.size)

        assertEquals(
            listOf(Interval(IntervalKind.WORK, 30), Interval(IntervalKind.RECOVERY, 15)),
            generatedTail,
        )
        // The whole point: a fresh Preset built from the spliced list treats every row alike.
        val preset = Preset(id = "preset-1", name = "Spliced", intervals = spliced)
        assertEquals(spliced, scheduleFrom(preset))
    }

    /** TIMER-070, TIMER-073: an invalid duration is rejected here exactly as [generateRounds]
     * itself rejects it — nothing about going through the splice weakens the rule. */
    @Test
    fun `rejects a below-minimum duration exactly as generateRounds does`() {
        assertThrows(IllegalArgumentException::class.java) {
            appendGeneratedRounds(
                existing = emptyList(),
                roundCount = 1,
                workDurationSeconds = 4,
                recoveryDurationSeconds = 15,
            )
        }
    }
}

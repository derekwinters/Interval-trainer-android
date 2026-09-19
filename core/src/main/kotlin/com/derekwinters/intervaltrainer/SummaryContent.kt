package com.derekwinters.intervaltrainer

/**
 * `docs/spec/screens.md` `SCREEN-050`–`053`: the summary screen's exactly three values, derived
 * from a [TimerState] alone — the same "a screen shows only what a `:core` reducer computed for
 * it" split [RunningScreenContent] and `WorkoutNotificationContent` (`docs/spec/service.md`
 * `SVC-025`) already give their own screens, so the summary reads one answer rather than
 * reassembling [TimerState.Ended] into a display ad hoc.
 *
 * [roundsCompleted] and [roundsPlanned] are [List.roundsCompleted]'s own pair (`TIMER-060`–`061`):
 * a work interval that was **skipped** was left behind rather than finished and never counts,
 * however far skip has since moved the schedule past it. This is a different question from
 * [RunningScreenContent]'s own `roundInProgress` (`SCREEN-022a`), which counts a skipped round as
 * still "in progress" until the next one begins — that question no longer applies once the
 * workout has ended, which is exactly why the running screen and the summary never show the same
 * number for it (`SCREEN-022`'s own invariant).
 */
data class SummaryContent(
    val roundsCompleted: Int,
    val roundsPlanned: Int,
    val totalTimeMillis: Long,
    val outcome: WorkoutOutcome,
)

/**
 * `SCREEN-050`–`053`: [this] [TimerState]'s summary content — `null` outside [TimerState.Ended],
 * since the summary is reached only once a workout has ended (`TIMER-051`) and has nothing to
 * show before then.
 */
fun TimerState.summaryContent(): SummaryContent? {
    if (this !is TimerState.Ended) return null
    val (completed, planned) = schedule.roundsCompleted()
    return SummaryContent(
        roundsCompleted = completed,
        roundsPlanned = planned,
        totalTimeMillis = totalTimeMillis,
        outcome = outcome,
    )
}

package com.derekwinters.intervaltrainer.screens.running

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derekwinters.intervaltrainer.Clock
import com.derekwinters.intervaltrainer.ColorRole
import com.derekwinters.intervaltrainer.CueTimerState
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.RunningScreenContent
import com.derekwinters.intervaltrainer.ScheduleEntry
import com.derekwinters.intervaltrainer.TimerEvent
import com.derekwinters.intervaltrainer.TimerPhase
import com.derekwinters.intervaltrainer.TimerState
import com.derekwinters.intervaltrainer.colorRole
import com.derekwinters.intervaltrainer.formatSeconds
import com.derekwinters.intervaltrainer.runningScreenContent
import com.derekwinters.intervaltrainer.toggleEvent
import com.derekwinters.intervaltrainer.designsystem.AlertDialog
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.DesignSystemColors
import com.derekwinters.intervaltrainer.designsystem.FullBleedLayout
import com.derekwinters.intervaltrainer.designsystem.StatusIconButton
import com.derekwinters.intervaltrainer.designsystem.TransportIconButton
import kotlinx.coroutines.delay
import kotlin.math.abs

/** How often the display refreshes its own "now" while a workout is actually counting down
 * (`Running`, never `Paused` — the held remaining time does not move, `SVC`'s own pause
 * invariant). Independent of the service's own tick loop (`SVC-012`'s 200ms): a
 * [CueTimerState] that carries the exact same values as the one before it does not re-emit
 * through [com.derekwinters.intervaltrainer.service.WorkoutServiceState]'s `StateFlow`
 * conflation, so this screen needs its own clock-driven refresh to show time actually passing,
 * the same way any countdown UI reading a monotonic clock does. */
private const val DisplayTickIntervalMillis = 200L

/**
 * The running screen (`docs/spec/screens.md` §3, `SCREEN-020`–`046`; `docs/spec/service.md` §7,
 * `SVC-050`–`053`): the ring timer, the schedule rail, the mute/pause-resume/skip/stop controls,
 * the navigation lock, and the stop confirmation.
 *
 * [state] and [clock] are read, never written, here (`ADR 0005`'s split): every value this screen
 * shows is [state.timer]'s own [runningScreenContent] (`SCREEN-020`–`022`, `SCREEN-022a`,
 * `SCREEN-024`) or a field of [state] directly ([CueTimerState.muted]). Every control below sends
 * a command through its own callback rather than mutating [state] itself — [state] always comes
 * from [com.derekwinters.intervaltrainer.service.WorkoutServiceState], the app-wide channel the
 * foreground service owns (`ADR 0002`).
 */
@Composable
fun RunningScreen(
    state: CueTimerState,
    clock: Clock,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onStopConfirmed: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val spacing = AppTheme.spacing
    var showStopConfirmation by remember { mutableStateOf(false) }

    // See DisplayTickIntervalMillis's own doc comment: this is what actually advances the ring
    // and rail between service-published state changes, while the workout is running. Paused
    // needs no ticking at all — the held remaining time (TIMER-023) does not move.
    var tick by remember { mutableLongStateOf(0L) }
    val isCountingDown = state.timer is TimerState.Running
    LaunchedEffect(isCountingDown) {
        while (isCountingDown) {
            delay(DisplayTickIntervalMillis)
            tick++
        }
    }
    val content = remember(state.timer, tick) { state.timer.runningScreenContent(clock) }
    val scheduleAndIndex = remember(state.timer) { state.timer.scheduleAndIndex() }

    // SCREEN-042, SVC-053: system back while running raises the same stop confirmation as the
    // stop button — the only exit the locked screen offers — and never fires while paused, when
    // the rest of the app (SCREEN-041) is reachable through ordinary back navigation instead.
    // Disabling the handler entirely while paused, rather than branching inside one always-on
    // handler, is what keeps "does not fire this while paused" true by construction: a disabled
    // BackHandler does not consume the back event at all, so it falls through to the NavHost's
    // own default (pop to whatever is beneath `running` on the stack).
    BackHandler(enabled = state.timer is TimerState.Running) {
        showStopConfirmation = true
    }

    KeepScreenOn() // SCREEN-046

    FullBleedLayout(modifier = modifier) {
        MuteButton(
            muted = state.muted,
            onToggleMute = onToggleMute,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(spacing.lg),
        )

        if (content != null) {
            RoundIndicator(
                roundInProgress = content.roundInProgress,
                roundsPlanned = content.roundsPlanned,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(spacing.lg),
            )
        }

        if (content != null && scheduleAndIndex != null) {
            val (schedule, currentIndex) = scheduleAndIndex
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = spacing.xxl * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (content) {
                    is RunningScreenContent.Active -> Ring(content = content, colors = colors)
                    is RunningScreenContent.GetReady -> GetReadyContent(content = content, colors = colors)
                }
                Spacer(Modifier.height(spacing.lg))
                TotalLeft(totalRemainingMillis = content.totalRemainingMillis, colors = colors)
            }

            ScheduleRail(
                schedule = schedule,
                currentIndex = currentIndex,
                activeRemainingMillis = (content as? RunningScreenContent.Active)?.remainingMillis,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = spacing.lg)
                    .width(112.dp)
                    .fillMaxHeight(0.6f),
            )
        }

        ControlRow(
            toggleEvent = state.timer.toggleEvent(),
            onPauseResume = onPauseResume,
            onSkip = onSkip,
            onStop = { showStopConfirmation = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = spacing.xxl),
        )
    }

    if (showStopConfirmation) {
        StopConfirmationDialog(
            onConfirm = {
                showStopConfirmation = false
                onStopConfirmed()
            },
            onDismiss = { showStopConfirmation = false },
        )
    }
}

/** `SCREEN-046`: the screen stays on for the whole time the running screen is in front — lead-in,
 * running and paused-while-viewing-it alike — and stops claiming that the moment it leaves
 * composition, whether that is a confirmed stop, natural completion, or backing out while paused
 * (`SCREEN-041`). */
@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** `SCREEN-033`, `SVC-050`, `DS-010`–`011`: the destructive stop confirmation, cancel left,
 * affirmative right — `AlertDialog`'s own guaranteed ordering, not reimplemented here. */
@Composable
private fun StopConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        title = "Stop workout?",
        text = "This ends the workout now and can't be undone.",
        confirmText = "Stop",
        onConfirm = onConfirm,
        onDismissRequest = onDismiss,
        cancelText = "Cancel",
        onCancel = onDismiss,
        isConfirmDestructive = true,
    )
}

/** `SCREEN-030`: the mute toggle, in the status icon-button role (`DS-005`) — smaller than the
 * transport row below, per the settled design's own top-corner placement. */
@Composable
private fun MuteButton(muted: Boolean, onToggleMute: () -> Unit, modifier: Modifier = Modifier) {
    StatusIconButton(
        icon = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
        contentDescription = if (muted) "Unmute" else "Mute",
        onClick = onToggleMute,
        modifier = modifier,
    )
}

/** `SCREEN-022`, `SCREEN-022a`: "Round 2 of 4", the round currently in progress against rounds
 * planned — never rounds completed (`TIMER-061`), which this screen never shows. */
@Composable
private fun RoundIndicator(
    roundInProgress: Int,
    roundsPlanned: Int,
    colors: DesignSystemColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BasicText(
            text = "ROUND",
            style = TextStyle(color = colors.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
        BasicText(
            text = "$roundInProgress of $roundsPlanned",
            style = AppTheme.timerTypography.stat.copy(color = colors.fg, fontWeight = FontWeight.Bold),
        )
    }
}

/** `SCREEN-021`: total remaining time across the whole workout (`TIMER-025`). */
@Composable
private fun TotalLeft(totalRemainingMillis: Long, colors: DesignSystemColors, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(text = "Total left ", style = TextStyle(color = colors.dim, fontSize = 13.sp))
        BasicText(
            text = formatSeconds(totalRemainingMillis.toWholeSeconds()),
            style = AppTheme.timerTypography.stat.copy(color = colors.fg, fontWeight = FontWeight.Bold),
        )
    }
}

/** `SCREEN-020`: the current interval's kind, colour, and remaining time against its full
 * duration — "0:32 of 0:45" — as a depleting ring: the coloured arc is what remains, so it
 * shrinks as the interval runs out, the same reading direction the settled design's own mockup
 * shows. */
@Composable
private fun Ring(content: RunningScreenContent.Active, colors: DesignSystemColors, modifier: Modifier = Modifier) {
    val ringColor = content.kind.dotColor(colors)
    val fraction = if (content.fullDurationMillis > 0) {
        (content.remainingMillis.toFloat() / content.fullDurationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 14.dp.toPx()
            drawArc(
                color = colors.line,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ringColor),
                )
                BasicText(
                    text = content.kind.displayName(),
                    style = TextStyle(color = ringColor, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            BasicText(
                text = formatSeconds(content.remainingMillis.toWholeSeconds()),
                style = AppTheme.timerTypography.large.copy(color = colors.fg),
            )
            BasicText(
                text = "of ${formatSeconds(content.fullDurationMillis.toWholeSeconds())}",
                style = TextStyle(color = colors.dim, fontSize = 13.sp),
            )
        }
    }
}

/** `SCREEN-024`: the lead-in's own distinct "get ready" state — no ring, no partially-formed
 * interval display, per `prototypes/screens/running-screen/States.dc.html`. */
@Composable
private fun GetReadyContent(
    content: RunningScreenContent.GetReady,
    colors: DesignSystemColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = "GET READY",
            style = TextStyle(color = colors.dim, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
        val secondsRemaining = ((content.remainingMillis + 999L) / 1_000L).coerceAtLeast(0L)
        BasicText(
            text = "$secondsRemaining",
            style = AppTheme.timerTypography.large.copy(color = colors.fg),
        )
        BasicText(text = "seconds", style = TextStyle(color = colors.dim, fontSize = 13.sp))
        BasicText(
            text = "Starting — ${content.upcomingKind.displayName()}, " +
                formatSeconds(content.upcomingDurationMillis.toWholeSeconds()),
            style = TextStyle(color = colors.dim, fontSize = 12.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** `SCREEN-023`: every interval in the schedule, in order, the current one emphasised and each
 * other one shrinking and dimming with its distance from it — a preview, not a control (no row
 * carries a click handler of any kind). */
@Composable
private fun ScheduleRail(
    schedule: List<ScheduleEntry>,
    currentIndex: Int,
    activeRemainingMillis: Long?,
    colors: DesignSystemColors,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem(index = (currentIndex - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
    ) {
        itemsIndexed(schedule) { index, entry ->
            RailRow(
                entry = entry,
                isCurrent = index == currentIndex,
                distance = abs(index - currentIndex),
                activeRemainingMillis = if (index == currentIndex) activeRemainingMillis else null,
                colors = colors,
            )
        }
    }
}

@Composable
private fun RailRow(
    entry: ScheduleEntry,
    isCurrent: Boolean,
    distance: Int,
    activeRemainingMillis: Long?,
    colors: DesignSystemColors,
    modifier: Modifier = Modifier,
) {
    val rowAlpha = if (isCurrent) 1f else (1f - 0.18f * distance).coerceIn(0.3f, 1f)
    val rowScale = if (isCurrent) 1f else (1f - 0.06f * distance).coerceIn(0.75f, 1f)
    val kindColor = entry.interval.kind.dotColor(colors)
    val trailingSeconds = activeRemainingMillis?.toWholeSeconds() ?: entry.interval.durationSeconds

    Row(
        modifier = modifier
            .graphicsLayer {
                this.alpha = rowAlpha
                scaleX = rowScale
                scaleY = rowScale
            }
            .then(
                if (isCurrent) {
                    Modifier
                        .clip(RoundedCornerShape(AppTheme.spacing.xs))
                        .background(colors.chip)
                        .border(1.dp, kindColor, RoundedCornerShape(AppTheme.spacing.xs))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(kindColor),
        )
        BasicText(
            text = entry.interval.kind.displayName(),
            style = TextStyle(
                color = if (isCurrent) colors.fg else colors.dim,
                fontSize = if (isCurrent) 13.sp else 11.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            ),
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.width(6.dp))
        BasicText(
            text = formatSeconds(trailingSeconds),
            style = TextStyle(color = if (isCurrent) kindColor else colors.dim, fontSize = 11.sp),
        )
    }
}

/** `SCREEN-031`–`033`: pause/resume (one toggle, `TIMER-014`), skip (`TIMER-040`–`045`), and stop
 * (`SVC-050`) — the transport icon-button role (`DS-006`), per [TransportIconButton]'s own doc
 * comment naming these exact controls. */
@Composable
private fun ControlRow(
    toggleEvent: TimerEvent?,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportIconButton(icon = Icons.Filled.SkipNext, contentDescription = "Skip", onClick = onSkip)
        TransportIconButton(
            icon = if (toggleEvent == TimerEvent.Resume) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = if (toggleEvent == TimerEvent.Resume) "Resume" else "Pause",
            onClick = onPauseResume,
        )
        TransportIconButton(icon = Icons.Filled.Stop, contentDescription = "Stop", onClick = onStop)
    }
}

private fun Long.toWholeSeconds(): Int = (this / 1_000L).toInt().coerceAtLeast(0)

/** [ScheduleEntry.interval]'s own schedule position, shared by [TimerState.Running] and
 * [TimerState.Paused] alike, and `null` outside both — the same split [runningScreenContent]
 * itself makes. */
private fun TimerState.scheduleAndIndex(): Pair<List<ScheduleEntry>, Int>? = when (this) {
    is TimerState.Running -> schedule to phase.index()
    is TimerState.Paused -> schedule to phase.index()
    else -> null
}

private fun TimerPhase.index(): Int = when (this) {
    is TimerPhase.LeadIn -> index
    is TimerPhase.InInterval -> index
}

private fun IntervalKind.displayName(): String = when (this) {
    IntervalKind.WARM_UP -> "Warm-up"
    IntervalKind.WORK -> "Work"
    IntervalKind.RECOVERY -> "Recovery"
    IntervalKind.COOL_DOWN -> "Cool-down"
}

/** The one place `:app` maps `:core`'s [ColorRole] (`CUE-030`) to a real [Color] (`DS-050`) for
 * this screen's own ring, kind label and rail ticks — `:core` does not depend on `:designsystem`
 * and has no way to know this mapping itself, the same reason every other screen's own
 * `toColor`/`dotColor` exists. */
private fun IntervalKind.dotColor(colors: DesignSystemColors): Color = when (colorRole()) {
    ColorRole.WORK -> colors.work
    ColorRole.RECOVERY -> colors.recovery
    ColorRole.NEUTRAL -> colors.neutral
}

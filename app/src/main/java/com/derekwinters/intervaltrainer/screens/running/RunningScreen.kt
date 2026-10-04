package com.derekwinters.intervaltrainer.screens.running

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
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
import com.derekwinters.intervaltrainer.ScheduleRow
import com.derekwinters.intervaltrainer.TimelineSegment
import com.derekwinters.intervaltrainer.TimerEvent
import com.derekwinters.intervaltrainer.TimerState
import com.derekwinters.intervaltrainer.colorRole
import com.derekwinters.intervaltrainer.formatSeconds
import com.derekwinters.intervaltrainer.runningScreenContent
import com.derekwinters.intervaltrainer.runningScreenSchedule
import com.derekwinters.intervaltrainer.toggleEvent
import com.derekwinters.intervaltrainer.designsystem.AlertDialog
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.DesignSystemColors
import com.derekwinters.intervaltrainer.designsystem.FullBleedLayout
import com.derekwinters.intervaltrainer.designsystem.StatusIconButton
import com.derekwinters.intervaltrainer.designsystem.TransportIconButton
import kotlinx.coroutines.delay

/** How often the display refreshes its own "now" while a workout is actually counting down
 * (`Running`, never `Paused` — the held remaining time does not move, `SVC`'s own pause
 * invariant). Independent of the service's own tick loop (`SVC-012`'s 200ms): a
 * [CueTimerState] that carries the exact same values as the one before it does not re-emit
 * through [com.derekwinters.intervaltrainer.service.WorkoutServiceState]'s `StateFlow`
 * conflation, so this screen needs its own clock-driven refresh to show time actually passing,
 * the same way any countdown UI reading a monotonic clock does. */
private const val DisplayTickIntervalMillis = 200L

// docs/spec/screens.md §3, issue #151: the stacked layout's measurements, at a 360 × 760dp
// portrait reference screen, measured from FullBleedLayout's edges.
private val StatusButtonDrawnHeight = 38.dp // StatusIconButton's drawn size; Total left centres on it
private val StripTop = 66.dp // SCREEN-025
private val StripHeight = 8.dp
private val StripCornerRadius = 2.dp
private val CenterColumnTop = 92.dp // SCREEN-028
private val CenterColumnBottom = 100.dp
private val CenterColumnGap = 18.dp
private val ListWidth = 190.dp
private val PastListMinHeight = 64.dp
private val UpcomingListMinHeight = 140.dp
private val RingSize = 176.dp // SCREEN-020, SCREEN-024
private val RingStroke = 11.dp
private val RingDigitsSize = 51.sp

/**
 * The running screen (`docs/spec/screens.md` §3, `SCREEN-020`–`046`; `docs/spec/service.md` §7,
 * `SVC-050`–`053`): the timeline strip, the ring timer between the past and upcoming lists, the
 * mute/pause-resume/skip/stop controls, the navigation lock, the portrait lock, and the stop
 * confirmation.
 *
 * [state] and [clock] are read, never written, here (`ADR 0005`'s split): every value this screen
 * shows is [state.timer]'s own [runningScreenContent] (`SCREEN-020`, `SCREEN-021`, `SCREEN-024`),
 * its [runningScreenSchedule] (`SCREEN-023`–`026`), or a field of [state] directly
 * ([CueTimerState.muted]). Every control below sends a command through its own callback rather
 * than mutating [state] itself — [state] always comes from [com.derekwinters.intervaltrainer.service.WorkoutServiceState], the app-wide channel the
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
    // between service-published state changes, while the workout is running. Paused needs no
    // ticking at all — the held remaining time (TIMER-023) does not move.
    var tick by remember { mutableLongStateOf(0L) }
    val isCountingDown = state.timer is TimerState.Running
    LaunchedEffect(isCountingDown) {
        while (isCountingDown) {
            delay(DisplayTickIntervalMillis)
            tick++
        }
    }
    val content = remember(state.timer, tick) { state.timer.runningScreenContent(clock) }
    val schedule = remember(state.timer) { state.timer.runningScreenSchedule() }

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
    LockPortrait() // SCREEN-027

    FullBleedLayout(modifier = modifier) {
        MuteButton(
            muted = state.muted,
            onToggleMute = onToggleMute,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(spacing.lg),
        )

        if (content != null) {
            // SCREEN-021: top centre, centre line at 16 + 19 = 35dp, as #151 fixes it.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = spacing.lg)
                    .height(StatusButtonDrawnHeight),
                contentAlignment = Alignment.Center,
            ) {
                TotalLeft(totalRemainingMillis = content.totalRemainingMillis, colors = colors)
            }
        }

        if (content != null && schedule != null) {
            TimelineStrip(
                segments = schedule.strip,
                gapDp = schedule.stripGapDp,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = StripTop, start = spacing.xl, end = spacing.xl)
                    .fillMaxWidth()
                    .height(StripHeight),
            )

            // SCREEN-028: one centred column between 92dp from the top and 100dp from the
            // bottom, its children centred as a group. The lists' minimum heights keep the ring
            // in place whatever number of rows they hold.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = CenterColumnTop, bottom = CenterColumnBottom),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CenterColumnGap, Alignment.CenterVertically),
            ) {
                ScheduleList(
                    rows = schedule.past,
                    colors = colors,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .width(ListWidth)
                        .heightIn(min = PastListMinHeight),
                )
                Box(modifier = Modifier.size(RingSize), contentAlignment = Alignment.Center) {
                    when (content) {
                        is RunningScreenContent.Active -> Ring(content = content, colors = colors)
                        is RunningScreenContent.GetReady -> GetReadyContent(content = content, colors = colors)
                    }
                }
                ScheduleList(
                    rows = schedule.upcoming,
                    colors = colors,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .width(ListWidth)
                        .heightIn(min = UpcomingListMinHeight),
                )
            }
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

/** `SCREEN-027`: the running screen is locked to portrait while it is shown, and only then.
 * Leaving it resets the activity to the platform default
 * ([ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED]) — what every other screen uses, since nothing
 * else sets an orientation. The reset is skipped while the activity is being recreated for a
 * configuration change: the lock is still wanted on the recreated activity, and resetting it there
 * would hand the rotation straight back. */
@Composable
private fun LockPortrait() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
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
 * duration — "0:32 of 0:45" — as a depleting 176dp ring with an 11dp stroke: the coloured arc is
 * what remains, so it shrinks as the interval runs out, the same reading direction the settled
 * design's own mockup shows. */
@Composable
private fun Ring(content: RunningScreenContent.Active, colors: DesignSystemColors, modifier: Modifier = Modifier) {
    val ringColor = content.kind.dotColor(colors)
    val fraction = if (content.fullDurationMillis > 0) {
        (content.remainingMillis.toFloat() / content.fullDurationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(RingSize)) {
            val strokeWidth = RingStroke.toPx()
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
                style = AppTheme.timerTypography.large.copy(color = colors.fg, fontSize = RingDigitsSize),
            )
            BasicText(
                text = "of ${formatSeconds(content.fullDurationMillis.toWholeSeconds())}",
                style = TextStyle(color = colors.dim, fontSize = 13.sp),
            )
        }
    }
}

/** `SCREEN-024`: the lead-in's own distinct "get ready" state — no ring, no partially-formed
 * interval display, per `prototypes/screens/running-screen/States.dc.html` — in the ring's 176dp
 * slot, its number at the ring's 51sp. */
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
            style = AppTheme.timerTypography.large.copy(color = colors.fg, fontSize = RingDigitsSize),
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

/** `SCREEN-025`, `SCREEN-026`: one equal-width segment per schedule entry, only the current one at
 * full alpha (the page's invariant), with the gap `:core` chose for the schedule's length. No
 * text, and no click handler of any kind. */
@Composable
private fun TimelineStrip(
    segments: List<TimelineSegment>,
    gapDp: Int,
    colors: DesignSystemColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gapDp.dp)) {
        segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .alpha(segment.alpha)
                    .clip(RoundedCornerShape(StripCornerRadius))
                    .background(segment.kind.dotColor(colors)),
            )
        }
    }
}

/** `SCREEN-023`: the past or upcoming list — rows `spacing.sm` apart, bottom-aligned above the ring
 * and top-aligned below it. A preview, not a control: no row carries a click handler. */
@Composable
private fun ScheduleList(
    rows: List<ScheduleRow>,
    colors: DesignSystemColors,
    verticalAlignment: Alignment.Vertical,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm, verticalAlignment),
    ) {
        rows.forEach { row -> ScheduleListRow(row = row, colors = colors) }
    }
}

/** `SCREEN-023`: one row, faded and shrunk by `:core`'s alpha and scale for its distance, scaled
 * around its own centre; kind bar and name on the left, duration at the right edge. */
@Composable
private fun ScheduleListRow(row: ScheduleRow, colors: DesignSystemColors, modifier: Modifier = Modifier) {
    val kindColor = row.interval.kind.dotColor(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = row.alpha
                scaleX = row.scale
                scaleY = row.scale
                transformOrigin = TransformOrigin.Center
            }
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
            text = row.interval.kind.displayName(),
            style = TextStyle(color = colors.dim, fontSize = 11.sp),
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = formatSeconds(row.interval.durationSeconds),
            style = TextStyle(color = colors.dim, fontSize = 11.sp),
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

private fun IntervalKind.displayName(): String = when (this) {
    IntervalKind.WARM_UP -> "Warm-up"
    IntervalKind.WORK -> "Work"
    IntervalKind.RECOVERY -> "Recovery"
    IntervalKind.COOL_DOWN -> "Cool-down"
}

/** The one place `:app` maps `:core`'s [ColorRole] (`CUE-030`) to a real [Color] (`DS-050`) for
 * this screen's own ring, kind label, list rows and strip segments — `:core` does not depend on `:designsystem`
 * and has no way to know this mapping itself, the same reason every other screen's own
 * `toColor`/`dotColor` exists. */
private fun IntervalKind.dotColor(colors: DesignSystemColors): Color = when (colorRole()) {
    ColorRole.WORK -> colors.work
    ColorRole.RECOVERY -> colors.recovery
    ColorRole.NEUTRAL -> colors.neutral
}

package com.derekwinters.intervaltrainer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.derekwinters.intervaltrainer.database.IntervalTrainerDatabase
import com.derekwinters.intervaltrainer.database.RoomPresetStore
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.screens.PlaceholderScreen
import com.derekwinters.intervaltrainer.screens.editor.PresetEditorScreen
import com.derekwinters.intervaltrainer.screens.home.HomeScreen
import com.derekwinters.intervaltrainer.screens.running.RunningScreen
import com.derekwinters.intervaltrainer.service.ElapsedRealtimeClock
import com.derekwinters.intervaltrainer.service.WorkoutService
import com.derekwinters.intervaltrainer.service.WorkoutServiceState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The launcher activity (BUILD-012, BUILD-018).
 *
 * Hosts a single Compose Navigation graph, wrapped in `:designsystem`'s [AppTheme] (`ADR 0007`):
 * `home` (`docs/spec/screens.md` §1, the home screen, `#79`), `editor/{presetId}` (§2, the
 * preset editor, `#80`), and `running` (§3, the running screen, `#81`), plus one placeholder
 * destination each for `summary` (§4) and `settings` (§5) — neither of which is built yet. See
 * [PlaceholderScreen]'s own doc comment, and this pull request's Deviations section, for why
 * registering routes ahead of the screen behind them is this project's own staging choice.
 *
 * `SCREEN-043`: opening the app while a workout exists lands on the running screen, on every
 * entry. [onNewIntent] carries the one entry point a start destination computed once at
 * composition cannot cover — the notification's own `PendingIntent`
 * ([WorkoutService.EXTRA_OPEN_RUNNING_WORKOUT]) arriving at an already-running instance of this
 * activity via `FLAG_ACTIVITY_SINGLE_TOP`, while some other screen is in front. A cold start with
 * a workout already running or paused is instead covered by [IntervalTrainerNavHost]'s own start
 * destination, computed from [WorkoutServiceState] — the same app-wide observation channel the
 * running screen itself reads — rather than only from this extra, so the lock holds "on every
 * entry, not only on tapping the notification" exactly as `SCREEN-043` requires.
 */
class MainActivity : ComponentActivity() {

    private lateinit var database: IntervalTrainerDatabase
    private val openRunningRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SCHEMA-004: opened once here, the same way AppDatabase.open documents, so this activity
        // and WorkoutService never read two different database files.
        database = AppDatabase.open(applicationContext)
        val presetStore = RoomPresetStore(database.presetDao())
        setContent {
            AppTheme {
                IntervalTrainerNavHost(presetStore = presetStore, openRunningRequests = openRunningRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(WorkoutService.EXTRA_OPEN_RUNNING_WORKOUT, false)) {
            openRunningRequests.tryEmit(Unit)
        }
    }

    override fun onDestroy() {
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }
}

private const val ROUTE_HOME = "home"
private const val ARG_PRESET_ID = "presetId"
private const val ROUTE_EDITOR = "editor/{$ARG_PRESET_ID}"
private const val ROUTE_RUNNING = "running"
private const val ROUTE_SUMMARY = "summary"
private const val ROUTE_SETTINGS = "settings"

/** `SCREEN-008`'s "new, empty preset": not a real preset id, so `PresetEditorScreen`'s own
 * `NavHost` entry below can tell the two cases — a new preset versus one already saved — apart. */
private const val NEW_PRESET_ID = "new"

private fun editorRoute(presetId: String) = "editor/$presetId"

/**
 * The navigation graph (`BUILD-018`): `home` is the start destination, unless a workout is
 * already running or paused (`SCREEN-043`, see below); `editor/{presetId}` (`#80`) and `running`
 * (`#81`) are real screens; `summary` and `settings` are registered routes still behind a
 * [PlaceholderScreen], per `MainActivity`'s own doc comment.
 *
 * `SCREEN-043`: [startDestination] is read once, synchronously, from [WorkoutServiceState] — the
 * running screen's own observation channel — rather than from [MainActivity]'s intent, so a cold
 * start lands on `running` regardless of what opened the app: the launcher, the notification, or
 * any other entry point holding a live or paused workout. [openRunningRequests] is the one case
 * that reaches an *already-composed* `NavHost` instead — [MainActivity.onNewIntent]'s own doc
 * comment says why a start destination alone cannot cover it.
 */
@Composable
private fun IntervalTrainerNavHost(presetStore: PresetStore, openRunningRequests: SharedFlow<Unit>) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clock = remember { ElapsedRealtimeClock() }
    val startDestination = remember {
        if (WorkoutServiceState.current.value.timer.isActiveWorkout()) ROUTE_RUNNING else ROUTE_HOME
    }
    LaunchedEffect(Unit) {
        openRunningRequests.collect {
            navController.navigate(ROUTE_RUNNING) { launchSingleTop = true }
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_HOME) {
            var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }
            // SCHEMA-004: presetStore.presets() is a blocking Room call (PresetDao.kt), read off
            // the main thread here rather than inside the composition itself.
            LaunchedEffect(Unit) {
                presets = withContext(Dispatchers.IO) { presetStore.presets() }
            }
            HomeScreen(
                presets = presets,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onNewPreset = { navController.navigate(editorRoute(NEW_PRESET_ID)) },
                onEditPreset = { presetId -> navController.navigate(editorRoute(presetId)) },
                onStartPreset = { preset ->
                    // TIMER-013: starts the workout immediately, via the already-existing
                    // foreground service (SVC-010–013) — real regardless of the running screen's
                    // own state.
                    context.startWorkoutService(preset.id)
                    navController.navigate(ROUTE_RUNNING)
                },
                modifier = Modifier,
            )
        }
        composable(
            route = ROUTE_EDITOR,
            arguments = listOf(navArgument(ARG_PRESET_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val presetId = backStackEntry.arguments?.getString(ARG_PRESET_ID) ?: NEW_PRESET_ID
            // SCREEN-008: NEW_PRESET_ID never names a real row, so it needs no Room lookup at
            // all — a fresh, empty Preset with a freshly generated id (SCHEMA-011) is available
            // synchronously, the moment this composable enters, rather than behind a loading gap
            // that only ever existed for SCREEN-007's real-preset case below.
            var preset by remember(presetId) {
                mutableStateOf(
                    if (presetId == NEW_PRESET_ID) {
                        Preset(id = UUID.randomUUID().toString(), name = "", intervals = emptyList())
                    } else {
                        null
                    },
                )
            }
            LaunchedEffect(presetId) {
                if (presetId != NEW_PRESET_ID) {
                    // SCREEN-007: reads the real preset off the main thread (SCHEMA-004). If it
                    // has since been deleted from under this navigation (not possible in v1, with
                    // no delete-preset control anywhere, but not ruled out by the route itself),
                    // the fallback is the same fresh-empty-preset shape rather than a crash.
                    preset = withContext(Dispatchers.IO) {
                        presetStore.preset(presetId)
                            ?: Preset(id = presetId, name = "", intervals = emptyList())
                    }
                }
            }
            preset?.let { loadedPreset ->
                PresetEditorScreen(
                    preset = loadedPreset,
                    onBack = { navController.popBackStack() },
                    onSave = { updated ->
                        // SCREEN-019: persists via :database (SCHEMA-004, SCHEMA-010, SCHEMA-025)
                        // off the main thread, then returns to home — the same Room-is-blocking
                        // discipline this NavHost already applies to reading the preset list.
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { presetStore.save(updated) }
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier,
                )
            }
        }
        composable(ROUTE_RUNNING) {
            val cueState by WorkoutServiceState.current.collectAsState()
            // TIMER-051, SCREEN-044: a workout that ends — via a confirmed stop (SVC-051) or
            // running out on its own — goes to the summary, whichever way it got there. This one
            // effect is the only place that navigates away from `running` on outcome, so the two
            // causes reach the same destination by construction rather than two call sites having
            // to agree. `#82` (the summary screen) is not built yet; this lands on the same
            // interim-placeholder pattern `#79` used for `running` and `settings` themselves,
            // named as such per this pull request's Deviations section.
            LaunchedEffect(cueState.timer) {
                if (cueState.timer is TimerState.Ended) {
                    navController.navigate(ROUTE_SUMMARY) {
                        popUpTo(ROUTE_RUNNING) { inclusive = true }
                    }
                }
            }
            RunningScreen(
                state = cueState,
                clock = clock,
                onPauseResume = {
                    // The invariant `docs/spec/service.md` carries forward from `ADR 0002`: the
                    // pause/resume toggle is one function of state (`toggleEvent()`, `SVC-026`),
                    // called from everywhere it appears — the notification and this screen alike
                    // (`SVC-023`), never a second, locally reinvented decision of which it means.
                    when (cueState.timer.toggleEvent()) {
                        TimerEvent.Pause -> context.sendWorkoutCommand(WorkoutService.pauseIntent(context))
                        TimerEvent.Resume -> context.sendWorkoutCommand(WorkoutService.resumeIntent(context))
                        else -> Unit
                    }
                },
                onSkip = { context.sendWorkoutCommand(WorkoutService.skipIntent(context)) },
                onStopConfirmed = {
                    // SVC-051: ends the workout; the LaunchedEffect above navigates to the
                    // summary once TimerState.Ended is actually observed, rather than this
                    // callback navigating optimistically ahead of the state that justifies it.
                    context.sendWorkoutCommand(WorkoutService.stopIntent(context))
                },
                onToggleMute = { context.sendWorkoutCommand(WorkoutService.toggleMuteIntent(context)) },
                modifier = Modifier,
            )
        }
        composable(ROUTE_SUMMARY) {
            PlaceholderScreen(label = "Summary (#82)")
        }
        composable(ROUTE_SETTINGS) {
            PlaceholderScreen(label = "Settings")
        }
    }
}

/** `SCREEN-043`: a workout the app should already be locked onto — running or paused, as opposed
 * to idle (never started) or ended (already on its way to the summary). */
private fun TimerState.isActiveWorkout(): Boolean =
    this is TimerState.Running || this is TimerState.Paused

/** `SVC-010`: this may promote the service to the foreground almost immediately (once the started
 * workout's timer reaches `Running`), so `ContextCompat.startForegroundService` is used rather
 * than plain `startService` — a no-op difference pre-`O`, and correct regardless of whether this
 * call happens to run while the app is foregrounded. */
private fun Context.startWorkoutService(presetId: String) {
    ContextCompat.startForegroundService(this, WorkoutService.startIntent(this, presetId))
}

/** `SVC-013`: pause, resume, skip, stop and the mute toggle, sent to an already-started service —
 * a plain `startService`, unlike [startWorkoutService]'s own `startForegroundService`, since these
 * only ever fire from the running screen while the app itself is in the foreground. */
private fun Context.sendWorkoutCommand(intent: Intent) {
    startService(intent)
}

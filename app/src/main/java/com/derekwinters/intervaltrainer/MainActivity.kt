package com.derekwinters.intervaltrainer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.derekwinters.intervaltrainer.database.IntervalTrainerDatabase
import com.derekwinters.intervaltrainer.database.RoomPresetStore
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.screens.editor.PresetEditorScreen
import com.derekwinters.intervaltrainer.screens.firstrun.FirstRunScreen
import com.derekwinters.intervaltrainer.screens.home.HomeScreen
import com.derekwinters.intervaltrainer.screens.running.RunningScreen
import com.derekwinters.intervaltrainer.screens.settings.SettingsScreen
import com.derekwinters.intervaltrainer.screens.summary.SummaryScreen
import com.derekwinters.intervaltrainer.service.ElapsedRealtimeClock
import com.derekwinters.intervaltrainer.service.WorkoutService
import com.derekwinters.intervaltrainer.service.WorkoutServiceState
import com.derekwinters.intervaltrainer.settings.DataStoreDefaultMuteStore
import com.derekwinters.intervaltrainer.settings.DataStoreFirstRunStore
import com.derekwinters.intervaltrainer.settings.DefaultMuteStore
import com.derekwinters.intervaltrainer.settings.FirstRunStore
import com.derekwinters.intervaltrainer.settings.notificationSettingsDeepLink
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The launcher activity (BUILD-012, BUILD-018).
 *
 * Hosts a single Compose Navigation graph, wrapped in `:designsystem`'s [AppTheme] (`ADR 0007`):
 * `home` (`docs/spec/screens.md` §1, the home screen, `#79`), `editor/{presetId}` (§2, the
 * preset editor, `#80`), `running` (§3, the running screen, `#81`), `summary` (§4, the summary
 * screen, `#82`), `settings` (§5, `#83`), and `first_run` (§6, `#84`) — every v1 screen.
 * `docs/spec/build.md` `BUILD-018` documents the staging convention that let each of these land
 * ahead of the screen behind its own route, back when one still would have.
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
        // SCREEN-080: the same DefaultMuteStore seam WorkoutService reads from (WorkoutService.kt),
        // opened separately here rather than shared with it — DataStore's own singleton-per-file
        // guard (one `PreferenceDataStoreFactory` per file path per process) is what
        // `preferencesDataStore`'s delegate already provides, so two independently constructed
        // `DataStoreDefaultMuteStore` instances in the same process still resolve to the one
        // underlying file, the same way this activity and WorkoutService already open the Room
        // database (`AppDatabase.open`) independently rather than sharing one instance across
        // process components that do not share a lifecycle.
        val defaultMuteStore = DataStoreDefaultMuteStore(applicationContext)
        val firstRunStore = DataStoreFirstRunStore(applicationContext)
        // SCREEN-043, SCREEN-070: the NavHost's start destination needs this value synchronously,
        // before the first frame — the same blocking-in-onCreate convention AppDatabase.open
        // above and WorkoutService.onCreate's own read of DefaultMuteStore already use, rather
        // than a collectAsState default that would show the first-run screen again, every launch,
        // until DataStore's own async read completed (FirstRunStore.kt's own doc comment).
        val firstRunSeenAtLaunch = runBlocking { firstRunStore.firstRunSeen.first() }
        setContent {
            AppTheme {
                IntervalTrainerNavHost(
                    presetStore = presetStore,
                    defaultMuteStore = defaultMuteStore,
                    firstRunStore = firstRunStore,
                    firstRunSeenAtLaunch = firstRunSeenAtLaunch,
                    openRunningRequests = openRunningRequests,
                )
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
private const val ROUTE_FIRST_RUN = "first_run"

/** `SCREEN-008`'s "new, empty preset": not a real preset id, so `PresetEditorScreen`'s own
 * `NavHost` entry below can tell the two cases — a new preset versus one already saved — apart. */
private const val NEW_PRESET_ID = "new"

private fun editorRoute(presetId: String) = "editor/$presetId"

/**
 * The navigation graph (`BUILD-018`): `home` is the start destination, unless a workout is already
 * running or paused, or first run has never been seen (`SCREEN-043`, `SCREEN-070`, see below);
 * `editor/{presetId}` (`#80`), `running` (`#81`), `summary` (`#82`), `settings` (`#83`) and
 * `first_run` (`#84`) are all real screens now — every v1 screen.
 *
 * `SCREEN-043`, `SCREEN-070`: [startRoute] is read once, synchronously, from [WorkoutServiceState]
 * — the running screen's own observation channel — and [firstRunSeenAtLaunch], rather than from
 * [MainActivity]'s intent, so a cold start lands on `running` or `first_run` regardless of what
 * opened the app: the launcher, the notification, or any other entry point. The two facts are
 * combined by [startDestination] (`StartDestination.kt`), a pure function tested on its own in
 * `StartDestinationTest.kt`, not reassembled ad hoc here. [openRunningRequests] is the one case
 * that reaches an *already-composed* `NavHost` instead — [MainActivity.onNewIntent]'s own doc
 * comment says why a start destination alone cannot cover it.
 */
@Composable
private fun IntervalTrainerNavHost(
    presetStore: PresetStore,
    defaultMuteStore: DefaultMuteStore,
    firstRunStore: FirstRunStore,
    firstRunSeenAtLaunch: Boolean,
    openRunningRequests: SharedFlow<Unit>,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clock = remember { ElapsedRealtimeClock() }
    val startRoute = remember {
        when (
            startDestination(
                firstRunSeen = firstRunSeenAtLaunch,
                workoutActive = WorkoutServiceState.current.value.timer.isActiveWorkout(),
            )
        ) {
            StartDestination.FIRST_RUN -> ROUTE_FIRST_RUN
            StartDestination.RUNNING -> ROUTE_RUNNING
            StartDestination.HOME -> ROUTE_HOME
        }
    }
    LaunchedEffect(Unit) {
        openRunningRequests.collect {
            navController.navigate(ROUTE_RUNNING) { launchSingleTop = true }
        }
    }
    NavHost(navController = navController, startDestination = startRoute) {
        composable(ROUTE_FIRST_RUN) {
            // SCREEN-072, SVC-030: the permission prompt is requested at most once, ever
            // (SVC-032), from this one call site — no other screen in the app requests it.
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                // SCREEN-072, SCREEN-073: whatever the answer, proceed to home — this screen's
                // only job is done either way, and a declined answer never gates reaching it.
                navController.navigate(ROUTE_HOME) {
                    popUpTo(ROUTE_FIRST_RUN) { inclusive = true }
                }
            }
            FirstRunScreen(
                onGetStarted = {
                    // SCREEN-070, SCREEN-080: the flag is persisted the moment this action is
                    // taken — before the system prompt is launched, not after it resolves — so
                    // an app process interrupted between this tap and the system's own callback
                    // still never shows this screen a second time (docs/spec/screens.md §6's own
                    // ordering invariant).
                    coroutineScope.launch { firstRunStore.setFirstRunSeen(true) }
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                modifier = Modifier,
            )
        }
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
            // to agree.
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
            val cueState by WorkoutServiceState.current.collectAsState()
            SummaryScreen(
                state = cueState,
                onDone = {
                    // SCREEN-052: the summary's one action returns to home. The back stack beneath
                    // `summary` varies with how it was reached (SCREEN-043's own cold-start-on-
                    // `running` case never pushes `home` at all), so this clears the whole stack —
                    // up to and including the graph's own root, which every back stack carries
                    // regardless of its start destination — rather than popping a specific route
                    // that might not be present, and lands on one fresh `home` entry either way.
                    navController.navigate(ROUTE_HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier,
            )
        }
        composable(ROUTE_SETTINGS) {
            val defaultMuted by defaultMuteStore.defaultMuted.collectAsState(initial = false)
            val lifecycleOwner = LocalLifecycleOwner.current
            var notificationPermissionGranted by remember {
                mutableStateOf(context.isNotificationPermissionGranted())
            }
            // SCREEN-063: the permission can only change from outside this screen — the system
            // settings page onOpenNotificationSettings below opens — so it is re-read on every
            // resume rather than once at composition, the way a value this screen itself owns
            // (SCREEN-061's default mute) would not need to be.
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationPermissionGranted = context.isNotificationPermissionGranted()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            SettingsScreen(
                defaultMuted = defaultMuted,
                onDefaultMutedChange = { muted ->
                    coroutineScope.launch { defaultMuteStore.setDefaultMuted(muted) }
                },
                notificationPermissionGranted = notificationPermissionGranted,
                onOpenNotificationSettings = { context.openNotificationSettings() },
                // SCREEN-060: home's own trailing header action is settings' only entry point in
                // v1, so popping the back stack always lands back on home — the same convention
                // the preset editor's own onBack already uses.
                onBack = { navController.popBackStack() },
                modifier = Modifier,
            )
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

/** `SCREEN-063`: the same `NotificationManagerCompat.areNotificationsEnabled()` check
 * `WorkoutService.postNotification` already uses to decide whether to post at all — read here
 * for the settings row's own display rather than a second mechanism for the same fact. */
private fun Context.isNotificationPermissionGranted(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

/** `SVC-033`: opens this app's own page in the system's notification settings, from the plain
 * action/extra pair [com.derekwinters.intervaltrainer.settings.notificationSettingsDeepLink]
 * resolves — the one place that plain value becomes a real `Intent`. */
private fun Context.openNotificationSettings() {
    val deepLink = notificationSettingsDeepLink(packageName)
    startActivity(Intent(deepLink.action).putExtra(deepLink.extraKey, deepLink.packageName))
}

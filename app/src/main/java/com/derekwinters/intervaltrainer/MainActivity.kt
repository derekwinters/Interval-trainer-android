package com.derekwinters.intervaltrainer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.derekwinters.intervaltrainer.service.WorkoutService
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The launcher activity (BUILD-012, BUILD-018).
 *
 * Hosts a single Compose Navigation graph, wrapped in `:designsystem`'s [AppTheme] (`ADR 0007`):
 * `home` (`docs/spec/screens.md` §1, the home screen, `#79`) and `editor/{presetId}` (§2, the
 * preset editor, `#80`), plus one placeholder destination each for the running screen (§3, `#81`)
 * and settings (§5) — neither of which is built yet. See [PlaceholderScreen]'s own doc comment,
 * and this pull request's Deviations section, for why registering routes ahead of the screen
 * behind them is this project's own staging choice.
 */
class MainActivity : ComponentActivity() {

    private lateinit var database: IntervalTrainerDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SCHEMA-004: opened once here, the same way AppDatabase.open documents, so this activity
        // and WorkoutService never read two different database files.
        database = AppDatabase.open(applicationContext)
        val presetStore = RoomPresetStore(database.presetDao())
        setContent {
            AppTheme {
                IntervalTrainerNavHost(presetStore = presetStore)
            }
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
private const val ROUTE_SETTINGS = "settings"

/** `SCREEN-008`'s "new, empty preset": not a real preset id, so `PresetEditorScreen`'s own
 * `NavHost` entry below can tell the two cases — a new preset versus one already saved — apart. */
private const val NEW_PRESET_ID = "new"

private fun editorRoute(presetId: String) = "editor/$presetId"

/**
 * The navigation graph (`BUILD-018`): `home` is the start destination; `editor/{presetId}` is a
 * real screen (`PresetEditorScreen`, `#80`); `running` and `settings` are registered routes still
 * behind a [PlaceholderScreen], per `MainActivity`'s own doc comment.
 */
@Composable
private fun IntervalTrainerNavHost(presetStore: PresetStore) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
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
            PlaceholderScreen(label = "Running screen (#81)")
        }
        composable(ROUTE_SETTINGS) {
            PlaceholderScreen(label = "Settings")
        }
    }
}

/** `SVC-010`: this may promote the service to the foreground almost immediately (once the started
 * workout's timer reaches `Running`), so `ContextCompat.startForegroundService` is used rather
 * than plain `startService` — a no-op difference pre-`O`, and correct regardless of whether this
 * call happens to run while the app is foregrounded. */
private fun Context.startWorkoutService(presetId: String) {
    ContextCompat.startForegroundService(this, WorkoutService.startIntent(this, presetId))
}

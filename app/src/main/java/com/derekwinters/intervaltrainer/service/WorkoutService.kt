package com.derekwinters.intervaltrainer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.derekwinters.intervaltrainer.AppDatabase
import com.derekwinters.intervaltrainer.CueTimerState
import com.derekwinters.intervaltrainer.IntervalKind
import com.derekwinters.intervaltrainer.MainActivity
import com.derekwinters.intervaltrainer.PresetStore
import com.derekwinters.intervaltrainer.R
import com.derekwinters.intervaltrainer.TimerEvent
import com.derekwinters.intervaltrainer.TimerState
import com.derekwinters.intervaltrainer.database.IntervalTrainerDatabase
import com.derekwinters.intervaltrainer.database.RoomPresetStore
import com.derekwinters.intervaltrainer.formatSeconds
import com.derekwinters.intervaltrainer.notificationContent
import com.derekwinters.intervaltrainer.settings.DataStoreDefaultMuteStore
import com.derekwinters.intervaltrainer.settings.DefaultMuteStore
import com.derekwinters.intervaltrainer.toggleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The foreground service that owns the running workout (`docs/spec/service.md` `SVC-010`–`024`,
 * `SVC-040`–`042`; `docs/adr/0002-the-foreground-service-owns-the-running-workout.md`).
 *
 * It holds a [WorkoutSession] — `:core`'s reducer plus the preset lookup `SVC-014` describes — an
 * [AndroidCueSink] (`docs/spec/cues.md`, issue #130), a `PARTIAL_WAKE_LOCK` (`SVC-011`), and the
 * notification (`SVC-020`–`026`). Every command a screen or the notification sends arrives as an
 * `Intent` this service's own `onStartCommand` decodes into a [WorkoutCommand] and queues for
 * [WorkoutSession.handle] — the decoding is the only part of this class [WorkoutSessionTest] does
 * not already cover, and it is a single `when` over an `Intent`'s action string with no branching
 * worth a JVM test of its own.
 *
 * `SVC-017` (#147): a start that left no workout still reaches the foreground, then leaves it and
 * stops — see [leaveForegroundAfterStartWithNoWorkout].
 *
 * `SVC-015`–`016` (#145): `onStartCommand` runs on the main thread, and a start's preset lookup
 * must not, so [WorkoutSession.handle] suspends. Commands therefore go into [commands], and one
 * coroutine applies them one at a time, in arrival order — the scheduler's own tick included — so
 * a start waiting on its preset read is neither overtaken by the commands behind it nor run
 * alongside them.
 *
 * `docs/spec/screens.md` `SCREEN-061`: it also collects [DefaultMuteStore]'s own `Flow` into
 * [currentDefaultMuted], the value it passes to every [WorkoutSession.handle] call —
 * `reduceAndFireCues`'s own already-tested `TimerEvent.Start`-only branch (`CUE-051`–`053`,
 * `CueSelectionTest.kt`) is what keeps this from ever reaching into a workout already running,
 * not a second check written here.
 *
 * *(This class itself is manual: a foreground service, a wake lock and the Android notification
 * APIs are not reachable from a JVM runner, per ADR 0005 — see this pull request's body for
 * exactly what is and is not verified.)*
 */
class WorkoutService : Service() {

    private lateinit var database: IntervalTrainerDatabase
    private lateinit var presetStore: PresetStore
    private lateinit var session: WorkoutSession
    private lateinit var defaultMuteStore: DefaultMuteStore
    private lateinit var cueSink: AndroidCueSink

    private val clock = ElapsedRealtimeClock()
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    // SVC-016: every command, from onStartCommand and from the tick loop alike, goes through this
    // one queue, and only the consumer launched in onCreate ever calls applyCommand.
    private val commands = Channel<WorkoutCommand>(Channel.UNLIMITED)

    // SCREEN-061: the settings screen's own persisted default-mute value, cached from
    // defaultMuteStore's Flow rather than read fresh (suspending) on every command, so applying a
    // command never blocks on DataStore's own I/O. Read by applyCommand below for every command,
    // not only Start — :core's own reduceAndFireCues (CueSelection.kt) already ignores this
    // argument for every event but TimerEvent.Start (CUE-051–053), so passing it unconditionally
    // does not risk it reaching into an already-running workout.
    @Volatile
    private var currentDefaultMuted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        // SCHEMA-004: the real PresetStore a workout starts from, built the first time anything
        // in :app needs one (app/build.gradle.kts's own note on why nothing built this earlier).
        // AppDatabase.open is the one place :app builds this, so this service and the home screen
        // (SCREEN-002, #79) can never open two different database files.
        database = AppDatabase.open(applicationContext)
        presetStore = RoomPresetStore(database.presetDao())
        // CUE-001–002, #130: the real sink, built here so SoundPool has decoded the four assets
        // before the first cue is due, and released in onDestroy with everything else this
        // service owns.
        cueSink = AndroidCueSink(applicationContext)
        session = WorkoutSession(presetStore, clock, cueSink)
        defaultMuteStore = DataStoreDefaultMuteStore(applicationContext)
        // SCREEN-061: read once, synchronously, matching AppDatabase.open's own blocking-on-
        // onCreate convention just above — onStartCommand can process a Start command (the
        // common case: this service is usually created by starting a fresh workout) immediately
        // after this method returns, before an async collector's first emission would necessarily
        // have landed, and a stale `false` default would be silently wrong rather than loudly so.
        currentDefaultMuted = runBlocking { defaultMuteStore.defaultMuted.first() }
        serviceScope.launch {
            defaultMuteStore.defaultMuted.collect { currentDefaultMuted = it }
        }
        createNotificationChannel()
        serviceScope.launch {
            for (command in commands) applyCommand(command)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // SVC-015–016: queued, never applied here — this runs on the main thread, and a start's
        // preset lookup must not.
        intent?.toWorkoutCommand()?.let { commands.trySend(it) }
        // SVC-042: nothing here promises the service comes back after the platform kills the
        // process; START_NOT_STICKY makes no attempt to, rather than promising something this
        // specification explicitly says is outside the app's control.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickJob?.cancel()
        commands.close()
        serviceScope.cancel()
        releaseWakeLock()
        // A SoundPool holds the four decoded assets in native memory for as long as it lives, so
        // it is released here, where this service already gives up everything else it acquired.
        // Nothing fires a cue after this point: the tick loop is cancelled above and the session
        // is not reachable once the service is destroyed.
        if (::cueSink.isInitialized) cueSink.close()
        if (::database.isInitialized) database.close()
        super.onDestroy()
    }

    // Deliberately not overridden: Service's own onTaskRemoved() does nothing by default, and
    // android:stopWithTask is left unset in the manifest (the platform default, false) — between
    // the two, removing the task from recents does not stop this service (SVC-041).

    private suspend fun applyCommand(command: WorkoutCommand) {
        val wasEnded = session.state.timer is TimerState.Ended
        val next = session.handle(command, defaultMuted = currentDefaultMuted)
        WorkoutServiceState.publish(next)
        if (startLeftNoWorkout(command, next)) {
            leaveForegroundAfterStartWithNoWorkout()
            return
        }
        onStateChanged(next, wasEnded)
    }

    /**
     * `SVC-017` (#147): a start that left no workout — its preset is gone, or holds no intervals —
     * still has to reach the foreground, because this service was started with
     * `startForegroundService` and the platform ends the app if it never calls `startForeground`.
     * So it posts the minimal notification, removes it at once and stops, with no wake lock and no
     * tick. The published state stays idle; nothing navigates.
     */
    private fun leaveForegroundAfterStartWithNoWorkout() {
        startForegroundCompat(buildMinimalNotification())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onStateChanged(state: CueTimerState, wasEnded: Boolean) {
        when (val timer = state.timer) {
            is TimerState.Running -> {
                acquireWakeLock()
                startForegroundCompat(buildNotification(timer))
                startTickLoop()
            }

            is TimerState.Paused -> {
                postNotification(buildNotification(timer))
            }

            is TimerState.Ended -> if (!wasEnded) {
                // SVC-011, SVC-051: a confirmed stop, or the last interval running out, ends the
                // workout — release the wake lock and give up the foreground state.
                stopWorkoutForegroundState()
            }

            TimerState.Idle -> Unit
        }
    }

    private fun startTickLoop() {
        if (tickJob?.isActive == true) return
        tickJob = serviceScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MILLIS)
                commands.send(WorkoutCommand.Tick)
            }
        }
    }

    private fun stopWorkoutForegroundState() {
        tickJob?.cancel()
        tickJob = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- SVC-011: the wake lock -----------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:WorkoutWakeLock")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ---- SVC-010: startForeground, with and without the type overload ---------------------

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---- SVC-020–026: the notification -----------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.workout_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun postNotification(notification: Notification) {
        // SVC-031: a declined POST_NOTIFICATIONS permission must never affect the workout — only
        // whether it is visible. NotificationManagerCompat.notify() can throw SecurityException
        // when the permission is missing on some platform versions; this call is best-effort.
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Nothing to do: the workout itself is unaffected (SVC-031).
        }
    }

    /**
     * `SVC-020`–`026`: the notification's content, its pause/resume toggle and its skip action —
     * built from [state] via `:core`'s own pure [com.derekwinters.intervaltrainer.notificationContent]
     * and [com.derekwinters.intervaltrainer.toggleEvent], the same functions a future running
     * screen's own controls call (`SVC-023`).
     */
    private fun buildNotification(state: TimerState): Notification {
        val content = state.notificationContent(clock)
        val contentText = if (content != null) {
            getString(
                R.string.workout_notification_content,
                intervalKindLabel(content.intervalKind),
                formatSeconds((content.remainingMillis / 1_000L).toInt().coerceAtLeast(0)),
            )
        } else {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(true)
            // SVC-021: no stop action, exactly two — pause/resume and skip.
            .setContentIntent(openRunningScreenIntent())
            .addAction(toggleAction(state))
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_action_skip,
                    getString(R.string.workout_action_skip),
                    commandPendingIntent(ACTION_SKIP),
                ),
            )

        return builder.build()
    }

    /**
     * `SVC-017`: the notification a start that left no workout posts and removes at once — the
     * app's icon and name on the workout channel, with no status content (`SVC-020`) and no
     * actions (`SVC-021`), since there is no workout for either to describe.
     */
    private fun buildMinimalNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .build()

    /** SVC-026: the notification's one toggling control, decided by the same pure function a
     * running-screen control (SCREEN-031) would call. */
    private fun toggleAction(state: TimerState): NotificationCompat.Action = when (state.toggleEvent()) {
        TimerEvent.Pause -> NotificationCompat.Action(
            R.drawable.ic_action_pause,
            getString(R.string.workout_action_pause),
            commandPendingIntent(ACTION_PAUSE),
        )

        TimerEvent.Resume -> NotificationCompat.Action(
            R.drawable.ic_action_resume,
            getString(R.string.workout_action_resume),
            commandPendingIntent(ACTION_RESUME),
        )

        else -> NotificationCompat.Action(
            R.drawable.ic_action_pause,
            getString(R.string.workout_action_pause),
            commandPendingIntent(ACTION_PAUSE),
        )
    }

    private fun intervalKindLabel(kind: IntervalKind): String = getString(
        when (kind) {
            IntervalKind.WARM_UP -> R.string.interval_kind_warm_up
            IntervalKind.WORK -> R.string.interval_kind_work
            IntervalKind.RECOVERY -> R.string.interval_kind_recovery
            IntervalKind.COOL_DOWN -> R.string.interval_kind_cool_down
        },
    )

    private fun commandPendingIntent(action: String): PendingIntent {
        val intent = Intent(action, null, this, WorkoutService::class.java)
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * SVC-022: tapping the notification opens the running screen — whichever activity currently
     * hosts it. Until the running screen exists (issue #81), this opens [MainActivity], the app's
     * one activity today; [EXTRA_OPEN_RUNNING_WORKOUT] is carried for #81 to read once it adds
     * that destination, rather than this issue guessing at a navigation route that does not exist
     * yet — see this pull request's Deviations section.
     */
    private fun openRunningScreenIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_RUNNING_WORKOUT, true)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 1

        /** SVC-012: far inside `TIMER-070`'s five-second minimum interval, per its own doc comment. */
        private const val TICK_INTERVAL_MILLIS = 200L

        const val EXTRA_PRESET_ID = "com.derekwinters.intervaltrainer.extra.PRESET_ID"
        const val EXTRA_OPEN_RUNNING_WORKOUT = "com.derekwinters.intervaltrainer.extra.OPEN_RUNNING_WORKOUT"

        private const val ACTION_START = "com.derekwinters.intervaltrainer.action.START"
        private const val ACTION_PAUSE = "com.derekwinters.intervaltrainer.action.PAUSE"
        private const val ACTION_RESUME = "com.derekwinters.intervaltrainer.action.RESUME"
        private const val ACTION_SKIP = "com.derekwinters.intervaltrainer.action.SKIP"
        private const val ACTION_STOP = "com.derekwinters.intervaltrainer.action.STOP"
        private const val ACTION_TOGGLE_MUTE = "com.derekwinters.intervaltrainer.action.TOGGLE_MUTE"

        /** SVC-013: starts a workout from the preset [presetId] names. */
        fun startIntent(context: Context, presetId: String): Intent =
            Intent(ACTION_START, null, context, WorkoutService::class.java)
                .putExtra(EXTRA_PRESET_ID, presetId)

        fun pauseIntent(context: Context): Intent = Intent(ACTION_PAUSE, null, context, WorkoutService::class.java)
        fun resumeIntent(context: Context): Intent = Intent(ACTION_RESUME, null, context, WorkoutService::class.java)
        fun skipIntent(context: Context): Intent = Intent(ACTION_SKIP, null, context, WorkoutService::class.java)
        fun stopIntent(context: Context): Intent = Intent(ACTION_STOP, null, context, WorkoutService::class.java)
        fun toggleMuteIntent(context: Context): Intent =
            Intent(ACTION_TOGGLE_MUTE, null, context, WorkoutService::class.java)

        private fun Intent.toWorkoutCommand(): WorkoutCommand? = when (action) {
            ACTION_START -> getStringExtra(EXTRA_PRESET_ID)?.let { WorkoutCommand.Start(it) }
            ACTION_PAUSE -> WorkoutCommand.Pause
            ACTION_RESUME -> WorkoutCommand.Resume
            ACTION_SKIP -> WorkoutCommand.Skip
            ACTION_STOP -> WorkoutCommand.Stop
            ACTION_TOGGLE_MUTE -> WorkoutCommand.ToggleMute
            else -> null
        }
    }
}

/**
 * The active workout's app-wide observation channel (the invariant `docs/spec/service.md` carries
 * forward from `ADR 0002`: "observable from anywhere in the app... never established by or scoped
 * to the running screen"). It is a plain object rather than something bound to [WorkoutService]'s
 * own lifecycle, so a screen can read it whether or not the service happens to be running —
 * defaulting to [CueTimerState]'s own idle default when it never has been.
 */
object WorkoutServiceState {
    private val _current = MutableStateFlow(CueTimerState())

    val current: StateFlow<CueTimerState> = _current.asStateFlow()

    internal fun publish(state: CueTimerState) {
        _current.value = state
    }
}

# Research — Foreground service requirements for a timer on Android 8 to 15

**Question (issue #25):** what does Android require of a foreground service that keeps an interval
timer running and cueing on time — short tones and vibration — with the screen off, on API 26
through 35?

**Sources checked:** 2026-09-10. Primary sources only: `developer.android.com` (guides, behaviour
change pages, API reference) and AOSP platform source. `source.android.com` and
`android.googlesource.com` are both unreachable from the research environment, so AOSP source was
read from the official AOSP GitHub mirror `aosp-mirror/platform_frameworks_base`, `main` branch, on
the date above. Where a mirror line is cited the file and the surrounding logic are quoted so the
claim can be re-checked against `cs.android.com`.

---

## Answer

**Service type.** Targeting Android 14+ (API 34+) every foreground service must declare a
`foregroundServiceType`, and calling `startForeground()` without one throws
`MissingForegroundServiceTypeException`. For a timer whose only background output is short tones and
vibration, **`mediaPlayback`** is the fitting type: it is documented for "continue audio or video
playback from the background", it needs only the manifest permission
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and it has **no runtime prerequisite** — nothing to request from
the user. `health` also describes this app category ("exercise trackers") but carries a real runtime
prerequisite (a sensor permission or `HIGH_SAMPLING_RATE_SENSORS`) that a timer has no reason to
hold; `shortService` caps out at about three minutes; `systemExempted` is not open to us;
`specialUse` works but is the residual bucket and needs a `<property>` justification.

**What Android 15 restricts about `mediaPlayback`.** One thing only, for apps targeting 15: a
`BOOT_COMPLETED` receiver may not start it (`ForegroundServiceStartNotAllowedException`). A timer
does not start itself at boot, so this does not bite. `mediaPlayback` is **not** subject to the
Android 15 six-hour timeout — that applies to `dataSync` and `mediaProcessing` only.

**`POST_NOTIFICATIONS`.** It is not required to *run* a foreground service, and denial does not stop
the service: the user simply does not see the notification in the shade (it still appears in the
Task Manager). Practically, though, a timer whose notification is invisible is a timer the user
cannot see or stop from the shade, so request it. From Android 14 the ongoing notification is
user-dismissible regardless.

**Timing.** A foreground service does **not** by itself keep the CPU awake. It does, however, buy the
one thing that matters in Doze: AOSP ignores partial wake locks during device idle *except* for
allowlisted UIDs and UIDs whose process state is at least `BOUND_FOREGROUND_SERVICE` — a running
foreground service qualifies. So **foreground service + `PARTIAL_WAKE_LOCK` + an in-process
scheduler on `SystemClock.elapsedRealtime()` is the mechanism for sub-second interval boundaries.**
Exact alarms are the wrong tool here and cannot substitute: `setExactAndAllowWhileIdle()` is
explicitly quota-limited (reference doc: "not more than about every minute", "such as 15 minutes"
when idle; AOSP default quota 72 dispatches per hour), which is coarser than a 20-second work
interval. Reserve `AlarmManager` for a coarse backstop (workout end, a long rest) if wanted at all;
if used, targeting API 31+ it needs `SCHEDULE_EXACT_ALARM` (denied by default from Android 14) —
`USE_EXACT_ALARM` is auto-granted but documented for calendar and alarm-clock apps, which this is
not.

**Start rules.** Start the service from the visible activity (`startForegroundService()`), and call
`startForeground()` within **five seconds** or the process is killed
(`ForegroundServiceDidNotStartInTimeException` on API 31+). Targeting Android 12+, the service may
not be started while the app is in the background, and "transitioning from a user-visible state such
as an activity" is the exemption we rely on. Destroying the activity does not stop a started
service. Swiping the task away does not stop it either unless `android:stopWithTask="true"` — but
the Task Manager **Stop** button kills the whole app with no callback.

**Battery-optimisation exemption.** Not needed for this design, and best not requested: the
foreground service already keeps the wake lock alive in Doze and keeps the app in the *active*
standby bucket. It is only worth asking for if the design later depends on unthrottled
allow-while-idle alarms. If it is ever needed: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the
manifest plus `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with a `package:` URI, checked
with `PowerManager.isIgnoringBatteryOptimizations()`.

**One finding not asked for but load-bearing:** AOSP drops vibrations requested by a UID it considers
background unless the vibration's usage is in an allowlist. Cue vibrations must therefore be tagged
`VibrationAttributes.USAGE_ALARM` (also the allowlist that survives battery saver), not the default
usage.

---

## 1. The `foregroundServiceType` declaration (Android 14+)

**The requirement.** "If an app that targets Android 14 doesn't define types for a given service in
the manifest, then the system will raise `MissingForegroundServiceTypeException` upon calling
`startForeground()`". Declaring a type without its type-specific permission gives a
`SecurityException`; failing a type's runtime prerequisites also gives a `SecurityException` after
`startForeground()`, which "prevents the foreground service from starting, might cause a running
foreground service to be removed from the foreground process state, and might cause your app to
crash". Passing a type to `startForeground()` that is not declared in the manifest throws
`IllegalArgumentException`.
Sources: <https://developer.android.com/about/versions/14/changes/fgs-types-required>,
<https://developer.android.com/develop/background-work/services/fgs/launch>

**Candidates, from the type table**
(<https://developer.android.com/develop/background-work/services/fgs/service-types>):

| Type | Manifest permission | Runtime prerequisite | Documented use case |
|---|---|---|---|
| `mediaPlayback` | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | **None** | "Continue audio or video playback from the background. Support Digital Video Recording (DVR) functionality on Android TV." |
| `health` | `FOREGROUND_SERVICE_HEALTH` | **Yes** — declare `HIGH_SAMPLING_RATE_SENSORS`, or be granted one of `BODY_SENSORS` (API ≤35), `READ_HEART_RATE`, `READ_SKIN_TEMPERATURE`, `READ_OXYGEN_SATURATION`, `ACTIVITY_RECOGNITION` | "Any long-running use cases to support apps in the fitness category such as exercise trackers." |
| `specialUse` | `FOREGROUND_SERVICE_SPECIAL_USE` | None, but requires a `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" …>` explanation | "Covers any valid foreground service use cases that aren't covered by the other foreground service types." |
| `shortService` | none (only `FOREGROUND_SERVICE`) | None | "Quickly finish critical work that cannot be interrupted or postponed" — limited to about 3 minutes, no sticky restart, cannot start other foreground services |
| `systemExempted` | `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Device owner / profile owner / `ROLE_EMERGENCY` / device admin / VPN / demo mode / holder of `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` | "Reserved for system applications and specific system integrations." |

**Reading.** `mediaPlayback` is the only candidate that both describes what the service actually does
in the background (emit audio) and asks nothing of the user. `health` is the closest *category*
match, but its prerequisite forces the app to hold a sensor permission it does not otherwise want;
that is a design decision for the owner, not a research finding (see Open questions).
`specialUse`'s Play Console justification requirement is documented but irrelevant to a sideloaded
build — the `<property>` element, however, is a manifest requirement and would still apply.

**Runtime call.** Types are passed as flags:
`ServiceCompat.startForeground(this, id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`
(<https://developer.android.com/about/versions/14/changes/fgs-types-required>).

### What Android 15 restricts

- **`BOOT_COMPLETED` may not launch it.** For apps targeting Android 15, a `BOOT_COMPLETED` receiver
  cannot start `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` or `microphone`
  foreground services: "If a `BOOT_COMPLETED` receiver tries to launch any of those types of
  foreground services, the system throws `ForegroundServiceStartNotAllowedException`."
  <https://developer.android.com/about/versions/15/behavior-changes-15>
- **`SYSTEM_ALERT_WINDOW` no longer suffices on its own** to start an FGS from the background: the
  app "needs to first launch a `TYPE_APPLICATION_OVERLAY` window *and* the window needs to be
  visible". Same page. Irrelevant to a timer started from its own UI.
- **No timeout for `mediaPlayback`.** The six-hour-per-24-hours cap with the `Service.onTimeout()`
  callback and the `RemoteServiceException` on failure to stop applies to `dataSync` and
  `mediaProcessing`: "Currently, this restriction only applies to `dataSync` and `mediaProcessing`
  foreground service type foreground services."
  <https://developer.android.com/develop/background-work/services/fgs/timeout>,
  <https://developer.android.com/about/versions/15/behavior-changes-15>

### Before API 34

Types are only *required* for apps targeting Android 14+. On API 26–33 devices the same manifest is
harmless. The type system did not exist before API 29; API 26–33 devices need `FOREGROUND_SERVICE`
(API 28+) and a notification channel (API 26+).

---

## 2. `POST_NOTIFICATIONS` on Android 13+

- The permission is a runtime permission covering "non-exempt (including Foreground Services (FGS))
  notifications".
- **The service still runs without it.** "Apps don't need to request the `POST_NOTIFICATIONS`
  permission in order to launch a foreground service. However, apps must include a notification when
  they start a foreground service, just as they do on previous versions of Android."
- **What is lost is visibility.** "On Android 13 (API level 33) or higher, if the user denies the
  notification permission, they still see notices related to foreground services in the Task Manager
  but don't see them in the notification drawer."
- Exemptions from the permission exist for media-session notifications and `CallStyle` call
  notifications; a plain FGS notification is not exempt from being hidden.

Sources: <https://developer.android.com/develop/ui/views/notifications/notification-permission>,
<https://developer.android.com/about/versions/13/changes/notification-permission>

**Android 14 — the notification is dismissible.** "If your app shows non-dismissable foreground
notifications to users, Android 14 has changed the behavior to allow users to dismiss such
notifications… The behavior of `FLAG_ONGOING_EVENT` has changed to make such notifications actually
dismissable by the user." It stays non-dismissible when the phone is locked and is not cleared by
**Clear all**. Dismissing the notification does not stop the service; only the Task Manager **Stop**
button does (section 4).
<https://developer.android.com/about/versions/14/behavior-changes-all>

**Notification appearance delay.** By default "the system can choose to defer visibility of the
notification for a short time after the service is started";
`Notification.Builder.setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` (API 31+)
"guarantee[s] that visibility is never deferred". For a timer the notification is the user's control
surface, so immediate is the right choice.
<https://developer.android.com/reference/android/app/Notification.Builder#setForegroundServiceBehavior(int)>

---

## 3. Doze, wake locks, and whether exact alarms are needed

### What Doze does

While in Doze the system "suspends network access", **"ignores wake locks"**, "defers standard
`AlarmManager` alarms, including `setExact()` and `setWindow()`", stops sync adapters and
`JobScheduler` (and therefore `WorkManager`), and does not perform Wi-Fi scans. Maintenance windows
run pending work periodically and become less frequent over time.
<https://developer.android.com/training/monitoring-device-state/doze-standby>

### The exemption that makes a foreground service work

The training page's "ignores wake locks" is the device-wide statement; the platform's actual rule is
narrower. In `PowerManagerService.setWakeLockDisabledStateLocked()`, a `PARTIAL_WAKE_LOCK` held by an
application UID is disabled during idle **only if** the UID is absent from both the device-idle
allowlist and the temp allowlist **and** its process state is worse than
`PROCESS_STATE_BOUND_FOREGROUND_SERVICE`:

```java
if (doesIdleStateBlockWakeLocksLocked()) {
    // If we are in idle mode, we will also ignore all partial wake locks that are
    // for application uids that are not allowlisted.
    final UidState state = wakeLock.mUidState;
    if (Arrays.binarySearch(mDeviceIdleWhitelist, appid) < 0 &&
            Arrays.binarySearch(mDeviceIdleTempWhitelist, appid) < 0 &&
            state.mProcState != ActivityManager.PROCESS_STATE_NONEXISTENT &&
            state.mProcState > ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
        disabled = true;
    }
}
```

`ProcessStateEnum.aidl` orders these values `TOP = 2`, `BOUND_TOP = 3`, `FOREGROUND_SERVICE = 4`,
`BOUND_FOREGROUND_SERVICE = 5`, `IMPORTANT_FOREGROUND = 6` — lower is more important — so an app
hosting a running foreground service (procstate 4) is **not** in the disabled set, and its partial
wake lock keeps working through Doze. `doesIdleStateBlockWakeLocksLocked()` covers full device idle
and, when the corresponding flag is on, light idle.

Sources (AOSP `main`, read 2026-09-10 via the AOSP GitHub mirror):
<https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/power/PowerManagerService.java>
(`setWakeLockDisabledStateLocked`, `doesIdleStateBlockWakeLocksLocked`),
<https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/ProcessStateEnum.aidl>

### A foreground service does not hold a wake lock for you

The "keep the device awake" guide poses the question directly — "Would it be detrimental to the user
experience if the device suspends while the foreground service is running and the device screen is
off?" — and only if the answer is yes "you might need to use a wake lock". A timer that must cue on
time with the screen off is exactly that yes. `PARTIAL_WAKE_LOCK` "[e]nsures that the CPU is running;
the screen and keyboard backlight will be allowed to go off."
Sources: <https://developer.android.com/develop/background-work/background-tasks/awake>,
<https://developer.android.com/reference/android/os/PowerManager#PARTIAL_WAKE_LOCK>

### Why exact alarms cannot carry interval boundaries

- The alarm guide states plainly that alarms are the wrong mechanism for in-app timing: "For timing
  operations that are guaranteed to occur **during** the lifetime of your application, consider using
  the `Handler` class in conjunction with `Timer` and `Thread`."
  <https://developer.android.com/develop/background-work/services/alarms/schedule>
- `setExactAndAllowWhileIdle()` is throttled by design: "there are restrictions on how frequently
  these alarms will go off for a particular application. Under normal system operation, it will not
  dispatch these alarms more than about every minute…; when in low-power idle modes this duration may
  be significantly longer, such as 15 minutes." It also grants the app a ~10-second temporary power
  exemption when it fires.
  <https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent)>
- The Doze training page gives an older figure for the same restriction: "Neither
  `setAndAllowWhileIdle()` nor `setExactAndAllowWhileIdle()` can fire alarms more than once per nine
  minutes, per app." <https://developer.android.com/training/monitoring-device-state/doze-standby>
- The platform's current defaults are a quota, not a fixed spacing:
  `DEFAULT_ALLOW_WHILE_IDLE_QUOTA = 72` per `DEFAULT_ALLOW_WHILE_IDLE_WINDOW = 60 * 60 * 1000` (one
  hour) for modern callers, and `DEFAULT_ALLOW_WHILE_IDLE_COMPAT_QUOTA = 7` per hour for legacy ones.
  <https://github.com/aosp-mirror/platform_frameworks_base/blob/main/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java>
- Any of those numbers rules out one alarm per interval boundary: a 30-minute session of 30s work /
  15s rest is 80 boundaries.

`setAlarmClock()` is not throttled the same way — "these alarms will be allowed to trigger even if
the system is in a low-power idle (a.k.a. doze) mode… the system never adjusts their delivery time" —
but it is documented as "extremely expensive on battery" and is one visible alarm-clock event, not a
scheduling primitive to fire 80 times.
<https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)>

### Conclusion for the timer

Hold a `PARTIAL_WAKE_LOCK` for the duration of a running session inside the foreground service, and
schedule boundaries in-process against `SystemClock.elapsedRealtime()` using absolute boundary
timestamps computed from the session start (so a late wake-up does not accumulate drift). No
`AlarmManager` is needed for boundaries. Note the platform makes **no documented guarantee** of
sub-second dispatch accuracy for any in-process mechanism — see Open questions.

### App Standby

An app "is in the active bucket while it is used… [or] runs a long running foreground service", so a
running session keeps the app out of standby throttling; the restricted bucket's one-alarm-per-day
limit is not reachable while the service runs.
<https://developer.android.com/topic/performance/appstandby>

### Vibration with the screen off

`VibrationSettings.shouldIgnoreVibration()` returns `IGNORED_BACKGROUND` when the calling UID is not
foreground and the vibration's usage is outside `BACKGROUND_PROCESS_USAGE_ALLOWLIST`
(`USAGE_RINGTONE`, `USAGE_ALARM`, `USAGE_NOTIFICATION`, `USAGE_COMMUNICATION_REQUEST`,
`USAGE_HARDWARE_FEEDBACK`, `USAGE_PHYSICAL_EMULATION`). A separate `BATTERY_SAVER_USAGE_ALLOWLIST`
(ringtone, alarm, communication request, physical emulation, hardware feedback) governs battery-saver
mode. Cue vibrations should therefore carry `VibrationAttributes.USAGE_ALARM` ("Usage value to use
for alarm vibrations"), which is in both lists.
Sources:
<https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/vibrator/VibrationSettings.java>,
<https://developer.android.com/reference/android/os/VibrationAttributes#USAGE_ALARM>

---

## 4. Start rules, the `startForeground` deadline, task removal, activity destruction

### Starting from the foreground activity

Apps targeting Android 12+ "can't start foreground services while the app is running in the
background, except for a few special cases. If an app tries to start a foreground service while the
app runs in the background, and the foreground service doesn't satisfy one of the exceptional cases,
the system throws a `ForegroundServiceStartNotAllowedException`." The first listed exemption is the
one a timer uses: "Your app transitions from a user-visible state, such as an activity." Other
exemptions of note: "Your app invokes an exact alarm to complete an action that the user requests",
and "The user turns off battery optimizations for your app."
Sources: <https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start>,
<https://developer.android.com/about/versions/12/behavior-changes-12>

`Context.startForegroundService()` itself "can be used at any time, regardless of whether the app
hosting the service is in a foreground state", but throws
`ForegroundServiceStartNotAllowedException` for callers targeting API 31+ that are background
restricted.
<https://developer.android.com/reference/android/content/Context#startForegroundService(android.content.Intent)>

### The deadline to call `startForeground()`

- API reference: the service "will call `startForeground(int, android.app.Notification)` once it
  begins running. The service is given an amount of time comparable to the ANR interval to do this,
  otherwise the system will automatically crash the process, in which case an internal exception
  `ForegroundServiceDidNotStartInTimeException` is logged on logcat on devices running SDK Version
  `Build.VERSION_CODES.S` or later. On older Android versions, an internal exception
  `RemoteServiceException` is logged instead."
  <https://developer.android.com/reference/android/content/Context#startForegroundService(android.content.Intent)>
- The Android 8.0 page gives the number: "After the system has created the service, the app has five
  seconds to call the service's `startForeground()` method… If the app does not call
  `startForeground()` within the time limit, the system stops the service and declares the app to be
  ANR." <https://developer.android.com/about/versions/oreo/background>

### Activity destroyed

A started service's lifetime is independent of the activity that started it — it runs until it stops
itself or is stopped — and background execution limits explicitly do "not apply to foreground
services, which are more noticeable to the user."
<https://developer.android.com/about/versions/oreo/background>,
<https://developer.android.com/guide/components/services>

### Task removal (swipe from Recents)

- `Service.onTaskRemoved(Intent)` "is called if the service is currently running and the user has
  removed a task that comes from the service's application. If you have set
  `ServiceInfo.FLAG_STOP_WITH_TASK` then you will not receive this callback; instead, the service
  will simply be stopped."
  <https://developer.android.com/reference/android/app/Service#onTaskRemoved(android.content.Intent)>
- The manifest attribute behind that flag: "`android:stopWithTask` — If set to `"true"`, the system
  automatically stops the service when the user removes a task that is rooted in an activity that the
  app owns. The default value is `"false"`."
  <https://developer.android.com/guide/topics/manifest/service-element>
- So by default a running session survives a swipe-away, and the app decides in `onTaskRemoved()`
  whether that is what it wants.

### Task Manager "Stop" (Android 13+)

When the user presses **Stop** next to the app in the Task Manager: "the system removes your app from
memory. Therefore, your entire app stops, not just the running foreground service"; the activity back
stack is removed, media playback stops, the FGS notification is removed. "The system doesn't send
your app any callbacks when the user taps the Stop button." Scheduled jobs and alarms still fire at
their scheduled times, and a restart can detect `ApplicationExitInfo.REASON_USER_REQUESTED`. Testable
with `adb shell cmd activity stop-app PACKAGE_NAME`.
<https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping>

---

## 5. Exact-alarm permissions, if alarms are used at all

Even as a backstop, exact alarms carry permission cost:

- **Android 12 (API 31):** apps targeting 31+ "need to request the `SCHEDULE_EXACT_ALARM` permission"
  to use `setExactAndAllowWhileIdle()` — "unless the app is exempt from battery restrictions" — and
  to use `setAlarmClock()`. "The user and the system can revoke this permission via the special app
  access screen in Settings."
  <https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent)>,
  <https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)>,
  <https://developer.android.com/about/versions/12/behavior-changes-12>
- **Android 14 (API 34):** "the `SCHEDULE_EXACT_ALARM` permission is no longer being pre-granted to
  most newly installed apps targeting Android 13 and higher — the permission is denied by default."
  Pre-granted only on upgrade if already held, for `SYSTEM_WELLBEING` role holders, platform-signed
  apps, privileged apps, and "apps that are on the power allowlist".
  <https://developer.android.com/about/versions/14/changes/schedule-exact-alarms>
- **Handling:** call `AlarmManager.canScheduleExactAlarms()` before scheduling; send the user to
  `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` otherwise; listen for
  `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` and reschedule, because "when the
  `SCHEDULE_EXACT_ALARM` permission is revoked for your app, your app stops, and all future exact
  alarms are canceled."
  <https://developer.android.com/develop/background-work/services/alarms/schedule>
- **`USE_EXACT_ALARM`** is a normal permission granted at install and not revocable, but it is scoped:
  "Calendar or alarm clock apps need to send calendar reminders, wake-up alarms, or alerts when the
  app is no longer running. These apps can request the `USE_EXACT_ALARM` normal permission." An
  interval trainer is neither, and holding it also makes the app eligible for `systemExempted`, which
  is a signal of how the platform reads it.
  <https://developer.android.com/about/versions/14/changes/schedule-exact-alarms>,
  <https://developer.android.com/develop/background-work/services/fgs/service-types>

---

## 6. Battery-optimisation exemption

**Is it needed here?** No, on the evidence above: the wake lock survives Doze because of the
foreground service's process state (section 3), and the app sits in the *active* standby bucket while
the service runs (section 3). The exemption buys three things this design does not need:

1. Unthrottled allow-while-idle alarms. AOSP sets `FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED` — "we will
   allow these alarms to go off as normal even while idle, with no timing restrictions" — for core
   system UIDs and for UIDs the user has exempted (`isUidPowerSaveUserExempt`).
   <https://github.com/aosp-mirror/platform_frameworks_base/blob/main/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java>
2. Exemption from needing `SCHEDULE_EXACT_ALARM` at all ("apps that are on the power allowlist").
   <https://developer.android.com/about/versions/14/changes/schedule-exact-alarms>
3. An additional exemption from the Android 12 background-FGS-start restriction ("The user turns off
   battery optimizations for your app").
   <https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start>

**How it is requested, if it ever is.** "Ask the user to allow an app to ignore battery optimizations…
For an app to use this, it also must hold the `Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
permission… The Intent's data URI must specify the application package name to be shown, with the
`package` scheme. That is `package:com.my.app`." Status is read with
`PowerManager.isIgnoringBatteryOptimizations()`. `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`
merely opens the list. The same reference warns: "most applications should not use this… This is only
for unusual applications that need to deeply control their own execution, at the potential expense of
the user's battery life."
<https://developer.android.com/reference/android/provider/Settings#ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS>,
<https://developer.android.com/reference/android/provider/Settings#ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS>

What the exemption does *not* do, per the Doze guide: "An app that is partially exempt can use the
network and hold partial wake locks during Doze and App Standby. However, other restrictions still
apply to the app… its regular `AlarmManager` alarms don't fire."
<https://developer.android.com/training/monitoring-device-state/doze-standby>

(Play policy on this exemption exists and is quoted on the same page, but the app is sideloaded, so
it is out of scope by the issue's own terms.)

---

## Open questions

Primary sources do not settle these; they need a decision by the owner or an experiment on a device.

1. **Sub-second accuracy is not documented anywhere as a guarantee.** The platform documents *that*
   partial wake locks keep the CPU running and *that* the foreground service process state exempts
   them during Doze, but no page states a bound on scheduling jitter for an in-process timer with the
   screen off. This has to be measured (log intended vs actual boundary times over a 30-minute
   screen-off session on a physical device, unplugged and stationary so Doze actually engages). It is
   also the kind of thing OEM power managers alter — no primary source covers OEM behaviour at all.
2. **`mediaPlayback` vs `health`.** Both are defensible. `mediaPlayback` matches the mechanism (audio
   from the background) and costs nothing; `health` matches the category the docs name ("exercise
   trackers") but requires holding a sensor permission — likely `ACTIVITY_RECOGNITION` — that the app
   has no other use for. Which trade the project wants is a design decision, not a documented answer.
3. **Whether the tone playback itself needs a `MediaSession` or an active audio stream.** No page in
   the API 34/35 documentation states a runtime prerequisite for `mediaPlayback` ("Runtime
   prerequisites: None"). Whether a later platform version tightens this — and how a device behaves
   if `mediaPlayback` is declared but audio only plays intermittently — was not established from
   primary sources.
4. **Interaction with audio focus and Do Not Disturb** for short cue tones (ducking other playback,
   audibility under DND) was outside the issue's scope and is not covered here.
5. **The allow-while-idle throttle figure disagrees across primary sources** — "once per nine
   minutes" (Doze guide), "about every minute… such as 15 minutes" when idle (AlarmManager
   reference), 72 per hour (AOSP default constant). All three are primary; they were written at
   different times. Since the design does not depend on allow-while-idle alarms, this was not
   resolved.
6. **`shortService` and boot-start behaviour** were checked only far enough to rule them out for this
   design; nothing here covers resuming a session after a reboot.

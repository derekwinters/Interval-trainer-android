package com.derekwinters.intervaltrainer.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.derekwinters.intervaltrainer.designsystem.AppTheme
import com.derekwinters.intervaltrainer.designsystem.ListLayout
import com.derekwinters.intervaltrainer.designsystem.StockListItem
import com.derekwinters.intervaltrainer.designsystem.Toggle

/**
 * The settings screen (`docs/spec/screens.md` §5, `SCREEN-060`–`063`): reached from home's
 * trailing header action (`SCREEN-001`, `SCREEN-060`), it shows exactly two rows —
 * `SCREEN-061`'s default-mute switch and `SCREEN-063`'s notification-permission row — and nothing
 * else, per that section's own "not specified" paragraph.
 *
 * Every value here is read, never written, the same split every other screen in this app makes
 * (`ADR 0005`): [defaultMuted] and [notificationPermissionGranted] are this screen's own inputs,
 * and [onDefaultMutedChange]/[onOpenNotificationSettings] are its own outputs. In particular this
 * composable does not itself read `DefaultMuteStore` (`SCREEN-080`) or call
 * `NotificationManagerCompat.areNotificationsEnabled()` — its caller (`MainActivity.kt`) does
 * both, the same "a screen shows only what it is handed" split `SummaryScreen` already follows for
 * `WorkoutServiceState`.
 *
 * `SCREEN-061`'s own invariant — toggling the default changes only the value a new workout starts
 * from, never a workout already running — holds structurally, not because this screen or its
 * caller re-checks anything: [onDefaultMutedChange] only ever reaches `DefaultMuteStore.setDefaultMuted`
 * (`MainActivity.kt`), which `WorkoutSession.handle`'s `defaultMuted` parameter consults only for a
 * fresh `TimerEvent.Start` (`docs/spec/service.md` `SVC-014`, `:core`'s own `reduceAndFireCues`,
 * `CUE-051`–`053`) — nothing on this screen, or on the path from it, ever reaches into
 * `WorkoutServiceState`'s current workout.
 *
 * `SCREEN-063`: [notificationPermissionGranted] decides both the row's own supporting text
 * ("Granted"/"Denied") and whether it responds to a tap at all — granted, the row is present but
 * inert (`StockListItem` is not given a `clickable` modifier); denied, tapping it calls
 * [onOpenNotificationSettings] (`SVC-033`'s deep link to the system's own notification-settings
 * page for this app, built at the call site from [com.derekwinters.intervaltrainer.settings.notificationSettingsDeepLink]).
 * A `Switch` is not used for this row — unlike [defaultMuted], this is not a value the app owns
 * and can flip on tap.
 */
@Composable
fun SettingsScreen(
    defaultMuted: Boolean,
    onDefaultMutedChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListLayout(
        title = "Settings",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            StockListItem(
                headline = "Default mute",
                supportingText = "The mute a new workout starts from",
                trailingContent = {
                    Toggle(
                        checked = defaultMuted,
                        onCheckedChange = onDefaultMutedChange,
                        contentDescription = "Default mute",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val permissionRowModifier = Modifier.fillMaxWidth().let { base ->
                // SCREEN-063: inert once granted — nothing left to do from here.
                if (notificationPermissionGranted) base else base.clickable(onClick = onOpenNotificationSettings)
            }
            StockListItem(
                headline = "Notification permission",
                supportingText = if (notificationPermissionGranted) "Granted" else "Denied",
                modifier = permissionRowModifier,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenGrantedPreview() {
    AppTheme {
        SettingsScreen(
            defaultMuted = false,
            onDefaultMutedChange = {},
            notificationPermissionGranted = true,
            onOpenNotificationSettings = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDeniedPreview() {
    AppTheme {
        SettingsScreen(
            defaultMuted = true,
            onDefaultMutedChange = {},
            notificationPermissionGranted = false,
            onOpenNotificationSettings = {},
            onBack = {},
        )
    }
}

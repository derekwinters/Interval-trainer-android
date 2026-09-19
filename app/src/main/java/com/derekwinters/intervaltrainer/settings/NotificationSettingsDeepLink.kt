package com.derekwinters.intervaltrainer.settings

/**
 * `docs/spec/service.md` `SVC-033`: the app's own page in the system's notification settings —
 * where the settings screen's notification-permission row
 * (`docs/spec/screens.md` `SCREEN-063`) sends the user when the permission is denied, since
 * Android gives an app no way to re-trigger its own system permission dialog once denied.
 *
 * `Settings.ACTION_APP_NOTIFICATION_SETTINGS` and `Settings.EXTRA_APP_PACKAGE`
 * (`android.provider.Settings`) are both API 26+ — the same level this app's own `minSdk` already
 * floors at (`docs/spec/build.md` `BUILD-010`) — so there is no older-platform fallback to branch
 * on here, unlike `SVC-033`'s own "or... where the platform version offers one" hedge, written
 * before `minSdk` was fixed at 26. [action] and [extraKey] are the literal string values those two
 * constants hold, not the constants themselves, so this one piece of real decision-making — which
 * action and which extra key open the *notification* settings page rather than the app's general
 * details page — is a plain Kotlin function a JVM test can call directly with no `android.*` on
 * its classpath, per `ADR 0005`. The one real `android.content.Intent` built from this is
 * assembled at the one call site that needs it (`MainActivity.kt`'s settings route), which is not
 * JVM-testable.
 */
data class NotificationSettingsDeepLink(
    val action: String,
    val extraKey: String,
    val packageName: String,
)

fun notificationSettingsDeepLink(packageName: String): NotificationSettingsDeepLink =
    NotificationSettingsDeepLink(
        action = "android.settings.APP_NOTIFICATION_SETTINGS",
        extraKey = "android.provider.extra.APP_PACKAGE",
        packageName = packageName,
    )

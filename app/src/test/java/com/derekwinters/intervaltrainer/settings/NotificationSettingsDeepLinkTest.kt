package com.derekwinters.intervaltrainer.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `docs/spec/service.md` `SVC-033`: the deep link the settings screen's notification-permission
 * row opens when the permission is denied.
 */
class NotificationSettingsDeepLinkTest {

    @Test
    fun `deep link targets the app's own page in the system notification settings`() {
        val spec = notificationSettingsDeepLink("com.derekwinters.intervaltrainer")

        assertEquals("android.settings.APP_NOTIFICATION_SETTINGS", spec.action)
        assertEquals("android.provider.extra.APP_PACKAGE", spec.extraKey)
        assertEquals("com.derekwinters.intervaltrainer", spec.packageName)
    }
}

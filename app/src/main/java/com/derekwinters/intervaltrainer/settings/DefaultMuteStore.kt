package com.derekwinters.intervaltrainer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * `docs/spec/screens.md` `SCREEN-061`, `SCREEN-080`: where the default-mute value — the mute a
 * new workout's effective mute starts from (`CUE-051`) — is read and written. This is `:app`'s own
 * seam, the same role `PresetStore` plays for `:core`'s `Preset` (`SCHEMA-004`), except this one
 * has no reason to live in `:core` itself: default mute is not a domain concept `:core`'s pure
 * reducer ever consults directly (`WorkoutSession.handle`'s own `defaultMuted` parameter is a
 * plain `Boolean` a caller already resolved, per `docs/spec/service.md` `SVC-014`), so the
 * interface sits beside its one real implementation and its one real caller, both in `:app`.
 *
 * [defaultMuted] is a [Flow] rather than a single synchronous read because the settings screen
 * observes it live (`SCREEN-061`'s switch reflects the stored value, and reacts to it changing —
 * there is only one write path in v1, the switch itself, but nothing here assumes that stays
 * true). [WorkoutService] does not collect this `Flow` per command; it caches the latest value a
 * running collector observed, per its own doc comment, so applying a command never blocks on
 * storage I/O.
 */
interface DefaultMuteStore {
    val defaultMuted: Flow<Boolean>

    suspend fun setDefaultMuted(value: Boolean)
}

/**
 * The real [DefaultMuteStore], backed by Jetpack DataStore Preferences (`SCREEN-080`):
 * `context.settingsDataStore`, a single named preferences store this app's only other
 * persistence, `:database`'s Room store (`SCHEMA-001`), never touches — so the two never contend
 * for the same file and neither needs the other's migration story.
 *
 * This class is Android-only (`Context`, `Context.preferencesDataStore`) and is not unit-tested on
 * the JVM here, per ADR 0005: it has no branching logic of its own to isolate — it is a single
 * key read through a single, already-tested DataStore API — and the surrounding classes that would
 * exercise it (`WorkoutService`, `SettingsScreen`'s own caller in `MainActivity.kt`) are
 * themselves manual, exactly as every other Android-only class in this codebase already is.
 */
class DataStoreDefaultMuteStore(context: Context) : DefaultMuteStore {

    private val store: DataStore<Preferences> = context.settingsDataStore

    override val defaultMuted: Flow<Boolean> =
        store.data.map { preferences -> preferences[KEY_DEFAULT_MUTED] ?: false }

    override suspend fun setDefaultMuted(value: Boolean) {
        store.edit { preferences -> preferences[KEY_DEFAULT_MUTED] = value }
    }

    private companion object {
        val KEY_DEFAULT_MUTED = booleanPreferencesKey("default_muted")
    }
}

private const val SETTINGS_DATASTORE_NAME = "settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

package com.derekwinters.intervaltrainer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * `docs/spec/screens.md` `SCREEN-070`, `SCREEN-080`: where the first-run-seen flag — whether the
 * first-run explanation (§6) has ever been shown — is read and written. The same shape
 * [DefaultMuteStore] already gives its own value: an interface `:app`'s own seam, with one real
 * implementation backed by Jetpack DataStore Preferences.
 *
 * [firstRunSeen] is a [Flow] for the same reason [DefaultMuteStore.defaultMuted] is: nothing here
 * requires it, since v1 has exactly one reader (`MainActivity.onCreate`'s own
 * `runBlocking { firstRunStore.firstRunSeen.first() }`, the NavHost start-destination computation
 * needing its value synchronously before the first frame — the same convention
 * `WorkoutService.onCreate`'s own blocking read of [DefaultMuteStore.defaultMuted] already
 * establishes) and exactly one writer (the first-run screen's own primary action) — but a `Flow`
 * is what every other DataStore-backed seam in this app already exposes, and there is no reason
 * for this one alone to read differently.
 */
interface FirstRunStore {
    val firstRunSeen: Flow<Boolean>

    suspend fun setFirstRunSeen(value: Boolean)
}

/**
 * The real [FirstRunStore], backed by the same [settingsDataStore] file [DataStoreDefaultMuteStore]
 * already reads and writes (`SCREEN-080`) — a second key in the one store, not a second file.
 *
 * Android-only (`Context`) and not unit-tested on the JVM here, per ADR 0005, for the same reason
 * [DataStoreDefaultMuteStore] is not: it is a single key read through a single, already-tested
 * DataStore API, with no branching logic of its own to isolate. The one piece of logic this
 * store's *value* feeds into that does branch — which of three screens the app starts on — is
 * pulled out into [com.derekwinters.intervaltrainer.startDestination]
 * (`com/derekwinters/intervaltrainer/StartDestination.kt`) instead, and tested there.
 */
class DataStoreFirstRunStore(context: Context) : FirstRunStore {

    private val store: DataStore<Preferences> = context.settingsDataStore

    override val firstRunSeen: Flow<Boolean> =
        store.data.map { preferences -> preferences[KEY_FIRST_RUN_SEEN] ?: false }

    override suspend fun setFirstRunSeen(value: Boolean) {
        store.edit { preferences -> preferences[KEY_FIRST_RUN_SEEN] = value }
    }

    private companion object {
        val KEY_FIRST_RUN_SEEN = booleanPreferencesKey("first_run_seen")
    }
}

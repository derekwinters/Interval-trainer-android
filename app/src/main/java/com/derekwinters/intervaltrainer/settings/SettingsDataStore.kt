package com.derekwinters.intervaltrainer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * `docs/spec/screens.md` `SCREEN-080`: the one named Jetpack DataStore Preferences store this app
 * keeps outside `:database`'s Room schema (`SCHEMA-001`) — [DataStoreDefaultMuteStore]'s default
 * mute (`SCREEN-061`) and [DataStoreFirstRunStore]'s first-run-seen flag (`SCREEN-070`) both read
 * and write it, as two keys in the one file, not two separate DataStore files.
 *
 * Factored out into its own file in this pull request (`#84`) rather than left as
 * `DefaultMuteStore.kt`'s own private property: DataStore's `preferencesDataStore` delegate
 * guards against more than one active `DataStore` for the same underlying file in a process — "a
 * single named preferences store... `:database`'s own Room store never touches" (`SCREEN-080`) is
 * a guarantee about *files*, not about how many Kotlin properties may name one, and two
 * independently declared `by preferencesDataStore(name = "settings")` delegates, one per store
 * class, would be exactly the two-properties-one-file shape DataStore itself rejects at runtime.
 * One property, reused by both stores, is what actually keeps this a single store rather than two
 * that happen to share a name.
 */
internal const val SETTINGS_DATASTORE_NAME = "settings"

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

package com.robcloud.bloodpressure

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits once on collection and again whenever one of [keys] changes (or any key, if [keys] is
 * empty) — including a change made through a different [SharedPreferences] instance on the same
 * file, such as a background worker's. Lets a live observer (a ViewModel) react to a write it
 * didn't make itself, without polling or waiting for its own next read.
 *
 * A `null` key (Android delivers one for [SharedPreferences.Editor.clear], and may for a bulk
 * write) always counts as a match, since there's no key to filter on.
 */
fun SharedPreferences.changes(vararg keys: String): Flow<Unit> = callbackFlow {
    val relevant = keys.toSet()
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (relevant.isEmpty() || key == null || key in relevant) trySend(Unit)
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(Unit)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}

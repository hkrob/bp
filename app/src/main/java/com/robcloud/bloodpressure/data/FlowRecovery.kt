package com.robcloud.bloodpressure.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

private const val BAD_ROW_RETRY_DELAY_MS = 5_000L

/**
 * Keeps a Room query [Flow] alive across a row it can't read, instead of letting Room's enum
 * converters ([Converters.fromArmName], [Converters.fromNoteTypeName]) end it for good on the
 * first one — an out-of-band edit, or a database restored from an older or newer build. Without
 * this, the very next invalidation (any insert/update/delete on the table, not just to the bad
 * row) re-runs the same query and throws again, so an unguarded collector of a Room flow — a
 * `StateFlow`, or a `combine` — gets stuck, or crashes outright if nothing downstream catches it.
 *
 * Emits [fallback] right away so a `combine`d or `stateIn`'d collector isn't left waiting on this
 * source, reports the failure once via [onError] — not on every retry — and keeps retrying so it
 * recovers on its own once the offending row is fixed (an edit, a restore, a future build that
 * understands it). [onError] fires again after a later, distinct failure, once a retry in between
 * has gone back to emitting real values.
 */
fun <T> Flow<T>.recoveringFromBadRows(fallback: T, onError: (Throwable) -> Unit): Flow<T> {
    var notified = false
    return onEach { notified = false }
        .retryWhen { cause, _ ->
            emit(fallback)
            if (!notified) {
                onError(cause)
                notified = true
            }
            delay(BAD_ROW_RETRY_DELAY_MS)
            true
        }
}

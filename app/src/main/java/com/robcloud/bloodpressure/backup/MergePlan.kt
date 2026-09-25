package com.robcloud.bloodpressure.backup

/**
 * Result of merging the backup file into the local database: [toInsert] are rows only the file
 * has (and that weren't deleted here), [merged] is the full set to write back to the file.
 */
data class MergePlan<T>(val toInsert: List<T>, val merged: List<T>)

/**
 * Local rows always win and are never rewritten — the database is the source of truth, and
 * rewriting it from a snapshot taken earlier would undo any edit or delete made in the meantime.
 * Tombstoned ids are dropped from the file so deletions propagate instead of resurrecting.
 */
fun <T> planMerge(local: List<T>, remote: List<T>, tombstones: Set<String>, id: (T) -> String): MergePlan<T> {
    val localIds = local.mapTo(HashSet(), id)
    val remoteOnly = remote
        .filter { id(it) !in localIds && id(it) !in tombstones }
        .distinctBy(id)
    return MergePlan(toInsert = remoteOnly, merged = local + remoteOnly)
}

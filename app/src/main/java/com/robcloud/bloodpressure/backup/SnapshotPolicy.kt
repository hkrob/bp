package com.robcloud.bloodpressure.backup

import java.time.LocalDate
import java.time.YearMonth

private const val SNAPSHOT_PREFIX = "readings-"
private const val SNAPSHOT_SUFFIX = ".csv"

/** Daily copy of readings.csv, e.g. `readings-2026-09-25.csv`. */
fun snapshotName(date: LocalDate): String = "$SNAPSHOT_PREFIX$date$SNAPSHOT_SUFFIX"

fun parseSnapshotDate(name: String): LocalDate? {
    if (!name.startsWith(SNAPSHOT_PREFIX) || !name.endsWith(SNAPSHOT_SUFFIX)) return null
    return runCatching { LocalDate.parse(name.removePrefix(SNAPSHOT_PREFIX).removeSuffix(SNAPSHOT_SUFFIX)) }.getOrNull()
}

/**
 * Which snapshot files to delete: keeps every snapshot from the last [keepDays] days, plus the
 * earliest snapshot of each of the last [keepMonths] calendar months (so there is always an
 * older copy to fall back to). Names that aren't snapshots are never touched.
 */
fun snapshotsToDelete(
    names: List<String>,
    today: LocalDate,
    keepDays: Int = 14,
    keepMonths: Int = 12
): List<String> {
    val dated = names.mapNotNull { name -> parseSnapshotDate(name)?.let { name to it } }
    val recentCutoff = today.minusDays(keepDays.toLong() - 1)
    val oldestMonth = YearMonth.from(today).minusMonths(keepMonths.toLong() - 1)
    val monthlyKeepers = dated
        .filter { (_, date) -> !YearMonth.from(date).isBefore(oldestMonth) }
        .groupBy { (_, date) -> YearMonth.from(date) }
        .values
        .map { inMonth -> inMonth.minBy { it.second }.first }
        .toSet()
    return dated
        .filter { (name, date) -> date.isBefore(recentCutoff) && name !in monthlyKeepers }
        .map { it.first }
}

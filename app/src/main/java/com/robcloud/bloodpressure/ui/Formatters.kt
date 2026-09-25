package com.robcloud.bloodpressure.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Display formatting for stored instants. The zone is resolved on every call rather than baked
 * into the formatters, so times stay consistent with the pickers (which also read the current
 * zone) after the phone's time zone changes while the app is running.
 */
object Formatters {
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** Compact "d MMM, h:mm a" — no year, for dense one-line-per-reading layouts. */
    private val dateTimeNoYearFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a")
    private val dateTimeShortYearFormatter = DateTimeFormatter.ofPattern("d MMM yy, h:mm a")

    private fun local(instant: Instant): ZonedDateTime = instant.atZone(ZoneId.systemDefault())

    fun dateTime(instant: Instant): String = dateTimeFormatter.format(local(instant))
    fun date(instant: Instant): String = dateFormatter.format(local(instant))
    fun time(instant: Instant): String = timeFormatter.format(local(instant))

    /** Omits the year for this year's readings; older ones get a two-digit year so rows stay unambiguous. */
    fun dateTimeCompact(instant: Instant): String {
        val zoned = local(instant)
        val formatter = if (zoned.year == ZonedDateTime.now(zoned.zone).year) {
            dateTimeNoYearFormatter
        } else {
            dateTimeShortYearFormatter
        }
        return formatter.format(zoned)
    }
}

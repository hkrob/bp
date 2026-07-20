package com.robcloud.bloodpressure.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Formatters {
    private val dateTimeFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

    private val dateFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    private val timeFormatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

    /** Compact "d MMM, h:mm a" — no year, for dense one-line-per-reading layouts. */
    private val dateTimeNoYearFormatter = DateTimeFormatter
        .ofPattern("d MMM, h:mm a")
        .withZone(ZoneId.systemDefault())

    fun dateTime(instant: Instant): String = dateTimeFormatter.format(instant)
    fun dateTimeNoYear(instant: Instant): String = dateTimeNoYearFormatter.format(instant)
    fun date(instant: Instant): String = dateFormatter.format(instant)
    fun time(instant: Instant): String = timeFormatter.format(instant)
}

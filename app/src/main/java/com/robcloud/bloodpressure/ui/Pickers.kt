package com.robcloud.bloodpressure.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Readings and notes can't be in the future; the date pickers stop at today. */
private fun DatePickerDialog.capAtToday(): DatePickerDialog = apply {
    datePicker.maxDate = System.currentTimeMillis()
}

fun showDatePicker(context: Context, current: Instant, onPicked: (Instant) -> Unit) {
    val zoned = current.atZone(ZoneId.systemDefault())
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(zoned.with(LocalDate.of(year, month + 1, dayOfMonth)).toInstant())
        },
        zoned.year,
        zoned.monthValue - 1,
        zoned.dayOfMonth
    ).capAtToday().show()
}

fun showTimePicker(context: Context, current: Instant, onPicked: (Instant) -> Unit) {
    val zoned = current.atZone(ZoneId.systemDefault())
    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val updated = zoned.withHour(hourOfDay).withMinute(minute).withSecond(0).withNano(0)
            onPicked(updated.toInstant())
        },
        zoned.hour,
        zoned.minute,
        DateFormat.is24HourFormat(context)
    ).show()
}

fun showDatePickerFor(context: Context, current: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(LocalDate.of(year, month + 1, dayOfMonth)) },
        current.year,
        current.monthValue - 1,
        current.dayOfMonth
    ).capAtToday().show()
}

fun showTimePickerFor(context: Context, hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hourOfDay, pickedMinute -> onPicked(hourOfDay, pickedMinute) },
        hour,
        minute,
        DateFormat.is24HourFormat(context)
    ).show()
}

/** Highest plausible readings — also drive the capture form's auto-advance (see CaptureScreen). */
const val SYSTOLIC_MAX = 260
const val DIASTOLIC_MAX = 150

/**
 * True once [value] can't gain another digit and still be a plausible reading, so the capture
 * form should jump focus to the next field. Two digits are enough whenever a third would
 * overshoot [maxValid] — diastolic tops out at 150, so "76" can only ever become 760+ and is
 * clearly finished, while "15" is left alone in case the user is typing 150. Three digits always
 * advances. (Waiting for three digits everywhere was the old behaviour: it never fired for the
 * usual two-digit diastolic, so heart rate had to be selected by hand.)
 */
fun isFieldComplete(value: String, maxValid: Int): Boolean {
    val digits = value.filter { it.isDigit() }
    val entered = digits.toIntOrNull() ?: return false
    return digits.length >= 3 || (digits.length == 2 && entered * 10 > maxValid)
}

/** Shared validation for reading fields; returns an error message or null if valid. */
fun validateReading(systolic: Int?, diastolic: Int?, heartRate: Int?): String? = when {
    systolic == null || systolic !in 60..SYSTOLIC_MAX -> "Enter a valid systolic reading (60-260 mmHg)"
    diastolic == null || diastolic !in 40..DIASTOLIC_MAX -> "Enter a valid diastolic reading (40-150 mmHg)"
    // Systolic below diastolic is physiologically impossible — always a swapped or mistyped entry.
    systolic <= diastolic -> "Systolic should be higher than diastolic — check the values"
    heartRate == null || heartRate !in 30..220 -> "Enter a valid heart rate (30-220 bpm)"
    else -> null
}

/** A little slack for clock skew and the minute-granular time picker. */
private val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)

/** Error message if [takenAt] is in the future, else null. */
fun validateTakenAt(takenAt: Instant, now: Instant = Instant.now()): String? =
    if (takenAt.isAfter(now.plus(FUTURE_TOLERANCE))) "That date and time is in the future — check it" else null

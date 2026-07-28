package com.robcloud.bloodpressure.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

fun showDatePicker(context: Context, current: Instant, onPicked: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val zoned = current.atZone(zone)
    val calendar = Calendar.getInstance().apply {
        set(zoned.year, zoned.monthValue - 1, zoned.dayOfMonth)
    }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val updated = zoned.withYear(year).withMonth(month + 1).withDayOfMonth(dayOfMonth)
            onPicked(updated.toInstant())
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun showTimePicker(context: Context, current: Instant, onPicked: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val zoned = current.atZone(zone)
    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val updated = zoned.withHour(hourOfDay).withMinute(minute).withSecond(0).withNano(0)
            onPicked(updated.toInstant())
        },
        zoned.hour,
        zoned.minute,
        false
    ).show()
}

fun showDatePickerFor(context: Context, current: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(LocalDate.of(year, month + 1, dayOfMonth)) },
        current.year,
        current.monthValue - 1,
        current.dayOfMonth
    ).show()
}

fun showTimePickerFor(context: Context, hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hourOfDay, pickedMinute -> onPicked(hourOfDay, pickedMinute) },
        hour,
        minute,
        false
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

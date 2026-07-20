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

/** Shared validation for reading fields; returns an error message or null if valid. */
fun validateReading(systolic: Int?, diastolic: Int?, heartRate: Int?): String? = when {
    systolic == null || systolic !in 60..260 -> "Enter a valid systolic reading (60-260 mmHg)"
    diastolic == null || diastolic !in 40..150 -> "Enter a valid diastolic reading (40-150 mmHg)"
    // Systolic below diastolic is physiologically impossible — always a swapped or mistyped entry.
    systolic <= diastolic -> "Systolic should be higher than diastolic — check the values"
    heartRate == null || heartRate !in 30..220 -> "Enter a valid heart rate (30-220 bpm)"
    else -> null
}

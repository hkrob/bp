package com.robcloud.bloodpressure.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.robcloud.bloodpressure.data.Note
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.ui.theme.LocalChartColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val axisDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * X axis unit is local days (epoch day + fraction of day), not epoch seconds — the chart
 * then spans the whole selected period without scrolling, and multiple readings on the
 * same day keep distinct positions. Rounded to 4 decimals (~9 s) because Vico rejects
 * x-values more precise than four decimal places.
 */
private fun xValue(instant: Instant, zone: ZoneId): Double {
    val zoned = instant.atZone(zone)
    val raw = zoned.toLocalDate().toEpochDay() + zoned.toLocalTime().toSecondOfDay() / 86_400.0
    return Math.round(raw * 10_000.0) / 10_000.0
}

private val noteChipDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/**
 * For each note date, returns (x, y) placing a dot on the systolic line: x is the note date at
 * start of day, y is the systolic value linearly interpolated between the surrounding readings
 * (clamped to the nearest reading when the date is outside the readings' range). Sorted x is
 * required so Vico accepts the series. `sortedX`/`sysY` come from the systolic series, ascending.
 */
private fun noteMarkerPoints(
    sortedX: List<Double>,
    sysY: List<Double>,
    noteDates: List<LocalDate>
): List<Pair<Double, Double>> {
    if (sortedX.isEmpty()) return emptyList()
    return noteDates.map { date ->
        val nx = date.toEpochDay().toDouble()
        val y = interpolateAt(nx, sortedX, sysY)
        nx to y
    }.sortedBy { it.first }
}

/** Linear interpolation of y at x over the ascending (xs, ys) samples; clamps outside the range. */
private fun interpolateAt(x: Double, xs: List<Double>, ys: List<Double>): Double {
    if (x <= xs.first()) return ys.first()
    if (x >= xs.last()) return ys.last()
    val hi = xs.indexOfFirst { it >= x }
    val lo = hi - 1
    val span = xs[hi] - xs[lo]
    if (span == 0.0) return ys[hi]
    val t = (x - xs[lo]) / span
    return ys[lo] + t * (ys[hi] - ys[lo])
}

@Composable
fun ReadingsChart(readings: List<Reading>, notes: List<Note> = emptyList(), modifier: Modifier = Modifier) {
    val sorted = remember(readings) { readings.sortedBy { it.takenAt } }
    val modelProducer = remember { CartesianChartModelProducer() }
    val zone = remember { ZoneId.systemDefault() }

    val noteDates = remember(notes) { notes.map { it.date }.distinct().sorted() }

    LaunchedEffect(sorted, noteDates) {
        if (sorted.isEmpty()) return@LaunchedEffect
        val x = sorted.map { xValue(it.takenAt, zone) }
        val sys = sorted.map { it.systolicMmHg.toDouble() }
        val markers = noteMarkerPoints(x, sys, noteDates)
        modelProducer.runTransaction {
            lineModel {
                series(x, sys)
                series(x, sorted.map { it.diastolicMmHg })
                series(x, sorted.map { it.heartRateBpm })
                if (markers.isNotEmpty()) series(markers.map { it.first }, markers.map { it.second })
            }
        }
    }

    if (sorted.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(220.dp)) {
            Text(
                "No readings in this period yet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    val chartColors = LocalChartColors.current
    val lineColors = listOf(chartColors.systolic, chartColors.diastolic, chartColors.heartRate)
    val noteColor = MaterialTheme.colorScheme.tertiary
    val hasNoteMarkers = noteDates.isNotEmpty()

    // Data lines (systolic/diastolic/heart rate).
    val dataLines = lineColors.map { color ->
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(color))
        )
    }
    // A 4th, points-only line whose dots sit on the systolic line at each note date.
    // Vico draws points during the line layer's own draw pass — unlike the marker API
    // (CartesianMarker), which never composited onto this chart. The connecting line is
    // hidden with a transparent fill so only the dots show.
    val noteMarkerLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                rememberShapeComponent(fill = Fill(noteColor), shape = CircleShape),
                12.dp
            )
        )
    )
    val allLines = if (hasNoteMarkers) dataLines + noteMarkerLine else dataLines

    Column(modifier = modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(allLines)
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberAxisLabelComponent(
                        style = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberAxisLabelComponent(
                        style = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                    ),
                    valueFormatter = CartesianValueFormatter { _, value, _ ->
                        axisDateFormatter.format(LocalDate.ofEpochDay(value.toLong()))
                    }
                )
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            scrollState = rememberVicoScrollState(scrollEnabled = false)
        )
        ChartLegend(lineColors)
        if (noteDates.isNotEmpty()) {
            NoteDatesRow(noteDates, noteColor)
        }
    }
}

@Composable
private fun ChartLegend(colors: List<Color>) {
    val labels = listOf("Systolic", "Diastolic", "Heart rate")
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        labels.forEachIndexed { index, label ->
            LegendEntry(colors[index], label)
        }
    }
}

/**
 * The dots on the systolic line mark note dates; this row spells out which dates they are.
 * (The on-chart dots come from a points-only line series — see [ReadingsChart] — since Vico's
 * CartesianMarker API never composited onto this chart.)
 */
@Composable
private fun NoteDatesRow(dates: List<LocalDate>, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            "Note" + (if (dates.size > 1) "s" else "") + " on " +
                dates.joinToString(", ") { noteChipDateFormatter.format(it) },
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

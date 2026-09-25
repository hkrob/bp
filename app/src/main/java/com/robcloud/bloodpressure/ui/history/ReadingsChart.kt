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

@Composable
fun ReadingsChart(readings: List<Reading>, modifier: Modifier = Modifier) {
    val sorted = remember(readings) { readings.sortedBy { it.takenAt } }
    val modelProducer = remember { CartesianChartModelProducer() }
    val zone = remember { ZoneId.systemDefault() }

    LaunchedEffect(sorted) {
        if (sorted.isEmpty()) return@LaunchedEffect
        val x = sorted.map { xValue(it.takenAt, zone) }
        modelProducer.runTransaction {
            lineModel {
                series(x, sorted.map { it.systolicMmHg.toDouble() })
                series(x, sorted.map { it.diastolicMmHg })
                series(x, sorted.map { it.heartRateBpm })
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

    // A line needs two points; with a single reading in the period, draw it as a dot instead of
    // leaving the chart looking empty.
    val singleReading = sorted.size == 1
    val dataLines = lineColors.map { color ->
        val dot = rememberShapeComponent(Fill(color), CircleShape)
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(color)),
            pointProvider = if (singleReading) {
                LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(dot, 8.dp))
            } else {
                null
            }
        )
    }

    Column(modifier = modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(dataLines)
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

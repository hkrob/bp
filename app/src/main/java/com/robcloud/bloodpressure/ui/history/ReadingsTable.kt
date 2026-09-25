package com.robcloud.bloodpressure.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.data.averageOf
import com.robcloud.bloodpressure.data.bpCategory
import com.robcloud.bloodpressure.data.groupIntoSittings
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.ui.theme.StatusElevated
import com.robcloud.bloodpressure.ui.theme.StatusHigh
import com.robcloud.bloodpressure.ui.theme.StatusLow
import com.robcloud.bloodpressure.ui.theme.StatusNormal

fun categoryColor(category: BpCategory) = when (category) {
    BpCategory.LOW -> StatusLow
    BpCategory.NORMAL -> StatusNormal
    BpCategory.ELEVATED, BpCategory.STAGE_1 -> StatusElevated
    BpCategory.STAGE_2, BpCategory.CRISIS -> StatusHigh
}

fun readingStatusColor(reading: Reading) = categoryColor(reading.bpCategory())

/** Marks a reading the monitor flagged as having an irregular heartbeat. */
const val IRREGULAR_MARK = "IRR"

/**
 * Newest first. Readings taken together (see [groupIntoSittings]) get a header row with the
 * sitting's average above them; a reading on its own is just a row.
 */
@Composable
fun ReadingsTable(
    readings: List<Reading>,
    onRowClick: (Reading) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {}
) {
    val sittings = remember(readings) { groupIntoSittings(readings) }
    LazyColumn(modifier = modifier) {
        item(key = "summary") { header() }
        item(key = "table-header") { TableHeaderRow() }
        sittings.forEach { sitting ->
            val grouped = sitting.size > 1
            if (grouped) {
                item(key = "sitting-${sitting.first().id}") { SittingHeaderRow(sitting) }
            }
            items(sitting, key = { it.id }) { reading ->
                TableRow(reading, indented = grouped, onClick = { onRowClick(reading) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("When", modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.labelLarge)
        Text("BP", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
        Text("HR", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SittingHeaderRow(sitting: List<Reading>) {
    val avg = averageOf(sitting) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "Sitting of ${sitting.size}, average",
            modifier = Modifier.weight(1.6f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            "${avg.systolic}/${avg.diastolic}",
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.labelLarge,
            color = categoryColor(avg.category),
            maxLines = 1
        )
        Text(
            "${avg.heartRate}",
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TableRow(reading: Reading, indented: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Only the date is indented, so the BP and HR columns stay aligned with every other row.
        Text(
            Formatters.dateTimeCompact(reading.takenAt),
            modifier = Modifier
                .weight(1.6f)
                .padding(start = if (indented) 12.dp else 0.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
        Text(
            "${reading.systolicMmHg}/${reading.diastolicMmHg}",
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = readingStatusColor(reading),
            maxLines = 1
        )
        Row(modifier = Modifier.weight(0.6f)) {
            Text("${reading.heartRateBpm}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (reading.irregularHeartbeat) {
                Text(
                    " $IRREGULAR_MARK",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
            }
        }
    }
}

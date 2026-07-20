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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robcloud.bloodpressure.data.BpCategory
import com.robcloud.bloodpressure.data.Reading
import com.robcloud.bloodpressure.data.bpCategory
import com.robcloud.bloodpressure.ui.Formatters
import com.robcloud.bloodpressure.ui.theme.StatusElevated
import com.robcloud.bloodpressure.ui.theme.StatusHigh
import com.robcloud.bloodpressure.ui.theme.StatusNormal

fun categoryColor(category: BpCategory) = when (category) {
    BpCategory.NORMAL -> StatusNormal
    BpCategory.ELEVATED, BpCategory.STAGE_1 -> StatusElevated
    BpCategory.STAGE_2, BpCategory.CRISIS -> StatusHigh
}

fun readingStatusColor(reading: Reading) = categoryColor(reading.bpCategory())

@Composable
fun ReadingsTable(
    readings: List<Reading>,
    onRowClick: (Reading) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item { TableHeaderRow() }
        items(readings, key = { it.id }) { reading ->
            TableRow(reading, onClick = { onRowClick(reading) })
            HorizontalDivider()
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
private fun TableRow(reading: Reading, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            Formatters.dateTimeNoYear(reading.takenAt),
            modifier = Modifier.weight(1.6f),
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
        Text(
            "${reading.heartRateBpm}",
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

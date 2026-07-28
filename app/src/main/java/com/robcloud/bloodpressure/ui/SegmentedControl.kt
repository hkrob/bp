package com.robcloud.bloodpressure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Equal-width single-choice segmented control.
 *
 * Replaces Material3's `SingleChoiceSegmentedButtonRow`, which cannot be made to fill a given
 * width: its own modifier chain ends with `width(IntrinsicSize.Min)`, applied *inside* whatever
 * the caller passes, so it always sizes to `itemCount × widest label`. On a narrow screen that
 * overflows — the longest option wraps onto two lines and grows while the others stay narrow —
 * and it only looks correct when the container happens to be wide (e.g. landscape). Adding
 * `Modifier.weight(1f)` to the buttons does nothing, because `SegmentedButton` already applies
 * `weight(1f)` internally.
 *
 * A plain Row with `weight(1f)` per cell is equal-width by construction at any container width,
 * font scale or orientation; a label too long for its cell ellipsizes instead of wrapping.
 */
@Composable
fun <T> EqualWidthSegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium
) {
    val corner = 20.dp
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .selectableGroup(),
        // Pull cells together by one pixel so adjacent borders render as a single shared line.
        horizontalArrangement = Arrangement.spacedBy((-1).dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val shape = when {
                options.size == 1 -> RoundedCornerShape(corner)
                index == 0 -> RoundedCornerShape(topStart = corner, bottomStart = corner)
                index == options.lastIndex -> RoundedCornerShape(topEnd = corner, bottomEnd = corner)
                else -> RectangleShape
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Keep the selected cell's border above its neighbours' shared edges.
                    .zIndex(if (isSelected) 1f else 0f)
                    .clip(shape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    )
                    .border(1.dp, outline, shape)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) }
                    )
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = label(option),
                    style = textStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

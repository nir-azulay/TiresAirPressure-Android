package com.example.tiresairpressuremanagement.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: androidx.compose.foundation.layout.Arrangement.Horizontal = androidx.compose.foundation.layout.Arrangement.Start,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        
        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            if (currentRowWidth + placeable.width > constraints.maxWidth) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }
        
        val height = rows.sumOf { row ->
            row.maxOf { it.height }
        } + (rows.size - 1) * 8.dp.roundToPx()
        
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                val rowWidth = row.sumOf { it.width }
                val spacing = when (horizontalArrangement) {
                    androidx.compose.foundation.layout.Arrangement.SpaceEvenly -> {
                        (constraints.maxWidth - rowWidth) / (row.size + 1)
                    }
                    androidx.compose.foundation.layout.Arrangement.SpaceBetween -> {
                        if (row.size > 1) (constraints.maxWidth - rowWidth) / (row.size - 1) else 0
                    }
                    else -> 0
                }
                
                var x = when (horizontalArrangement) {
                    androidx.compose.foundation.layout.Arrangement.Center -> {
                        (constraints.maxWidth - rowWidth - (row.size - 1) * spacing) / 2
                    }
                    androidx.compose.foundation.layout.Arrangement.SpaceEvenly -> spacing
                    else -> 0
                }
                
                row.forEach { placeable ->
                    placeable.place(x, y)
                    x += placeable.width + spacing
                }
                y += row.maxOf { it.height } + 8.dp.roundToPx()
            }
        }
    }
} 
package com.personalizeprolabs.odolog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.MonthCost

// The charts are drawn with plain Compose (boxes and a Canvas); there is no chart library.

/**
 * Stacked bars: cost per month, split into fuel + charging (bottom) and service + other
 * (top). Tapping a bar shows the exact amount of that month.
 */
@Composable
fun MonthlyCostChart(months: List<MonthCost>, modifier: Modifier = Modifier) {
    val fmt = LocalFmt.current
    val energyColor = MaterialTheme.colorScheme.primary
    val otherColor = AccentAmber
    var selected by remember(months) { mutableIntStateOf(months.lastIndex) }
    val max = months.maxOfOrNull { it.total } ?: 0.0

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val current = months.getOrNull(selected)
        Text(
            text = if (current != null) {
                fmt.monthYear(current.year, current.month) + ": " + fmt.money(current.total)
            } else {
                ""
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
            months.forEachIndexed { index, month ->
                val energyPart = if (max > 0.0) (month.energyCost / max).toFloat() else 0f
                val otherPart = if (max > 0.0) (month.otherCost / max).toFloat() else 0f
                val emptyPart = 1f - energyPart - otherPart
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { selected = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    // Column weights share the height: empty space, then the two bar parts.
                    if (emptyPart > 0.001f) Spacer(Modifier.weight(emptyPart))
                    if (otherPart > 0.001f) {
                        Box(Modifier.weight(otherPart).fillMaxWidth(0.62f).background(otherColor))
                    }
                    if (energyPart > 0.001f) {
                        Box(Modifier.weight(energyPart).fillMaxWidth(0.62f).background(energyColor))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            months.forEachIndexed { index, month ->
                Text(
                    text = fmt.monthLetter(month.month),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(energyColor)
            Text(
                text = stringResource(R.string.chart_legend_energy),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp, end = 16.dp),
            )
            LegendDot(otherColor)
            Text(
                text = stringResource(R.string.stat_service_other),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(10.dp).background(color))
}

/**
 * A simple line through [values] (oldest first), with the highest and the lowest value
 * written next to it.
 */
@Composable
fun TrendChart(values: List<Double>, highLabel: String, lowLabel: String, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0

    Row(modifier = modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxHeight().width(56.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = highLabel, style = MaterialTheme.typography.labelSmall)
            Text(text = lowLabel, style = MaterialTheme.typography.labelSmall)
        }
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val pad = 6.dp.toPx()
            val width = size.width - 2 * pad
            val height = size.height - 2 * pad
            val count = values.size

            fun x(index: Int): Float = if (count <= 1) pad + width / 2f else pad + width * index / (count - 1)
            fun y(value: Double): Float =
                if (max <= min) pad + height / 2f else pad + (height * (1.0 - (value - min) / (max - min))).toFloat()

            drawLine(gridColor, Offset(0f, pad), Offset(size.width, pad), strokeWidth = 1.dp.toPx())
            drawLine(
                gridColor,
                Offset(0f, size.height - pad),
                Offset(size.width, size.height - pad),
                strokeWidth = 1.dp.toPx(),
            )
            for (i in 1 until count) {
                drawLine(
                    color = lineColor,
                    start = Offset(x(i - 1), y(values[i - 1])),
                    end = Offset(x(i), y(values[i])),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            for (i in 0 until count) {
                drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x(i), y(values[i])))
            }
        }
    }
}

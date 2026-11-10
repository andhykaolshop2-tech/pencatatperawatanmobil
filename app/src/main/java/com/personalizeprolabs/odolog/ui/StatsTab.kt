@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalizeprolabs.odolog.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.EconomyInterval
import com.personalizeprolabs.odolog.core.EnergyStats
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.MonthCost
import com.personalizeprolabs.odolog.core.PeriodSummary
import com.personalizeprolabs.odolog.core.Stats
import java.time.LocalDate

private const val PERIOD_12_MONTHS = 0
private const val PERIOD_THIS_YEAR = 1
private const val PERIOD_ALL = 2

/** How many fill-ups / charges the trend chart shows at most. */
private const val TREND_POINTS = 24

/** Totals, cost per distance, consumption and two simple charts. */
@Composable
fun StatsTab(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    val fmt = LocalFmt.current
    var period by rememberSaveable { mutableStateOf(PERIOD_12_MONTHS) }
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val from: Long? = when (period) {
        PERIOD_12_MONTHS -> today.minusMonths(12).plusDays(1).toEpochDay()
        PERIOD_THIS_YEAR -> today.withDayOfYear(1).toEpochDay()
        else -> null
    }
    val summary = remember(entries, from) { Stats.summarize(entries, from, null) }
    val months = remember(entries, todayDay) { Stats.monthlyCosts(entries, todayDay, 12) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = period == PERIOD_12_MONTHS,
                onClick = { period = PERIOD_12_MONTHS },
                label = { Text(stringResource(R.string.period_12_months)) },
            )
            FilterChip(
                selected = period == PERIOD_THIS_YEAR,
                onClick = { period = PERIOD_THIS_YEAR },
                label = { Text(stringResource(R.string.period_this_year)) },
            )
            FilterChip(
                selected = period == PERIOD_ALL,
                onClick = { period = PERIOD_ALL },
                label = { Text(stringResource(R.string.period_all)) },
            )
        }

        if (entries.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.stats_empty_title),
                text = stringResource(R.string.stats_empty_text),
            )
        } else {
            StatsCards(summary = summary, months = months)
        }
    }
}

/** The cards of the statistics tab; they are placed directly into the tab's column. */
@Composable
private fun StatsCards(summary: PeriodSummary, months: List<MonthCost>) {
    val fmt = LocalFmt.current
    val cardModifier = Modifier.padding(horizontal = 16.dp)

    SectionCard(title = stringResource(R.string.stats_costs), modifier = cardModifier) {
        StatRow(stringResource(R.string.stat_total_cost), fmt.money(summary.totalCost), emphasized = true)
        if (summary.fuelCost > 0.0 || summary.fuelLiters > 0.0) {
            StatRow(stringResource(R.string.stat_fuel), fmt.money(summary.fuelCost))
        }
        if (summary.chargeCost > 0.0 || summary.chargeKwh > 0.0) {
            StatRow(stringResource(R.string.stat_charging), fmt.money(summary.chargeCost))
        }
        StatRow(stringResource(R.string.stat_service_other), fmt.money(summary.otherCost))
    }

    SectionCard(title = stringResource(R.string.stats_driving), modifier = cardModifier) {
        val distance = summary.distanceKm
        val costPerKm = summary.costPerKm
        if (distance != null) StatRow(stringResource(R.string.stat_distance), fmt.distance(distance))
        if (costPerKm != null) {
            StatRow(
                stringResource(R.string.stat_cost_per_distance, fmt.distanceLabel),
                fmt.costPerDistance(costPerKm),
            )
        }
        val fuelCostPerKm = summary.fuelEconomy.costPerKm
        if (fuelCostPerKm != null && summary.fuelEconomy.cost > 0.0) {
            StatRow(
                stringResource(R.string.stat_fuel_cost_per_distance, fmt.distanceLabel),
                fmt.costPerDistance(fuelCostPerKm),
            )
        }
        val chargeCostPerKm = summary.chargeEconomy.costPerKm
        if (chargeCostPerKm != null && summary.chargeEconomy.cost > 0.0) {
            StatRow(
                stringResource(R.string.stat_charge_cost_per_distance, fmt.distanceLabel),
                fmt.costPerDistance(chargeCostPerKm),
            )
        }
        if (distance == null) {
            HintText(stringResource(R.string.stats_distance_hint))
        }
    }

    SectionCard(title = stringResource(R.string.stats_consumption), modifier = cardModifier) {
        if (summary.fuelEconomy.hasData) {
            StatRow(
                stringResource(R.string.stat_fuel_consumption),
                fmt.fuelEconomy(summary.fuelEconomy.quantity, summary.fuelEconomy.distanceKm),
                emphasized = true,
            )
        }
        if (summary.fuelLiters > 0.0) {
            StatRow(stringResource(R.string.stat_fuel_total), fmt.quantity(EntryType.FUEL, summary.fuelLiters))
        }
        if (summary.chargeEconomy.hasData) {
            StatRow(
                stringResource(R.string.stat_electric_consumption),
                fmt.electricEconomy(summary.chargeEconomy.quantity, summary.chargeEconomy.distanceKm),
                emphasized = true,
            )
        }
        if (summary.chargeKwh > 0.0) {
            StatRow(stringResource(R.string.stat_energy_total), fmt.quantity(EntryType.CHARGE, summary.chargeKwh))
        }
        if (!summary.fuelEconomy.hasData && !summary.chargeEconomy.hasData) {
            HintText(stringResource(R.string.home_consumption_hint))
        }
    }

    SectionCard(title = stringResource(R.string.stats_chart_costs), modifier = cardModifier) {
        MonthlyCostChart(months = months)
    }

    ConsumptionTrend(
        title = stringResource(R.string.stats_chart_fuel),
        type = EntryType.FUEL,
        stats = summary.fuelEconomy,
        modifier = cardModifier,
    )
    ConsumptionTrend(
        title = stringResource(R.string.stats_chart_electric),
        type = EntryType.CHARGE,
        stats = summary.chargeEconomy,
        modifier = cardModifier,
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Consumption of each measured interval as a line; hidden with fewer than two intervals. */
@Composable
private fun ConsumptionTrend(title: String, type: EntryType, stats: EnergyStats, modifier: Modifier = Modifier) {
    val fmt = LocalFmt.current
    val intervals: List<EconomyInterval> = stats.intervals.takeLast(TREND_POINTS)
    if (intervals.size < 2) return
    val values = intervals.map { fmt.economyValue(type, it.quantity, it.distanceKm) }
    val max = values.maxOrNull() ?: return
    val min = values.minOrNull() ?: return
    SectionCard(title = title, modifier = modifier) {
        TrendChart(
            values = values,
            highLabel = fmt.number(max, 1, 1),
            lowLabel = fmt.number(min, 1, 1),
        )
        HintText(
            stringResource(
                R.string.stats_chart_trend_caption,
                fmt.economyLabel(type),
                fmt.date(intervals.first().endEpochDay),
                fmt.date(intervals.last().endEpochDay),
            )
        )
    }
}

package com.personalizeprolabs.odolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.AppSettings
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.Powertrain
import com.personalizeprolabs.odolog.core.Reminder
import com.personalizeprolabs.odolog.core.Stats
import java.time.LocalDate

/** Overview: this month, the last 12 months, the next reminders and the latest entries. */
@Composable
fun HomeTab(
    settings: AppSettings,
    entries: List<LogEntry>,
    reminders: List<Reminder>,
    onAddEntry: (EntryType) -> Unit,
    onEditEntry: (LogEntry) -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (Reminder) -> Unit,
    onShowLog: () -> Unit,
    onShowReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fmt = LocalFmt.current
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val monthStart = today.withDayOfMonth(1).toEpochDay()
    val yearStart = today.minusMonths(12).plusDays(1).toEpochDay()
    val month = remember(entries, monthStart) { Stats.summarize(entries, monthStart, null) }
    val year = remember(entries, yearStart) { Stats.summarize(entries, yearStart, null) }
    val latestOdometer = remember(entries) { Stats.latestOdometerKm(entries) }
    val fuelIntervals = remember(entries) {
        Stats.economyIntervals(entries, EntryType.FUEL).associateBy { it.endEntryId }
    }
    val chargeIntervals = remember(entries) {
        Stats.economyIntervals(entries, EntryType.CHARGE).associateBy { it.endEntryId }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entries.isEmpty()) {
            WelcomeCard(powertrain = settings.powertrain, onAddEntry = onAddEntry)
        } else {
            SectionCard(title = stringResource(R.string.home_this_month)) {
                StatRow(stringResource(R.string.stat_total_cost), fmt.money(month.totalCost), emphasized = true)
                if (month.fuelCost > 0.0) StatRow(stringResource(R.string.stat_fuel), fmt.money(month.fuelCost))
                if (month.chargeCost > 0.0) StatRow(stringResource(R.string.stat_charging), fmt.money(month.chargeCost))
                if (month.otherCost > 0.0) {
                    StatRow(stringResource(R.string.stat_service_other), fmt.money(month.otherCost))
                }
            }

            SectionCard(title = stringResource(R.string.home_last_12_months)) {
                StatRow(stringResource(R.string.stat_total_cost), fmt.money(year.totalCost), emphasized = true)
                if (year.fuelEconomy.hasData) {
                    StatRow(
                        stringResource(R.string.stat_fuel_consumption),
                        fmt.fuelEconomy(year.fuelEconomy.quantity, year.fuelEconomy.distanceKm),
                    )
                }
                if (year.chargeEconomy.hasData) {
                    StatRow(
                        stringResource(R.string.stat_electric_consumption),
                        fmt.electricEconomy(year.chargeEconomy.quantity, year.chargeEconomy.distanceKm),
                    )
                }
                val costPerKm = year.costPerKm
                if (costPerKm != null) {
                    StatRow(
                        stringResource(R.string.stat_cost_per_distance, fmt.distanceLabel),
                        fmt.costPerDistance(costPerKm),
                    )
                }
                val distance = year.distanceKm
                if (distance != null) StatRow(stringResource(R.string.stat_distance), fmt.distance(distance))
                if (latestOdometer != null) {
                    StatRow(stringResource(R.string.stat_odometer), fmt.distance(latestOdometer))
                }
                if (!year.fuelEconomy.hasData && !year.chargeEconomy.hasData) {
                    Text(
                        text = stringResource(R.string.home_consumption_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.tab_reminders)) {
            if (reminders.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_no_reminders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(onClick = onAddReminder, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.reminder_add))
                }
            } else {
                for (reminder in reminders.take(3)) {
                    ReminderLine(reminder, todayDay, onClick = { onEditReminder(reminder) })
                }
                if (reminders.size > 3) {
                    TextButton(onClick = onShowReminders, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.action_show_all))
                    }
                }
            }
        }

        if (entries.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.home_latest_entries)) {
                for (entry in entries.take(4)) {
                    val interval = if (entry.type == EntryType.CHARGE) chargeIntervals[entry.id] else fuelIntervals[entry.id]
                    EntryRow(entry = entry, economy = interval, onClick = { onEditEntry(entry) })
                }
                TextButton(onClick = onShowLog, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.action_show_all))
                }
            }
        }
    }
}

/** Shown while the log book is still empty. */
@Composable
private fun WelcomeCard(powertrain: Powertrain, onAddEntry: (EntryType) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.welcome_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (powertrain.usesFuel) {
                    Button(onClick = { onAddEntry(EntryType.FUEL) }) { Text(stringResource(R.string.add_fuel)) }
                }
                if (powertrain.usesCharging) {
                    Button(onClick = { onAddEntry(EntryType.CHARGE) }) { Text(stringResource(R.string.add_charge)) }
                }
            }
            OutlinedButton(onClick = { onAddEntry(EntryType.EXPENSE) }) {
                Text(stringResource(R.string.add_expense))
            }
        }
    }
}

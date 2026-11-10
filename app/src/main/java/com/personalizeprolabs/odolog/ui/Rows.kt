package com.personalizeprolabs.odolog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.EconomyInterval
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.Reminder
import com.personalizeprolabs.odolog.core.ReminderLogic
import com.personalizeprolabs.odolog.core.ReminderStatus

/**
 * One line of the log book.
 *
 * @param economy the consumption interval that ends with this entry, if there is one.
 */
@Composable
fun EntryRow(entry: LogEntry, economy: EconomyInterval?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fmt = LocalFmt.current
    val typeLabel = stringResource(entryTypeLabelRes(entry.type))
    val categoryLabel = stringResource(categoryLabelRes(entry.category))

    val title = when (entry.type) {
        EntryType.EXPENSE -> if (entry.title.isBlank()) categoryLabel else entry.title
        else -> typeLabel + " · " + fmt.quantity(entry.type, entry.quantity)
    }
    val details = ArrayList<String>()
    details.add(fmt.date(entry.dateEpochDay))
    val odometer = entry.odometerKm
    if (odometer != null) details.add(fmt.distance(odometer))
    if (entry.type == EntryType.EXPENSE && entry.title.isNotBlank()) details.add(categoryLabel)
    if (economy != null) details.add(fmt.economy(entry.type, economy.quantity, economy.distanceKm))

    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entryIconRes(entry), contentDescription = typeLabel, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text = fmt.money(entry.totalCost), style = MaterialTheme.typography.titleMedium)
    }
}

/** "in 12 days", "due today" or "3 days overdue". */
@Composable
fun reminderStatusText(reminder: Reminder, todayEpochDay: Long): String {
    val days = ReminderLogic.daysUntilDue(reminder, todayEpochDay).toInt()
    val resources = LocalContext.current.resources
    return when {
        days < 0 -> resources.getQuantityString(R.plurals.overdue_days, -days, -days)
        days == 0 -> stringResource(R.string.reminder_due_today)
        else -> resources.getQuantityString(R.plurals.in_days, days, days)
    }
}

@Composable
fun reminderStatusColor(reminder: Reminder, todayEpochDay: Long): Color =
    when (ReminderLogic.status(reminder, todayEpochDay)) {
        ReminderStatus.OVERDUE, ReminderStatus.DUE_TODAY -> MaterialTheme.colorScheme.error
        ReminderStatus.UPCOMING -> MaterialTheme.colorScheme.tertiary
        ReminderStatus.LATER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** Compact reminder line for the home tab. */
@Composable
fun ReminderLine(reminder: Reminder, todayEpochDay: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fmt = LocalFmt.current
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(R.drawable.ic_bell, contentDescription = null, tint = reminderStatusColor(reminder, todayEpochDay))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = fmt.date(reminder.dueEpochDay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = reminderStatusText(reminder, todayEpochDay),
            style = MaterialTheme.typography.labelLarge,
            color = reminderStatusColor(reminder, todayEpochDay),
        )
    }
}

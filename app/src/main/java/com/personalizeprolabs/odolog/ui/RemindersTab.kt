@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalizeprolabs.odolog.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.Reminder
import java.time.LocalDate

/** All reminders, most urgent first. */
@Composable
fun RemindersTab(
    reminders: List<Reminder>,
    notificationsEnabled: Boolean,
    onEdit: (Reminder) -> Unit,
    onDone: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val today = LocalDate.now().toEpochDay()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!notificationsEnabled && reminders.isNotEmpty()) {
            item(key = "notifications-off") {
                NotificationsOffCard(onOpenSettings = { openNotificationSettings(context) })
            }
        }
        if (reminders.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.reminders_empty_title),
                    text = stringResource(R.string.reminders_empty_text),
                )
            }
        }
        items(reminders, key = { it.id }) { reminder ->
            ReminderCard(
                reminder = reminder,
                todayEpochDay = today,
                onEdit = { onEdit(reminder) },
                onDone = { onDone(reminder) },
            )
        }
    }
}

@Composable
private fun ReminderCard(reminder: Reminder, todayEpochDay: Long, onEdit: () -> Unit, onDone: () -> Unit) {
    val fmt = LocalFmt.current
    val resources = LocalContext.current.resources
    val repeatText = if (reminder.repeatMonths > 0) {
        resources.getQuantityString(R.plurals.repeat_every_months, reminder.repeatMonths, reminder.repeatMonths)
    } else {
        stringResource(R.string.reminder_repeat_none)
    }
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = reminderStatusText(reminder, todayEpochDay),
                    style = MaterialTheme.typography.labelLarge,
                    color = reminderStatusColor(reminder, todayEpochDay),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = stringResource(reminderKindLabelRes(reminder.kind)) + " · " + fmt.date(reminder.dueEpochDay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = repeatText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reminder.note.isNotBlank()) {
                Text(
                    text = reminder.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.reminder_mark_done)) }
            }
        }
    }
}

/** Shown when Android will not display the app's notifications. */
@Composable
private fun NotificationsOffCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(text = stringResource(R.string.notifications_off_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.notifications_off_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.notifications_off_action)) }
            }
        }
    }
}

/** Opens the system screen where notifications for this app can be switched on. */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // No such settings screen on this device: nothing else to do.
    }
}

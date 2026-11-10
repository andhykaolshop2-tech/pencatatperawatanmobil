@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalizeprolabs.odolog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.personalizeprolabs.odolog.OdologApp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.AppSettings
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.Powertrain
import com.personalizeprolabs.odolog.core.Reminder
import com.personalizeprolabs.odolog.core.ReminderLogic
import java.time.LocalDate
import kotlinx.coroutines.launch

const val TAB_HOME = 0
const val TAB_LOG = 1
const val TAB_REMINDERS = 2
const val TAB_STATS = 3

/** The main screen: top bar, the four tabs and the "add" button. */
@Composable
fun MainScreen(
    tab: Int,
    onTabChange: (Int) -> Unit,
    settings: AppSettings,
    entries: List<LogEntry>,
    reminders: List<Reminder>,
    notificationsEnabled: Boolean,
    onAddEntry: (EntryType) -> Unit,
    onEditEntry: (LogEntry) -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (Reminder) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as OdologApp
    val fmt = LocalFmt.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var reminderToRemove by remember { mutableStateOf<Reminder?>(null) }

    // "Done": a repeating reminder moves to its next date, any other reminder is removed.
    val onReminderDone: (Reminder) -> Unit = { reminder ->
        val next = ReminderLogic.nextDueEpochDay(reminder, LocalDate.now().toEpochDay())
        if (next == null) {
            reminderToRemove = reminder
        } else {
            scope.launch {
                app.repository.saveReminder(
                    reminder.copy(dueEpochDay = next, notifiedStage = ReminderLogic.STAGE_NONE)
                )
                snackbarHostState.showSnackbar(context.getString(R.string.reminder_next_due, fmt.date(next)))
            }
        }
    }

    val appName = stringResource(R.string.app_name)
    val title = when (tab) {
        TAB_LOG -> stringResource(R.string.tab_log)
        TAB_REMINDERS -> stringResource(R.string.tab_reminders)
        TAB_STATS -> stringResource(R.string.tab_stats)
        else -> if (settings.vehicleName.isBlank()) appName else settings.vehicleName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        AppIcon(R.drawable.ic_settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == TAB_HOME,
                    onClick = { onTabChange(TAB_HOME) },
                    icon = { AppIcon(R.drawable.ic_home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_LOG,
                    onClick = { onTabChange(TAB_LOG) },
                    icon = { AppIcon(R.drawable.ic_list, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_log)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_REMINDERS,
                    onClick = { onTabChange(TAB_REMINDERS) },
                    icon = { AppIcon(R.drawable.ic_bell, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_reminders)) },
                )
                NavigationBarItem(
                    selected = tab == TAB_STATS,
                    onClick = { onTabChange(TAB_STATS) },
                    icon = { AppIcon(R.drawable.ic_chart, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_stats)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            when (tab) {
                TAB_HOME, TAB_LOG -> AddEntryButton(powertrain = settings.powertrain, onAdd = onAddEntry)
                TAB_REMINDERS -> FloatingActionButton(onClick = onAddReminder) {
                    AppIcon(R.drawable.ic_add, contentDescription = stringResource(R.string.reminder_add))
                }
                else -> Unit
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding).fillMaxSize()
        when (tab) {
            TAB_LOG -> LogTab(entries = entries, onEditEntry = onEditEntry, modifier = contentModifier)

            TAB_REMINDERS -> RemindersTab(
                reminders = reminders,
                notificationsEnabled = notificationsEnabled,
                onEdit = onEditReminder,
                onDone = onReminderDone,
                modifier = contentModifier,
            )

            TAB_STATS -> StatsTab(entries = entries, modifier = contentModifier)

            else -> HomeTab(
                settings = settings,
                entries = entries,
                reminders = reminders,
                onAddEntry = onAddEntry,
                onEditEntry = onEditEntry,
                onAddReminder = onAddReminder,
                onEditReminder = onEditReminder,
                onShowLog = { onTabChange(TAB_LOG) },
                onShowReminders = { onTabChange(TAB_REMINDERS) },
                modifier = contentModifier,
            )
        }
    }

    val removing = reminderToRemove
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { reminderToRemove = null },
            title = { Text(stringResource(R.string.reminder_done_title)) },
            text = { Text(stringResource(R.string.reminder_done_remove_text, removing.title)) },
            confirmButton = {
                TextButton(onClick = {
                    reminderToRemove = null
                    scope.launch { app.repository.deleteReminder(removing.id) }
                }) { Text(stringResource(R.string.reminder_done_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { reminderToRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The floating "+" button with a small menu: fill-up, charge, service / other cost. */
@Composable
private fun AddEntryButton(powertrain: Powertrain, onAdd: (EntryType) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { menuOpen = true }) {
            AppIcon(R.drawable.ic_add, contentDescription = stringResource(R.string.action_add_entry))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (powertrain.usesFuel) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_fuel)) },
                    leadingIcon = { AppIcon(R.drawable.ic_fuel, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onAdd(EntryType.FUEL)
                    },
                )
            }
            if (powertrain.usesCharging) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_charge)) },
                    leadingIcon = { AppIcon(R.drawable.ic_charge, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onAdd(EntryType.CHARGE)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_expense)) },
                leadingIcon = { AppIcon(R.drawable.ic_service, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onAdd(EntryType.EXPENSE)
                },
            )
        }
    }
}

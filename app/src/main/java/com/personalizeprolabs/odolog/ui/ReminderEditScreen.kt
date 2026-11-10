@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.personalizeprolabs.odolog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.OdologApp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.Reminder
import com.personalizeprolabs.odolog.core.ReminderKind
import com.personalizeprolabs.odolog.core.ReminderLogic
import java.time.LocalDate
import kotlinx.coroutines.launch

private val REPEAT_OPTIONS = listOf(0, 3, 6, 12, 24)
private val NOTIFY_OPTIONS = listOf(0, 1, 3, 7, 14, 30)

private const val DIALOG_NONE = 0
private const val DIALOG_REPEAT = 1
private const val DIALOG_NOTIFY = 2
private const val DIALOG_DELETE = 3

/**
 * Form for a new or an existing reminder.
 *
 * @param reminderId id of the reminder to edit, or 0 for a new one.
 */
@Composable
fun ReminderEditScreen(reminderId: Long, reminders: List<Reminder>, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OdologApp
    val resources = context.resources
    val scope = rememberCoroutineScope()

    val existing = remember(reminderId) { reminders.firstOrNull { it.id == reminderId } }
    val today = LocalDate.now().toEpochDay()

    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var kindCode by rememberSaveable { mutableStateOf((existing?.kind ?: ReminderKind.SERVICE).code) }
    var due by rememberSaveable {
        mutableStateOf(existing?.dueEpochDay ?: LocalDate.now().plusMonths(12).toEpochDay())
    }
    var repeatMonths by rememberSaveable { mutableStateOf(existing?.repeatMonths ?: 12) }
    var notifyDays by rememberSaveable { mutableStateOf(existing?.notifyDaysBefore ?: 14) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(DIALOG_NONE) }

    val kind = ReminderKind.fromCode(kindCode) ?: ReminderKind.SERVICE
    val titleMissing = title.isBlank()

    // Android 13+ asks the user before an app may show notifications. The question is
    // asked once, right after the first reminder was saved; the screen closes afterwards
    // whatever the answer is.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onDone()
    }

    val save: () -> Unit = {
        if (titleMissing) {
            showErrors = true
        } else if (!saving) {
            saving = true
            val draft = Reminder(
                id = existing?.id ?: 0L,
                title = title.trim(),
                kind = kind,
                dueEpochDay = due,
                repeatMonths = repeatMonths,
                notifyDaysBefore = notifyDays,
                note = note.trim(),
            )
            // A stage that is already reached today is marked as shown: saving a reminder
            // should not immediately notify about what the user has just typed in.
            val reminder = draft.copy(notifiedStage = ReminderLogic.stageFor(draft, today))
            scope.launch {
                app.repository.saveReminder(reminder)
                val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onDone()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (existing == null) R.string.reminder_new else R.string.reminder_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        AppIcon(R.drawable.ic_arrow_back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { dialog = DIALOG_DELETE }) {
                            AppIcon(R.drawable.ic_delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.reminder_field_title)) },
                placeholder = { Text(stringResource(R.string.reminder_field_title_hint)) },
                isError = titleMissing && showErrors,
                supportingText = if (titleMissing && showErrors) {
                    { Text(stringResource(R.string.error_title_required)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in ReminderKind.values()) {
                    FilterChip(
                        selected = kind == option,
                        onClick = { kindCode = option.code },
                        label = { Text(stringResource(reminderKindLabelRes(option))) },
                    )
                }
            }

            DateField(label = stringResource(R.string.reminder_field_due), epochDay = due, onChange = { due = it })

            PickerField(
                label = stringResource(R.string.reminder_field_repeat),
                value = repeatLabel(resources, repeatMonths),
                onClick = { dialog = DIALOG_REPEAT },
            )
            PickerField(
                label = stringResource(R.string.reminder_field_notify),
                value = notifyLabel(resources, notifyDays),
                onClick = { dialog = DIALOG_NOTIFY },
            )
            Text(
                text = stringResource(R.string.reminder_notify_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_note)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Button(onClick = save, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    when (dialog) {
        DIALOG_REPEAT -> ChoiceDialog(
            title = stringResource(R.string.reminder_field_repeat),
            options = (REPEAT_OPTIONS + repeatMonths).distinct().sorted(),
            selected = repeatMonths,
            label = { repeatLabel(resources, it) },
            onSelect = {
                repeatMonths = it
                dialog = DIALOG_NONE
            },
            onDismiss = { dialog = DIALOG_NONE },
        )

        DIALOG_NOTIFY -> ChoiceDialog(
            title = stringResource(R.string.reminder_field_notify),
            options = (NOTIFY_OPTIONS + notifyDays).distinct().sorted(),
            selected = notifyDays,
            label = { notifyLabel(resources, it) },
            onSelect = {
                notifyDays = it
                dialog = DIALOG_NONE
            },
            onDismiss = { dialog = DIALOG_NONE },
        )

        DIALOG_DELETE -> if (existing != null) {
            AlertDialog(
                onDismissRequest = { dialog = DIALOG_NONE },
                title = { Text(stringResource(R.string.reminder_delete_title)) },
                text = { Text(stringResource(R.string.reminder_delete_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = DIALOG_NONE
                        scope.launch {
                            app.repository.deleteReminder(existing.id)
                            onDone()
                        }
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = DIALOG_NONE }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

private fun repeatLabel(resources: android.content.res.Resources, months: Int): String =
    if (months <= 0) {
        resources.getString(R.string.reminder_repeat_none)
    } else {
        resources.getQuantityString(R.plurals.repeat_every_months, months, months)
    }

private fun notifyLabel(resources: android.content.res.Resources, days: Int): String =
    if (days <= 0) {
        resources.getString(R.string.notify_on_the_day)
    } else {
        resources.getQuantityString(R.plurals.notify_days_before, days, days)
    }

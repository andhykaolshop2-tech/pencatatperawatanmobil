@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalizeprolabs.odolog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.ExpenseCategory
import com.personalizeprolabs.odolog.core.Fmt
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.Powertrain
import com.personalizeprolabs.odolog.core.ReminderKind

/** The formatter for the current locale and settings; provided once in OdologRoot. */
val LocalFmt = staticCompositionLocalOf<Fmt> { error("LocalFmt is not provided") }

private const val MILLIS_PER_DAY = 86_400_000L

// ------------------------------------------------------------------ icons

/** An icon from res/drawable (the app ships its own small set of vector icons). */
@Composable
fun AppIcon(
    id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painter = painterResource(id), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

fun entryIconRes(entry: LogEntry): Int = when (entry.type) {
    EntryType.FUEL -> R.drawable.ic_fuel
    EntryType.CHARGE -> R.drawable.ic_charge
    EntryType.EXPENSE -> if (
        entry.category == ExpenseCategory.SERVICE ||
        entry.category == ExpenseCategory.REPAIR ||
        entry.category == ExpenseCategory.INSPECTION ||
        entry.category == ExpenseCategory.TIRES
    ) R.drawable.ic_service else R.drawable.ic_receipt
}

// ----------------------------------------------------------------- labels

fun entryTypeLabelRes(type: EntryType): Int = when (type) {
    EntryType.FUEL -> R.string.type_fuel
    EntryType.CHARGE -> R.string.type_charge
    EntryType.EXPENSE -> R.string.type_expense
}

fun categoryLabelRes(category: ExpenseCategory): Int = when (category) {
    ExpenseCategory.SERVICE -> R.string.category_service
    ExpenseCategory.REPAIR -> R.string.category_repair
    ExpenseCategory.INSPECTION -> R.string.category_inspection
    ExpenseCategory.TIRES -> R.string.category_tires
    ExpenseCategory.INSURANCE -> R.string.category_insurance
    ExpenseCategory.TAX -> R.string.category_tax
    ExpenseCategory.PARKING -> R.string.category_parking
    ExpenseCategory.CLEANING -> R.string.category_cleaning
    ExpenseCategory.FINANCING -> R.string.category_financing
    ExpenseCategory.OTHER -> R.string.category_other
}

fun reminderKindLabelRes(kind: ReminderKind): Int = when (kind) {
    ReminderKind.SERVICE -> R.string.reminder_kind_service
    ReminderKind.INSPECTION -> R.string.reminder_kind_inspection
    ReminderKind.OTHER -> R.string.reminder_kind_other
}

fun powertrainLabelRes(powertrain: Powertrain): Int = when (powertrain) {
    Powertrain.COMBUSTION -> R.string.powertrain_combustion
    Powertrain.ELECTRIC -> R.string.powertrain_electric
    Powertrain.HYBRID -> R.string.powertrain_hybrid
}

// ------------------------------------------------------------ small pieces

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.padding(top = 8.dp))
            content()
        }
    }
}

/** "Label ............ value" line inside a card. */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.padding(top = 8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ----------------------------------------------------------------- inputs

/** A text field for numbers. The text is kept as typed; parsing happens in the screen. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else {
            null
        },
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

/**
 * Looks like a text field but opens a picker (date, list of choices) when tapped.
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIconRes: Int? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            trailingIcon = if (trailingIconRes != null) {
                { AppIcon(trailingIconRes, contentDescription = null) }
            } else {
                null
            },
        )
        // A read-only text field does not report clicks, so a transparent layer on top does.
        Box(Modifier.matchParentSize().clickable(onClickLabel = label, onClick = onClick))
    }
}

/** Shows a date and opens the Material date picker when tapped. */
@Composable
fun DateField(label: String, epochDay: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val fmt = LocalFmt.current
    var showPicker by remember { mutableStateOf(false) }
    PickerField(
        label = label,
        value = fmt.date(epochDay),
        onClick = { showPicker = true },
        modifier = modifier,
        trailingIconRes = R.drawable.ic_calendar,
    )
    if (showPicker) {
        // The Material date picker works with UTC midnight in milliseconds.
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) onChange(Math.floorDiv(millis, MILLIS_PER_DAY))
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** A checkbox with its label; the whole row can be tapped. */
@Composable
fun CheckRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** A dialog with one radio button per option; choosing an option closes it. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (option in options) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Spacer(Modifier.width(8.dp))
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

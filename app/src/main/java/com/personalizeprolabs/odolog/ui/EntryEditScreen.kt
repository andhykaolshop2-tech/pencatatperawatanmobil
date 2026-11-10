@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.personalizeprolabs.odolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.ExpenseCategory
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.NumberInput
import com.personalizeprolabs.odolog.core.Stats
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Form for a new or an existing log entry.
 *
 * @param type what is being logged (fill-up, charge, service / other expense).
 * @param entryId id of the entry to edit, or 0 for a new one.
 */
@Composable
fun EntryEditScreen(type: EntryType, entryId: Long, entries: List<LogEntry>, onDone: () -> Unit) {
    val fmt = LocalFmt.current
    val locale = fmt.locale
    val settings = fmt.settings
    val app = LocalContext.current.applicationContext as OdologApp
    val scope = rememberCoroutineScope()

    // The entry as it was when the screen opened; null means "new entry".
    val existing = remember(entryId) { entries.firstOrNull { it.id == entryId } }
    val isExpense = type == EntryType.EXPENSE

    var date by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var odometerText by rememberSaveable {
        mutableStateOf(existing?.odometerKm?.let { fmt.inputDistance(it) } ?: "")
    }
    var quantityText by rememberSaveable {
        mutableStateOf(if (existing != null && existing.quantity > 0.0) fmt.inputQuantity(type, existing.quantity) else "")
    }
    var costText by rememberSaveable { mutableStateOf(if (existing != null) fmt.input(existing.totalCost, 2) else "") }
    var isFull by rememberSaveable { mutableStateOf(existing?.isFull ?: true) }
    var missedPrevious by rememberSaveable { mutableStateOf(existing?.missedPrevious ?: false) }
    var categoryCode by rememberSaveable { mutableStateOf((existing?.category ?: ExpenseCategory.SERVICE).code) }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var chooseCategory by remember { mutableStateOf(false) }

    // What the typed texts mean. The limits make "45.123" kWh on a German keyboard a decimal.
    val odometerValue = NumberInput.parse(odometerText, locale, 9_999_999.0)
    val quantityValue = NumberInput.parse(quantityText, locale, 1_000.0)
    val costValue = NumberInput.parse(costText, locale)
    val odometerInvalid = odometerText.isNotBlank() && odometerValue == null
    val quantityInvalid = !isExpense && (quantityValue == null || quantityValue <= 0.0)
    val costInvalid = costText.isNotBlank() && costValue == null

    val odometerKm = if (odometerValue != null) settings.distanceUnit.toKm(odometerValue) else null
    val quantityCanonical = when {
        isExpense || quantityValue == null -> 0.0
        type == EntryType.FUEL -> settings.volumeUnit.toLiters(quantityValue)
        else -> quantityValue
    }
    val category = ExpenseCategory.fromCode(categoryCode) ?: ExpenseCategory.OTHER
    val previousOdometer = remember(entries, date, entryId) { Stats.highestOdometerUntil(entries, date, entryId) }
    val unitPrice = fmt.unitPrice(type, quantityCanonical, costValue ?: 0.0)

    val save: () -> Unit = {
        if (odometerInvalid || quantityInvalid || costInvalid) {
            showErrors = true
        } else if (!saving) {
            saving = true
            val entry = LogEntry(
                id = existing?.id ?: 0L,
                type = type,
                dateEpochDay = date,
                odometerKm = odometerKm,
                quantity = quantityCanonical,
                totalCost = costValue ?: 0.0,
                isFull = if (isExpense) true else isFull,
                missedPrevious = if (isExpense) false else missedPrevious,
                category = if (isExpense) category else ExpenseCategory.OTHER,
                title = if (isExpense) title.trim() else "",
                note = note.trim(),
            )
            scope.launch {
                app.repository.saveEntry(entry)
                onDone()
            }
        }
    }

    val titleRes = when (type) {
        EntryType.FUEL -> if (existing == null) R.string.entry_new_fuel else R.string.entry_edit_fuel
        EntryType.CHARGE -> if (existing == null) R.string.entry_new_charge else R.string.entry_edit_charge
        EntryType.EXPENSE -> if (existing == null) R.string.entry_new_expense else R.string.entry_edit_expense
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        AppIcon(R.drawable.ic_arrow_back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
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
            DateField(label = stringResource(R.string.field_date), epochDay = date, onChange = { date = it })

            if (isExpense) {
                PickerField(
                    label = stringResource(R.string.field_category),
                    value = stringResource(categoryLabelRes(category)),
                    onClick = { chooseCategory = true },
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_title)) },
                    placeholder = { Text(stringResource(R.string.field_title_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            } else {
                NumberField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = stringResource(if (type == EntryType.FUEL) R.string.field_volume else R.string.field_energy),
                    suffix = fmt.quantityLabel(type),
                    isError = quantityInvalid && (showErrors || quantityText.isNotBlank()),
                    supportingText = if (quantityInvalid && (showErrors || quantityText.isNotBlank())) {
                        stringResource(R.string.error_amount_required)
                    } else {
                        null
                    },
                )
            }

            NumberField(
                value = costText,
                onValueChange = { costText = it },
                label = stringResource(R.string.field_total_cost),
                suffix = fmt.currencySymbol,
                isError = costInvalid,
                supportingText = when {
                    costInvalid -> stringResource(R.string.error_number)
                    unitPrice != null -> unitPrice
                    else -> null
                },
            )

            NumberField(
                value = odometerText,
                onValueChange = { odometerText = it },
                label = stringResource(R.string.field_odometer),
                suffix = fmt.distanceLabel,
                isError = odometerInvalid,
                supportingText = when {
                    odometerInvalid -> stringResource(R.string.error_number)
                    odometerKm != null && previousOdometer != null && odometerKm < previousOdometer ->
                        stringResource(R.string.odometer_lower_warning, fmt.distance(previousOdometer))
                    previousOdometer != null -> stringResource(R.string.odometer_last, fmt.distance(previousOdometer))
                    else -> stringResource(R.string.odometer_optional)
                },
            )

            if (!isExpense) {
                CheckRow(
                    text = stringResource(if (type == EntryType.FUEL) R.string.field_full_tank else R.string.field_full_charge),
                    checked = isFull,
                    onCheckedChange = { isFull = it },
                )
                CheckRow(
                    text = stringResource(if (type == EntryType.FUEL) R.string.field_missed_fuel else R.string.field_missed_charge),
                    checked = missedPrevious,
                    onCheckedChange = { missedPrevious = it },
                )
                Text(
                    text = stringResource(if (type == EntryType.FUEL) R.string.help_consumption_fuel else R.string.help_consumption_charge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

    if (chooseCategory) {
        ChoiceDialog(
            title = stringResource(R.string.field_category),
            options = ExpenseCategory.values().toList(),
            selected = category,
            label = { stringResource(categoryLabelRes(it)) },
            onSelect = {
                categoryCode = it.code
                chooseCategory = false
            },
            onDismiss = { chooseCategory = false },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.entry_delete_title)) },
            text = { Text(stringResource(R.string.entry_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.repository.deleteEntry(existing.id)
                        onDone()
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

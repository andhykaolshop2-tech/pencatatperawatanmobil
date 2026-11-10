@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalizeprolabs.odolog.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.OdologApp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.AppSettings
import com.personalizeprolabs.odolog.core.Backup
import com.personalizeprolabs.odolog.core.BackupData
import com.personalizeprolabs.odolog.core.Csv
import com.personalizeprolabs.odolog.core.Defaults
import com.personalizeprolabs.odolog.core.DistanceUnit
import com.personalizeprolabs.odolog.core.ElectricEconomyUnit
import com.personalizeprolabs.odolog.core.FuelEconomyUnit
import com.personalizeprolabs.odolog.core.ImportResult
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.Powertrain
import com.personalizeprolabs.odolog.core.Reminder
import com.personalizeprolabs.odolog.core.VehicleInfo
import com.personalizeprolabs.odolog.core.VolumeUnit
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DIALOG_NONE = 0
private const val DIALOG_NAME = 1
private const val DIALOG_POWERTRAIN = 2
private const val DIALOG_DISTANCE = 3
private const val DIALOG_VOLUME = 4
private const val DIALOG_FUEL_ECONOMY = 5
private const val DIALOG_ELECTRIC_ECONOMY = 6
private const val DIALOG_CURRENCY = 7

/** Import files larger than this are refused (a log book of many years is far smaller). */
private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

// ------------------------------------------------------------------ setup

/** First start: choose the kind of car and the units, then begin. */
@Composable
fun SetupScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onDone: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.setup_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            VehicleAndUnitRows(settings = settings, onChange = onChange)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.setup_start))
            }
        }
    }
}

// --------------------------------------------------------------- settings

@Composable
fun SettingsScreen(settings: AppSettings, entries: List<LogEntry>, reminders: List<Reminder>, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OdologApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingImport by remember { mutableStateOf<ImportResult?>(null) }
    var importReplace by remember { mutableStateOf(false) }
    var importFailed by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    writeBackup(context, uri, settings, entries, reminders)
                }
                snackbarHostState.showSnackbar(
                    if (ok) {
                        context.getString(R.string.export_done, entries.size, reminders.size)
                    } else {
                        context.getString(R.string.export_failed)
                    }
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { readBackup(context, uri) }
                val usable = result != null && result.recognized &&
                    (result.data.entries.isNotEmpty() || result.data.reminders.isNotEmpty())
                if (usable) {
                    importReplace = false
                    pendingImport = result
                } else {
                    importFailed = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(R.drawable.ic_arrow_back, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsHeader(stringResource(R.string.settings_section_vehicle))
            VehicleAndUnitRows(settings = settings, onChange = { app.settings.update(it) })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsHeader(stringResource(R.string.settings_section_data))
            SettingRow(
                title = stringResource(R.string.settings_export),
                value = stringResource(R.string.settings_export_summary),
                onClick = { exportLauncher.launch("odolog-" + LocalDate.now() + ".csv") },
            )
            SettingRow(
                title = stringResource(R.string.settings_import),
                value = stringResource(R.string.settings_import_summary),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
            SettingRow(
                title = stringResource(R.string.settings_delete_all),
                value = stringResource(R.string.settings_delete_all_summary),
                onClick = { confirmDeleteAll = true },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsHeader(stringResource(R.string.settings_section_about))
            if (Build.VERSION.SDK_INT >= 33) {
                SettingRow(
                    title = stringResource(R.string.settings_language),
                    value = stringResource(R.string.settings_language_summary),
                    onClick = { openAppLanguageSettings(context) },
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_privacy),
                value = stringResource(R.string.settings_privacy_summary),
                onClick = { showPrivacy = true },
            )
            SettingRow(
                title = stringResource(R.string.settings_licenses),
                value = stringResource(R.string.settings_licenses_summary),
                onClick = { showLicenses = true },
            )
            SettingRow(
                title = stringResource(R.string.app_name),
                value = stringResource(R.string.settings_version, appVersionName(context)),
                onClick = null,
            )
        }
    }

    val pending = pendingImport
    if (pending != null) {
        val hasData = entries.isNotEmpty() || reminders.isNotEmpty()
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.import_found,
                            pending.data.entries.size,
                            pending.data.reminders.size,
                        )
                    )
                    if (pending.skippedRows > 0) {
                        Text(stringResource(R.string.import_skipped, pending.skippedRows))
                    }
                    if (hasData) {
                        RadioRow(
                            text = stringResource(R.string.import_mode_add),
                            selected = !importReplace,
                            onClick = { importReplace = false },
                        )
                        RadioRow(
                            text = stringResource(R.string.import_mode_replace),
                            selected = importReplace,
                            onClick = { importReplace = true },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val replace = hasData && importReplace
                    pendingImport = null
                    scope.launch {
                        val counts = app.repository.importData(pending.data, replace)
                        val vehicle = pending.data.vehicle
                        if (vehicle != null && (replace || !hasData)) {
                            app.settings.update(
                                app.settings.settings.value.copy(
                                    vehicleName = vehicle.name,
                                    powertrain = vehicle.powertrain,
                                )
                            )
                        }
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.import_done, counts.entries, counts.reminders)
                        )
                    }
                }) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (importFailed) {
        AlertDialog(
            onDismissRequest = { importFailed = false },
            title = { Text(stringResource(R.string.import_failed_title)) },
            text = { Text(stringResource(R.string.import_failed_text)) },
            confirmButton = {
                TextButton(onClick = { importFailed = false }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.delete_all_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    scope.launch {
                        app.repository.deleteAll()
                        snackbarHostState.showSnackbar(context.getString(R.string.delete_all_done))
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.settings_privacy)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.privacy_summary))
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { openUrl(context, context.getString(R.string.privacy_policy_url)) }) {
                    Text(stringResource(R.string.privacy_open_full))
                }
            },
        )
    }

    if (showLicenses) {
        val licenseText = remember { readRawText(context, R.raw.apache_license_2_0) }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(licenseText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

// ------------------------------------------------------- vehicle and units

/** The rows for vehicle and units; shared by the settings screen and the first-run setup. */
@Composable
private fun VehicleAndUnitRows(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var dialog by remember { mutableStateOf(DIALOG_NONE) }
    val close = { dialog = DIALOG_NONE }
    val currencies = remember(settings.currencyCode) {
        (listOf(settings.currencyCode, Defaults.currencyFor(Locale.getDefault())) + Defaults.COMMON_CURRENCIES).distinct()
    }

    SettingRow(
        title = stringResource(R.string.settings_vehicle_name),
        value = if (settings.vehicleName.isBlank()) stringResource(R.string.settings_not_set) else settings.vehicleName,
        onClick = { dialog = DIALOG_NAME },
    )
    SettingRow(
        title = stringResource(R.string.settings_powertrain),
        value = stringResource(powertrainLabelRes(settings.powertrain)),
        onClick = { dialog = DIALOG_POWERTRAIN },
    )
    SettingRow(
        title = stringResource(R.string.settings_distance_unit),
        value = stringResource(distanceLabelRes(settings.distanceUnit)),
        onClick = { dialog = DIALOG_DISTANCE },
    )
    if (settings.powertrain.usesFuel) {
        SettingRow(
            title = stringResource(R.string.settings_volume_unit),
            value = stringResource(volumeLabelRes(settings.volumeUnit)),
            onClick = { dialog = DIALOG_VOLUME },
        )
        SettingRow(
            title = stringResource(R.string.settings_fuel_economy),
            value = fuelEconomyLabel(settings.fuelEconomyUnit),
            onClick = { dialog = DIALOG_FUEL_ECONOMY },
        )
    }
    if (settings.powertrain.usesCharging) {
        SettingRow(
            title = stringResource(R.string.settings_electric_economy),
            value = settings.electricEconomyUnit.label,
            onClick = { dialog = DIALOG_ELECTRIC_ECONOMY },
        )
    }
    SettingRow(
        title = stringResource(R.string.settings_currency),
        value = currencyLabel(settings.currencyCode),
        onClick = { dialog = DIALOG_CURRENCY },
    )

    when (dialog) {
        DIALOG_NAME -> TextInputDialog(
            title = stringResource(R.string.settings_vehicle_name),
            initial = settings.vehicleName,
            hint = stringResource(R.string.settings_vehicle_name_hint),
            onConfirm = {
                onChange(settings.copy(vehicleName = it.trim()))
                close()
            },
            onDismiss = close,
        )

        DIALOG_POWERTRAIN -> ChoiceDialog(
            title = stringResource(R.string.settings_powertrain),
            options = Powertrain.values().toList(),
            selected = settings.powertrain,
            label = { stringResource(powertrainLabelRes(it)) },
            onSelect = {
                onChange(settings.copy(powertrain = it))
                close()
            },
            onDismiss = close,
        )

        DIALOG_DISTANCE -> ChoiceDialog(
            title = stringResource(R.string.settings_distance_unit),
            options = DistanceUnit.values().toList(),
            selected = settings.distanceUnit,
            label = { stringResource(distanceLabelRes(it)) },
            onSelect = {
                onChange(Defaults.withDistanceUnit(settings, it))
                close()
            },
            onDismiss = close,
        )

        DIALOG_VOLUME -> ChoiceDialog(
            title = stringResource(R.string.settings_volume_unit),
            options = VolumeUnit.values().toList(),
            selected = settings.volumeUnit,
            label = { stringResource(volumeLabelRes(it)) },
            onSelect = {
                onChange(Defaults.withVolumeUnit(settings, it))
                close()
            },
            onDismiss = close,
        )

        DIALOG_FUEL_ECONOMY -> ChoiceDialog(
            title = stringResource(R.string.settings_fuel_economy),
            options = FuelEconomyUnit.values().toList(),
            selected = settings.fuelEconomyUnit,
            label = { fuelEconomyLabel(it) },
            onSelect = {
                onChange(settings.copy(fuelEconomyUnit = it))
                close()
            },
            onDismiss = close,
        )

        DIALOG_ELECTRIC_ECONOMY -> ChoiceDialog(
            title = stringResource(R.string.settings_electric_economy),
            options = ElectricEconomyUnit.values().toList(),
            selected = settings.electricEconomyUnit,
            label = { it.label },
            onSelect = {
                onChange(settings.copy(electricEconomyUnit = it))
                close()
            },
            onDismiss = close,
        )

        DIALOG_CURRENCY -> ChoiceDialog(
            title = stringResource(R.string.settings_currency),
            options = currencies,
            selected = settings.currencyCode,
            label = { currencyLabel(it) },
            onSelect = {
                onChange(settings.copy(currencyCode = it))
                close()
            },
            onDismiss = close,
        )
    }
}

private fun distanceLabelRes(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KM -> R.string.unit_kilometers
    DistanceUnit.MI -> R.string.unit_miles
}

private fun volumeLabelRes(unit: VolumeUnit): Int = when (unit) {
    VolumeUnit.LITER -> R.string.unit_liters
    VolumeUnit.GALLON_US -> R.string.unit_gallons_us
    VolumeUnit.GALLON_UK -> R.string.unit_gallons_uk
}

/** "mpg" exists twice, so the two kinds are told apart here. */
private fun fuelEconomyLabel(unit: FuelEconomyUnit): String = when (unit) {
    FuelEconomyUnit.MPG_US -> "mpg (US)"
    FuelEconomyUnit.MPG_UK -> "mpg (UK)"
    else -> unit.label
}

/** "EUR (€)" */
private fun currencyLabel(code: String): String {
    val symbol = try {
        Currency.getInstance(code).getSymbol(Locale.getDefault())
    } catch (e: Exception) {
        null
    }
    return if (symbol == null || symbol == code) code else "$code ($symbol)"
}

// ------------------------------------------------------------ small pieces

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A two-line settings row. Without [onClick] it only shows information. */
@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)?) {
    val rowModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(modifier = rowModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(text = text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    hint: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ------------------------------------------------------------------ helpers

/** Writes the CSV backup to the document the user picked. Blocking; returns false on failure. */
private fun writeBackup(
    context: Context,
    uri: Uri,
    settings: AppSettings,
    entries: List<LogEntry>,
    reminders: List<Reminder>,
): Boolean {
    return try {
        val data = BackupData(VehicleInfo(settings.vehicleName, settings.powertrain), entries, reminders)
        val bytes = Csv.encode(Backup.exportCsv(data, settings))
        val stream = context.contentResolver.openOutputStream(uri) ?: return false
        stream.use { it.write(bytes) }
        true
    } catch (e: Exception) {
        false
    }
}

/** Reads and parses a CSV backup. Blocking; returns null when the file cannot be read. */
private fun readBackup(context: Context, uri: Uri): ImportResult? {
    return try {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = stream.use { readAtMost(it, MAX_IMPORT_BYTES) } ?: return null
        Backup.importCsv(Csv.decode(bytes), slashDatesMonthFirst = Locale.getDefault().country == "US")
    } catch (e: Exception) {
        null
    }
}

private fun readAtMost(input: InputStream, limit: Int): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = input.read(chunk)
        if (count < 0) break
        total += count
        if (total > limit) return null
        buffer.write(chunk, 0, count)
    }
    return buffer.toByteArray()
}

private fun readRawText(context: Context, id: Int): String {
    return try {
        context.resources.openRawResource(id).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        ""
    }
}

private fun appVersionName(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // No browser installed: the short summary in the dialog still tells the essentials.
    }
}

/** Android 13+: the system screen where the language of this app can be chosen. */
private fun openAppLanguageSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
    } catch (e: Exception) {
        // Not available on this device.
    }
}

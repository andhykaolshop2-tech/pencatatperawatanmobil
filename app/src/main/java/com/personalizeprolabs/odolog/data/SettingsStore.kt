package com.personalizeprolabs.odolog.data

import android.content.Context
import android.content.SharedPreferences
import com.personalizeprolabs.odolog.core.AppSettings
import com.personalizeprolabs.odolog.core.Defaults
import com.personalizeprolabs.odolog.core.DistanceUnit
import com.personalizeprolabs.odolog.core.ElectricEconomyUnit
import com.personalizeprolabs.odolog.core.FuelEconomyUnit
import com.personalizeprolabs.odolog.core.Powertrain
import com.personalizeprolabs.odolog.core.VolumeUnit
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The user's settings, stored in SharedPreferences and observable by the UI. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val settingsState = MutableStateFlow(load())
    private val setupDoneState = MutableStateFlow(prefs.getBoolean(KEY_SETUP_DONE, false))

    val settings: StateFlow<AppSettings> get() = settingsState

    /** False until the user has confirmed the first-run setup screen. */
    val setupDone: StateFlow<Boolean> get() = setupDoneState

    /** Values that were never saved fall back to defaults for the country of the device. */
    private fun load(): AppSettings {
        val defaults = Defaults.settingsFor(Locale.getDefault())
        return AppSettings(
            vehicleName = prefs.getString(KEY_VEHICLE_NAME, null) ?: defaults.vehicleName,
            powertrain = Powertrain.fromCode(prefs.getString(KEY_POWERTRAIN, null)) ?: defaults.powertrain,
            distanceUnit = DistanceUnit.fromCode(prefs.getString(KEY_DISTANCE_UNIT, null)) ?: defaults.distanceUnit,
            volumeUnit = VolumeUnit.fromCode(prefs.getString(KEY_VOLUME_UNIT, null)) ?: defaults.volumeUnit,
            fuelEconomyUnit = FuelEconomyUnit.fromCode(prefs.getString(KEY_FUEL_ECONOMY, null))
                ?: defaults.fuelEconomyUnit,
            electricEconomyUnit = ElectricEconomyUnit.fromCode(prefs.getString(KEY_ELECTRIC_ECONOMY, null))
                ?: defaults.electricEconomyUnit,
            currencyCode = prefs.getString(KEY_CURRENCY, null) ?: defaults.currencyCode,
        )
    }

    fun update(newSettings: AppSettings) {
        prefs.edit()
            .putString(KEY_VEHICLE_NAME, newSettings.vehicleName)
            .putString(KEY_POWERTRAIN, newSettings.powertrain.code)
            .putString(KEY_DISTANCE_UNIT, newSettings.distanceUnit.code)
            .putString(KEY_VOLUME_UNIT, newSettings.volumeUnit.code)
            .putString(KEY_FUEL_ECONOMY, newSettings.fuelEconomyUnit.code)
            .putString(KEY_ELECTRIC_ECONOMY, newSettings.electricEconomyUnit.code)
            .putString(KEY_CURRENCY, newSettings.currencyCode)
            .apply()
        settingsState.value = newSettings
    }

    fun markSetupDone() {
        prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply()
        setupDoneState.value = true
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_SETUP_DONE = "setup_done"
        const val KEY_VEHICLE_NAME = "vehicle_name"
        const val KEY_POWERTRAIN = "powertrain"
        const val KEY_DISTANCE_UNIT = "distance_unit"
        const val KEY_VOLUME_UNIT = "volume_unit"
        const val KEY_FUEL_ECONOMY = "fuel_economy_unit"
        const val KEY_ELECTRIC_ECONOMY = "electric_economy_unit"
        const val KEY_CURRENCY = "currency"
    }
}

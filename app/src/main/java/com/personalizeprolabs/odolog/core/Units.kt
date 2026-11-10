package com.personalizeprolabs.odolog.core

import java.util.Currency
import java.util.Locale

private const val KM_PER_MILE = 1.609344
private const val LITERS_PER_US_GALLON = 3.785411784
private const val LITERS_PER_UK_GALLON = 4.54609

enum class DistanceUnit(val code: String, val label: String, val kmPerUnit: Double) {
    KM("km", "km", 1.0),
    MI("mi", "mi", KM_PER_MILE);

    fun fromKm(km: Double): Double = km / kmPerUnit
    fun toKm(value: Double): Double = value * kmPerUnit

    companion object {
        fun fromCode(code: String?): DistanceUnit? = when (code?.trim()?.lowercase(Locale.ROOT)) {
            "km", "kilometer", "kilometre", "kilometers", "kilometres" -> KM
            "mi", "mile", "miles" -> MI
            else -> null
        }
    }
}

enum class VolumeUnit(val code: String, val label: String, val litersPerUnit: Double) {
    LITER("l", "L", 1.0),
    GALLON_US("gal_us", "gal", LITERS_PER_US_GALLON),
    GALLON_UK("gal_uk", "gal", LITERS_PER_UK_GALLON);

    fun fromLiters(liters: Double): Double = liters / litersPerUnit
    fun toLiters(value: Double): Double = value * litersPerUnit

    companion object {
        fun fromCode(code: String?): VolumeUnit? = when (code?.trim()?.lowercase(Locale.ROOT)) {
            "l", "liter", "litre", "liters", "litres" -> LITER
            "gal_us", "gal", "gallon", "gallons", "us_gal" -> GALLON_US
            "gal_uk", "uk_gal", "imp_gal", "gal_imp" -> GALLON_UK
            else -> null
        }
    }
}

/** How fuel consumption is shown. The calculation always starts from litres and kilometres. */
enum class FuelEconomyUnit(val code: String, val label: String) {
    L_PER_100KM("l_100km", "L/100 km"),
    KM_PER_L("km_l", "km/L"),
    MPG_US("mpg_us", "mpg"),
    MPG_UK("mpg_uk", "mpg");

    /** For L/100 km a lower number is better, for the other units a higher number is better. */
    val lowerIsBetter: Boolean get() = this == L_PER_100KM

    fun convert(liters: Double, km: Double): Double = when (this) {
        L_PER_100KM -> liters / km * 100.0
        KM_PER_L -> km / liters
        MPG_US -> (km / KM_PER_MILE) / (liters / LITERS_PER_US_GALLON)
        MPG_UK -> (km / KM_PER_MILE) / (liters / LITERS_PER_UK_GALLON)
    }

    companion object {
        fun fromCode(code: String?): FuelEconomyUnit? = values().firstOrNull { it.code == code }
    }
}

/** How electric consumption is shown. The calculation always starts from kWh and kilometres. */
enum class ElectricEconomyUnit(val code: String, val label: String) {
    KWH_PER_100KM("kwh_100km", "kWh/100 km"),
    KWH_PER_100MI("kwh_100mi", "kWh/100 mi"),
    KM_PER_KWH("km_kwh", "km/kWh"),
    MI_PER_KWH("mi_kwh", "mi/kWh");

    val lowerIsBetter: Boolean get() = this == KWH_PER_100KM || this == KWH_PER_100MI

    fun convert(kwh: Double, km: Double): Double = when (this) {
        KWH_PER_100KM -> kwh / km * 100.0
        KWH_PER_100MI -> kwh / (km / KM_PER_MILE) * 100.0
        KM_PER_KWH -> km / kwh
        MI_PER_KWH -> (km / KM_PER_MILE) / kwh
    }

    companion object {
        fun fromCode(code: String?): ElectricEconomyUnit? = values().firstOrNull { it.code == code }
    }
}

object Defaults {
    /** Currencies offered in the settings (the device currency is added when it is missing). */
    val COMMON_CURRENCIES: List<String> = listOf(
        "EUR", "GBP", "USD", "CAD", "AUD", "CHF", "NZD", "SEK", "NOK", "DKK", "PLN", "CZK",
    )

    /** Sensible first-run settings for the country of the device. */
    fun settingsFor(locale: Locale): AppSettings {
        val country = locale.country.uppercase(Locale.ROOT)
        val currency = currencyFor(locale)
        return when (country) {
            "US" -> AppSettings(
                vehicleName = "",
                powertrain = Powertrain.COMBUSTION,
                distanceUnit = DistanceUnit.MI,
                volumeUnit = VolumeUnit.GALLON_US,
                fuelEconomyUnit = FuelEconomyUnit.MPG_US,
                electricEconomyUnit = ElectricEconomyUnit.MI_PER_KWH,
                currencyCode = currency,
            )
            // United Kingdom: fuel is sold in litres, distances are miles, economy is quoted in imperial mpg.
            "GB" -> AppSettings(
                vehicleName = "",
                powertrain = Powertrain.COMBUSTION,
                distanceUnit = DistanceUnit.MI,
                volumeUnit = VolumeUnit.LITER,
                fuelEconomyUnit = FuelEconomyUnit.MPG_UK,
                electricEconomyUnit = ElectricEconomyUnit.MI_PER_KWH,
                currencyCode = currency,
            )
            else -> AppSettings(
                vehicleName = "",
                powertrain = Powertrain.COMBUSTION,
                distanceUnit = DistanceUnit.KM,
                volumeUnit = VolumeUnit.LITER,
                fuelEconomyUnit = FuelEconomyUnit.L_PER_100KM,
                electricEconomyUnit = ElectricEconomyUnit.KWH_PER_100KM,
                currencyCode = currency,
            )
        }
    }

    /**
     * Changes the distance unit and moves the consumption units along with it
     * (km -> L/100 km and kWh/100 km, miles -> mpg and mi/kWh). The user can still
     * pick other consumption units afterwards.
     */
    fun withDistanceUnit(settings: AppSettings, unit: DistanceUnit): AppSettings {
        if (unit == settings.distanceUnit) return settings
        return when (unit) {
            DistanceUnit.KM -> settings.copy(
                distanceUnit = unit,
                fuelEconomyUnit = FuelEconomyUnit.L_PER_100KM,
                electricEconomyUnit = ElectricEconomyUnit.KWH_PER_100KM,
            )
            DistanceUnit.MI -> settings.copy(
                distanceUnit = unit,
                fuelEconomyUnit = if (settings.volumeUnit == VolumeUnit.GALLON_US) FuelEconomyUnit.MPG_US else FuelEconomyUnit.MPG_UK,
                electricEconomyUnit = ElectricEconomyUnit.MI_PER_KWH,
            )
        }
    }

    /** Changes the volume unit; with miles the matching kind of mpg (US / UK) is selected too. */
    fun withVolumeUnit(settings: AppSettings, unit: VolumeUnit): AppSettings {
        if (unit == settings.volumeUnit) return settings
        if (settings.distanceUnit != DistanceUnit.MI) return settings.copy(volumeUnit = unit)
        return settings.copy(
            volumeUnit = unit,
            fuelEconomyUnit = if (unit == VolumeUnit.GALLON_US) FuelEconomyUnit.MPG_US else FuelEconomyUnit.MPG_UK,
        )
    }

    fun currencyFor(locale: Locale): String {
        val fromLocale = try {
            Currency.getInstance(locale)?.currencyCode
        } catch (e: Exception) {
            null
        }
        if (!fromLocale.isNullOrEmpty() && fromLocale != "XXX") return fromLocale
        return if (locale.language == "en") "USD" else "EUR"
    }
}

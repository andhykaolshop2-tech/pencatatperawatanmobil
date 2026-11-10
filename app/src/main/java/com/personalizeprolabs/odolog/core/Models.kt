package com.personalizeprolabs.odolog.core

import java.util.Locale

/*
 * Plain data model. Everything in the "core" package is pure Kotlin/JVM (no Android
 * imports) so that it can be unit-tested on any PC with `gradlew test`.
 *
 * Canonical units used for storage:
 *   distance  -> kilometres
 *   fuel      -> litres
 *   energy    -> kWh
 *   dates     -> epoch day (java.time.LocalDate.toEpochDay())
 *   money     -> plain number in the user's currency (never converted)
 *
 * The `code` values of the enums are stable identifiers: they are written to the
 * database and to CSV files. Never rename them, only add new ones.
 */

enum class EntryType(val code: String) {
    FUEL("fuel"),
    CHARGE("charge"),
    EXPENSE("expense");

    companion object {
        fun fromCode(code: String?): EntryType? {
            val c = normalize(code) ?: return null
            return values().firstOrNull { it.code == c }
        }
    }
}

enum class ExpenseCategory(val code: String) {
    SERVICE("service"),
    REPAIR("repair"),
    INSPECTION("inspection"),
    TIRES("tires"),
    INSURANCE("insurance"),
    TAX("tax"),
    PARKING("parking"),
    CLEANING("cleaning"),
    FINANCING("financing"),
    OTHER("other");

    companion object {
        fun fromCode(code: String?): ExpenseCategory? {
            val c = normalize(code) ?: return null
            if (c == "tyres") return TIRES
            return values().firstOrNull { it.code == c }
        }
    }
}

enum class Powertrain(val code: String) {
    COMBUSTION("combustion"),
    ELECTRIC("electric"),
    HYBRID("hybrid");

    val usesFuel: Boolean get() = this != ELECTRIC
    val usesCharging: Boolean get() = this != COMBUSTION

    companion object {
        fun fromCode(code: String?): Powertrain? {
            val c = normalize(code) ?: return null
            return values().firstOrNull { it.code == c }
        }
    }
}

enum class ReminderKind(val code: String) {
    SERVICE("service"),
    INSPECTION("inspection"),
    OTHER("other");

    companion object {
        fun fromCode(code: String?): ReminderKind? {
            val c = normalize(code) ?: return null
            return values().firstOrNull { it.code == c }
        }
    }
}

private fun normalize(code: String?): String? {
    val c = code?.trim()?.lowercase(Locale.ROOT)
    return if (c.isNullOrEmpty()) null else c
}

/** One line of the log book: a fill-up, a charge, or a service / other expense. */
data class LogEntry(
    val id: Long = 0,
    val type: EntryType,
    val dateEpochDay: Long,
    /** Odometer reading in km, or null when the user left it empty. */
    val odometerKm: Double? = null,
    /** Litres for FUEL, kWh for CHARGE, 0 for EXPENSE. */
    val quantity: Double = 0.0,
    val totalCost: Double = 0.0,
    /** FUEL: tank filled up completely. CHARGE: charged to the usual level. */
    val isFull: Boolean = true,
    /** The fill-up / charge before this one was not logged. */
    val missedPrevious: Boolean = false,
    /** Only meaningful for EXPENSE. */
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    /** Only meaningful for EXPENSE (free text, may be empty). */
    val title: String = "",
    val note: String = "",
)

/** A date based reminder (service, inspection, ...). */
data class Reminder(
    val id: Long = 0,
    val title: String,
    val kind: ReminderKind = ReminderKind.SERVICE,
    val dueEpochDay: Long,
    /** 0 = does not repeat. */
    val repeatMonths: Int = 0,
    /** How many days before the due date the first notification is shown (0 = only on the day). */
    val notifyDaysBefore: Int = 14,
    val note: String = "",
    /** 0 = nothing sent yet, 1 = "coming up" sent, 2 = "due" sent. See [ReminderLogic]. */
    val notifiedStage: Int = 0,
)

data class VehicleInfo(
    val name: String,
    val powertrain: Powertrain,
)

/** Everything the user can configure. Stored in SharedPreferences by the Android layer. */
data class AppSettings(
    val vehicleName: String,
    val powertrain: Powertrain,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val fuelEconomyUnit: FuelEconomyUnit,
    val electricEconomyUnit: ElectricEconomyUnit,
    /** ISO 4217 code, for example EUR, GBP, USD. */
    val currencyCode: String,
)

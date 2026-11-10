package com.personalizeprolabs.odolog.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

/**
 * Turns canonical values (km, litres, kWh, epoch days, plain money amounts) into text in
 * the units and the number format of the user. Unit labels such as "km" or "L/100 km"
 * are the same in English and German, so no string resources are needed here.
 */
class Fmt(val locale: Locale, val settings: AppSettings) {

    private val currency: Currency? = try {
        Currency.getInstance(settings.currencyCode)
    } catch (e: Exception) {
        null
    }

    private val decimalComma: Boolean = try {
        DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','
    } catch (e: Exception) {
        false
    }

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    private val monthYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", locale)

    val distanceLabel: String get() = settings.distanceUnit.label
    val volumeLabel: String get() = settings.volumeUnit.label

    /** "€", "£", "$" ... or the currency code when the device does not know the currency. */
    val currencySymbol: String
        get() {
            val c = currency ?: return settings.currencyCode
            return try {
                c.getSymbol(locale)
            } catch (e: Exception) {
                settings.currencyCode
            }
        }

    // ---------------------------------------------------------------- numbers

    fun number(value: Double, minDecimals: Int, maxDecimals: Int): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.minimumFractionDigits = minDecimals
        nf.maximumFractionDigits = maxDecimals
        return nf.format(value)
    }

    /** A money amount with the usual number of decimals of the currency, e.g. "1.234,56 €". */
    fun money(amount: Double): String {
        val c = currency ?: return number(amount, 2, 2) + " " + settings.currencyCode
        val nf = NumberFormat.getCurrencyInstance(locale)
        nf.currency = c
        val digits = c.defaultFractionDigits
        if (digits >= 0) {
            nf.minimumFractionDigits = digits
            nf.maximumFractionDigits = digits
        }
        return nf.format(amount)
    }

    /** A small money amount with up to three decimals (price per litre, cost per km). */
    fun moneyPrecise(amount: Double): String {
        val c = currency ?: return number(amount, 2, 3) + " " + settings.currencyCode
        val nf = NumberFormat.getCurrencyInstance(locale)
        nf.currency = c
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 3
        return nf.format(amount)
    }

    /**
     * Text for an input field: plain digits, the decimal separator of the locale, no
     * grouping. [NumberInput.parse] reads it back to the same value.
     */
    fun input(value: Double, maxDecimals: Int): String {
        if (value.isNaN() || value.isInfinite()) return ""
        val plain = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
        return if (decimalComma) plain.replace('.', ',') else plain
    }

    // ------------------------------------------------------------------ dates

    fun date(epochDay: Long): String = dateFormatter.format(LocalDate.ofEpochDay(epochDay))

    fun monthYear(year: Int, month: Int): String = monthYearFormatter.format(LocalDate.of(year, month, 1))

    /** One letter for a chart axis: J, F, M, ... */
    fun monthLetter(month: Int): String = Month.of(month).getDisplayName(TextStyle.NARROW, locale)

    // -------------------------------------------------------- distance & fuel

    fun distance(km: Double): String = number(settings.distanceUnit.fromKm(km), 0, 0) + " " + distanceLabel

    fun inputDistance(km: Double): String = input(settings.distanceUnit.fromKm(km), 1)

    /** Litres or kWh of an entry in the user's unit, e.g. "42,10 L" or "38,5 kWh". */
    fun quantity(type: EntryType, canonicalQuantity: Double): String = when (type) {
        EntryType.FUEL -> number(settings.volumeUnit.fromLiters(canonicalQuantity), 2, 2) + " " + volumeLabel
        EntryType.CHARGE -> number(canonicalQuantity, 1, 2) + " kWh"
        EntryType.EXPENSE -> ""
    }

    fun inputQuantity(type: EntryType, canonicalQuantity: Double): String = when (type) {
        EntryType.FUEL -> input(settings.volumeUnit.fromLiters(canonicalQuantity), 3)
        else -> input(canonicalQuantity, 3)
    }

    /** "L", "gal" or "kWh". */
    fun quantityLabel(type: EntryType): String = if (type == EntryType.CHARGE) "kWh" else volumeLabel

    /** Price per litre / gallon / kWh, e.g. "1,789 €/L"; null when it cannot be computed. */
    fun unitPrice(type: EntryType, canonicalQuantity: Double, totalCost: Double): String? {
        if (type == EntryType.EXPENSE || canonicalQuantity <= 0.0 || totalCost <= 0.0) return null
        val shown = if (type == EntryType.FUEL) settings.volumeUnit.fromLiters(canonicalQuantity) else canonicalQuantity
        return moneyPrecise(totalCost / shown) + "/" + quantityLabel(type)
    }

    fun fuelEconomy(liters: Double, km: Double): String {
        val unit = settings.fuelEconomyUnit
        return number(unit.convert(liters, km), 1, 1) + " " + unit.label
    }

    fun electricEconomy(kwh: Double, km: Double): String {
        val unit = settings.electricEconomyUnit
        val decimals = if (unit.lowerIsBetter) 1 else 2
        return number(unit.convert(kwh, km), decimals, decimals) + " " + unit.label
    }

    /** Consumption of an interval of fill-ups (FUEL) or charges (CHARGE). */
    fun economy(type: EntryType, quantity: Double, km: Double): String =
        if (type == EntryType.CHARGE) electricEconomy(quantity, km) else fuelEconomy(quantity, km)

    /** The plain number behind [economy], for charts. */
    fun economyValue(type: EntryType, quantity: Double, km: Double): Double =
        if (type == EntryType.CHARGE) settings.electricEconomyUnit.convert(quantity, km)
        else settings.fuelEconomyUnit.convert(quantity, km)

    fun economyLabel(type: EntryType): String =
        if (type == EntryType.CHARGE) settings.electricEconomyUnit.label else settings.fuelEconomyUnit.label

    /** Cost per km or per mile, e.g. "0,12 €/km". */
    fun costPerDistance(costPerKm: Double): String =
        moneyPrecise(costPerKm * settings.distanceUnit.kmPerUnit) + "/" + distanceLabel
}

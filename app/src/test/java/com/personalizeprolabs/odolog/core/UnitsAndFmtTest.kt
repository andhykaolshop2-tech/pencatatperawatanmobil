package com.personalizeprolabs.odolog.core

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsAndFmtTest {
    private val metric = AppSettings(
        vehicleName = "", powertrain = Powertrain.COMBUSTION, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.LITER, fuelEconomyUnit = FuelEconomyUnit.L_PER_100KM,
        electricEconomyUnit = ElectricEconomyUnit.KWH_PER_100KM, currencyCode = "EUR",
    )
    private val british = metric.copy(
        distanceUnit = DistanceUnit.MI, fuelEconomyUnit = FuelEconomyUnit.MPG_UK,
        electricEconomyUnit = ElectricEconomyUnit.MI_PER_KWH, currencyCode = "GBP",
    )

    @Test
    fun distanceAndVolume() {
        assertEquals(160.9344, DistanceUnit.MI.toKm(100.0), 1e-9)
        assertEquals(100.0, DistanceUnit.MI.fromKm(160.9344), 1e-9)
        assertEquals(37.85411784, VolumeUnit.GALLON_US.toLiters(10.0), 1e-9)
        assertEquals(10.0, VolumeUnit.GALLON_UK.fromLiters(45.4609), 1e-9)
        assertEquals(DistanceUnit.MI, DistanceUnit.fromCode(" MI "))
        assertEquals(VolumeUnit.GALLON_UK, VolumeUnit.fromCode("gal_uk"))
        assertEquals(VolumeUnit.LITER, VolumeUnit.fromCode("L"))
        assertNull(VolumeUnit.fromCode("barrel"))
    }

    @Test
    fun fuelEconomy() {
        // 7 L/100 km
        assertEquals(7.0, FuelEconomyUnit.L_PER_100KM.convert(35.0, 500.0), 1e-9)
        assertEquals(14.2857142857, FuelEconomyUnit.KM_PER_L.convert(35.0, 500.0), 1e-9)
        assertEquals(33.6020833, FuelEconomyUnit.MPG_US.convert(35.0, 500.0), 1e-6)
        assertEquals(40.3544195, FuelEconomyUnit.MPG_UK.convert(35.0, 500.0), 1e-6)
        assertTrue(FuelEconomyUnit.L_PER_100KM.lowerIsBetter)
        assertTrue(!FuelEconomyUnit.MPG_UK.lowerIsBetter)
    }

    @Test
    fun electricEconomy() {
        // 20 kWh/100 km
        assertEquals(20.0, ElectricEconomyUnit.KWH_PER_100KM.convert(40.0, 200.0), 1e-9)
        assertEquals(5.0, ElectricEconomyUnit.KM_PER_KWH.convert(40.0, 200.0), 1e-9)
        assertEquals(3.10685596, ElectricEconomyUnit.MI_PER_KWH.convert(40.0, 200.0), 1e-6)
        assertEquals(32.18688, ElectricEconomyUnit.KWH_PER_100MI.convert(40.0, 200.0), 1e-6)
    }

    @Test
    fun enumCodesAreStable() {
        // These codes are stored in databases and CSV files of existing users.
        assertEquals(listOf("fuel", "charge", "expense"), EntryType.values().map { it.code })
        assertEquals(
            listOf("service", "repair", "inspection", "tires", "insurance", "tax", "parking", "cleaning", "financing", "other"),
            ExpenseCategory.values().map { it.code },
        )
        assertEquals(listOf("combustion", "electric", "hybrid"), Powertrain.values().map { it.code })
        assertEquals(listOf("service", "inspection", "other"), ReminderKind.values().map { it.code })
        assertEquals(listOf("km", "mi"), DistanceUnit.values().map { it.code })
        assertEquals(listOf("l", "gal_us", "gal_uk"), VolumeUnit.values().map { it.code })
        for (unit in FuelEconomyUnit.values()) assertEquals(unit, FuelEconomyUnit.fromCode(unit.code))
        for (unit in ElectricEconomyUnit.values()) assertEquals(unit, ElectricEconomyUnit.fromCode(unit.code))
        assertEquals(EntryType.CHARGE, EntryType.fromCode(" Charge "))
        assertNull(EntryType.fromCode(""))
        assertNull(EntryType.fromCode(null))
    }

    @Test
    fun defaultsByCountry() {
        val us = Defaults.settingsFor(Locale.US)
        assertEquals(DistanceUnit.MI, us.distanceUnit)
        assertEquals(VolumeUnit.GALLON_US, us.volumeUnit)
        assertEquals(FuelEconomyUnit.MPG_US, us.fuelEconomyUnit)
        assertEquals("USD", us.currencyCode)

        val gb = Defaults.settingsFor(Locale.UK)
        assertEquals(DistanceUnit.MI, gb.distanceUnit)
        assertEquals(VolumeUnit.LITER, gb.volumeUnit)
        assertEquals(FuelEconomyUnit.MPG_UK, gb.fuelEconomyUnit)
        assertEquals("GBP", gb.currencyCode)

        val de = Defaults.settingsFor(Locale.GERMANY)
        assertEquals(DistanceUnit.KM, de.distanceUnit)
        assertEquals(FuelEconomyUnit.L_PER_100KM, de.fuelEconomyUnit)
        assertEquals(ElectricEconomyUnit.KWH_PER_100KM, de.electricEconomyUnit)
        assertEquals("EUR", de.currencyCode)

        assertEquals("CAD", Defaults.settingsFor(Locale.CANADA).currencyCode)
        assertEquals(DistanceUnit.KM, Defaults.settingsFor(Locale.CANADA).distanceUnit)
        assertEquals("AUD", Defaults.settingsFor(Locale.forLanguageTag("en-AU")).currencyCode)
        // a locale without a country must not crash
        assertEquals("USD", Defaults.settingsFor(Locale.ENGLISH).currencyCode)
        assertEquals("EUR", Defaults.settingsFor(Locale.GERMAN).currencyCode)
    }

    @Test
    fun changingUnitsMovesTheConsumptionUnitsAlong() {
        val miles = Defaults.withDistanceUnit(metric, DistanceUnit.MI)
        assertEquals(DistanceUnit.MI, miles.distanceUnit)
        assertEquals(FuelEconomyUnit.MPG_UK, miles.fuelEconomyUnit)      // litres + miles = imperial mpg
        assertEquals(ElectricEconomyUnit.MI_PER_KWH, miles.electricEconomyUnit)

        val usGallons = Defaults.withVolumeUnit(miles, VolumeUnit.GALLON_US)
        assertEquals(FuelEconomyUnit.MPG_US, usGallons.fuelEconomyUnit)
        assertEquals(FuelEconomyUnit.MPG_UK, Defaults.withVolumeUnit(usGallons, VolumeUnit.GALLON_UK).fuelEconomyUnit)

        val backToKm = Defaults.withDistanceUnit(usGallons, DistanceUnit.KM)
        assertEquals(FuelEconomyUnit.L_PER_100KM, backToKm.fuelEconomyUnit)
        assertEquals(ElectricEconomyUnit.KWH_PER_100KM, backToKm.electricEconomyUnit)
        assertEquals(VolumeUnit.GALLON_US, backToKm.volumeUnit)

        // with kilometres the volume unit does not touch the consumption unit
        assertEquals(FuelEconomyUnit.L_PER_100KM, Defaults.withVolumeUnit(metric, VolumeUnit.GALLON_UK).fuelEconomyUnit)
        // nothing changes when the same unit is chosen again (a manual choice is kept)
        val manual = metric.copy(fuelEconomyUnit = FuelEconomyUnit.KM_PER_L)
        assertEquals(manual, Defaults.withDistanceUnit(manual, DistanceUnit.KM))
        assertEquals(manual, Defaults.withVolumeUnit(manual, VolumeUnit.LITER))
    }

    @Test
    fun inputTextRoundTrips() {
        val german = Fmt(Locale.GERMANY, metric)
        val english = Fmt(Locale.UK, british)
        assertEquals("42,1", german.input(42.1, 3))
        assertEquals("42.1", english.input(42.1, 3))
        assertEquals("45210", german.input(45210.0, 1))
        assertEquals("1,79", german.input(1.789, 2))
        assertEquals("", german.input(Double.NaN, 2))
        for (value in listOf(0.0, 0.5, 42.1, 9.874, 45210.0, 123456.7, 1234.56)) {
            assertEquals(value, NumberInput.parse(german.input(value, 3), Locale.GERMANY) ?: Double.NaN, 1e-9)
            assertEquals(value, NumberInput.parse(english.input(value, 3), Locale.UK) ?: Double.NaN, 1e-9)
        }
        // miles shown, kilometres stored
        assertEquals("28092.2", english.inputDistance(45210.0))
        assertEquals(45210.0, DistanceUnit.MI.toKm(NumberInput.parse("28092.2", Locale.UK) ?: 0.0), 0.1)
    }

    @Test
    fun moneyInputKeepsTwoDecimals() {
        val german = Fmt(Locale.GERMANY, metric)
        val english = Fmt(Locale.UK, british)
        assertEquals("486.40", english.inputMoney(486.4))
        assertEquals("486,40", german.inputMoney(486.4))
        assertEquals("486", english.inputMoney(486.0))
        assertEquals("0", english.inputMoney(0.0))
        assertEquals("75.32", english.inputMoney(75.32))
        assertEquals("12.345", english.inputMoney(12.345))
        assertEquals("", english.inputMoney(Double.NaN))
        for (value in listOf(0.0, 14.0, 486.4, 75.32, 1289.9, 12.345)) {
            assertEquals(value, NumberInput.parse(german.inputMoney(value), Locale.GERMANY) ?: Double.NaN, 1e-9)
            assertEquals(value, NumberInput.parse(english.inputMoney(value), Locale.UK) ?: Double.NaN, 1e-9)
        }
    }

    @Test
    fun formattedValuesContainTheRightNumbersAndUnits() {
        val german = Fmt(Locale.GERMANY, metric)
        val english = Fmt(Locale.UK, british)
        // exact spacing and currency placement depend on the Java / Android version, so only the essentials are checked
        assertTrue(german.money(1234.5).contains("1.234,50"))
        assertTrue(german.money(1234.5).contains("€"))
        assertTrue(english.money(1234.5).contains("1,234.50"))
        assertTrue(english.money(1234.5).contains("£"))
        assertTrue(german.moneyPrecise(1.789).contains("1,789"))
        assertTrue(german.moneyPrecise(1.5).contains("1,50"))
        assertEquals("45.210 km", german.distance(45210.0))
        assertEquals("28,092 mi", english.distance(45210.0))
        assertEquals("42,10 L", german.quantity(EntryType.FUEL, 42.1))
        assertEquals("38,5 kWh", german.quantity(EntryType.CHARGE, 38.5))
        assertEquals("", german.quantity(EntryType.EXPENSE, 0.0))
        assertEquals("7,0 L/100 km", german.fuelEconomy(35.0, 500.0))
        assertEquals("40.4 mpg", english.fuelEconomy(35.0, 500.0))
        assertEquals("20,0 kWh/100 km", german.electricEconomy(40.0, 200.0))
        assertEquals("3.11 mi/kWh", english.electricEconomy(40.0, 200.0))
        assertEquals("7,0 L/100 km", german.economy(EntryType.FUEL, 35.0, 500.0))
        assertEquals(20.0, german.economyValue(EntryType.CHARGE, 40.0, 200.0), 1e-9)
        assertEquals("mpg", english.economyLabel(EntryType.FUEL))
        assertTrue(german.costPerDistance(0.12).endsWith("/km"))
        assertTrue(german.costPerDistance(0.12).contains("0,12"))
        assertTrue(english.costPerDistance(0.1).contains("0.161"))       // 0.10 per km = 0.161 per mile
        assertNotNull(german.unitPrice(EntryType.FUEL, 42.1, 75.32))
        assertTrue(german.unitPrice(EntryType.FUEL, 42.1, 75.32)!!.endsWith("/L"))
        assertNull(german.unitPrice(EntryType.FUEL, 0.0, 75.32))
        assertNull(german.unitPrice(EntryType.EXPENSE, 1.0, 75.32))
        assertEquals("€", german.currencySymbol)
    }

    @Test
    fun unknownCurrencyDoesNotCrash() {
        val odd = Fmt(Locale.US, metric.copy(currencyCode = "???"))
        assertTrue(odd.money(12.5).contains("12.50"))
        assertTrue(odd.money(12.5).contains("???"))
        assertEquals("???", odd.currencySymbol)
    }

    @Test
    fun datesAreFormatted() {
        val german = Fmt(Locale.GERMANY, metric)
        val text = german.date(LocalDate.of(2026, 11, 5).toEpochDay())
        assertTrue(text, text.contains("2026"))
        assertTrue(text, text.contains("11") || text.contains("Nov"))
        assertTrue(german.monthYear(2026, 11).contains("2026"))
        assertEquals(1, german.monthLetter(11).length)
    }
}

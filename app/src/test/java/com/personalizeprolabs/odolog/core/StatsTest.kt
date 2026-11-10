package com.personalizeprolabs.odolog.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private var nextId = 1L

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long = LocalDate.of(year, month, dayOfMonth).toEpochDay()

    private fun fuel(
        date: Long,
        odometer: Double?,
        liters: Double,
        cost: Double = 0.0,
        full: Boolean = true,
        missed: Boolean = false,
    ) = LogEntry(
        id = nextId++, type = EntryType.FUEL, dateEpochDay = date, odometerKm = odometer,
        quantity = liters, totalCost = cost, isFull = full, missedPrevious = missed,
    )

    private fun charge(date: Long, odometer: Double?, kwh: Double, cost: Double = 0.0, full: Boolean = true) = LogEntry(
        id = nextId++, type = EntryType.CHARGE, dateEpochDay = date, odometerKm = odometer,
        quantity = kwh, totalCost = cost, isFull = full,
    )

    private fun expense(date: Long, cost: Double, odometer: Double? = null) = LogEntry(
        id = nextId++, type = EntryType.EXPENSE, dateEpochDay = date, odometerKm = odometer,
        totalCost = cost, category = ExpenseCategory.SERVICE, title = "Service",
    )

    @Test
    fun fullToFull() {
        val entries = listOf(
            fuel(day(2026, 1, 1), 1000.0, 40.0, 70.0),
            fuel(day(2026, 1, 15), 1500.0, 35.0, 60.0),
        )
        val intervals = Stats.economyIntervals(entries, EntryType.FUEL)
        assertEquals(1, intervals.size)
        assertEquals(500.0, intervals[0].distanceKm, 1e-9)
        assertEquals(35.0, intervals[0].quantity, 1e-9)   // the first fill-up is not counted
        assertEquals(60.0, intervals[0].cost, 1e-9)
        assertEquals(entries[1].id, intervals[0].endEntryId)
        assertEquals(7.0, FuelEconomyUnit.L_PER_100KM.convert(intervals[0].quantity, intervals[0].distanceKm), 1e-9)
    }

    @Test
    fun orderOfTheListDoesNotMatter() {
        val a = fuel(day(2026, 1, 1), 1000.0, 40.0)
        val b = fuel(day(2026, 1, 15), 1500.0, 35.0)
        val c = fuel(day(2026, 2, 1), 2100.0, 45.0)
        val intervals = Stats.economyIntervals(listOf(c, a, b), EntryType.FUEL)
        assertEquals(2, intervals.size)
        assertEquals(500.0, intervals[0].distanceKm, 1e-9)
        assertEquals(600.0, intervals[1].distanceKm, 1e-9)
    }

    @Test
    fun partialFillUpsAreAddedToTheInterval() {
        val entries = listOf(
            fuel(day(2026, 1, 1), 1000.0, 40.0),
            fuel(day(2026, 1, 8), 1200.0, 10.0, 18.0, full = false),
            fuel(day(2026, 1, 12), null, 5.0, 9.0, full = false),       // odometer forgotten: still counted
            fuel(day(2026, 1, 20), 1600.0, 27.0, 50.0),
        )
        val intervals = Stats.economyIntervals(entries, EntryType.FUEL)
        assertEquals(1, intervals.size)
        assertEquals(600.0, intervals[0].distanceKm, 1e-9)
        assertEquals(42.0, intervals[0].quantity, 1e-9)
        assertEquals(77.0, intervals[0].cost, 1e-9)
    }

    @Test
    fun missedFillUpBreaksTheChain() {
        val entries = listOf(
            fuel(day(2026, 1, 1), 1000.0, 40.0),
            fuel(day(2026, 1, 15), 1500.0, 35.0, missed = true),
            fuel(day(2026, 2, 1), 2000.0, 30.0),
        )
        val intervals = Stats.economyIntervals(entries, EntryType.FUEL)
        assertEquals(1, intervals.size)
        assertEquals(500.0, intervals[0].distanceKm, 1e-9)
        assertEquals(30.0, intervals[0].quantity, 1e-9)
        assertEquals(entries[2].id, intervals[0].endEntryId)
    }

    @Test
    fun fullFillUpWithoutOdometerBreaksTheChain() {
        val entries = listOf(
            fuel(day(2026, 1, 1), 1000.0, 40.0),
            fuel(day(2026, 1, 15), null, 35.0),
            fuel(day(2026, 2, 1), 2000.0, 30.0),
            fuel(day(2026, 2, 15), 2400.0, 28.0),
        )
        val intervals = Stats.economyIntervals(entries, EntryType.FUEL)
        assertEquals(1, intervals.size)
        assertEquals(400.0, intervals[0].distanceKm, 1e-9)
        assertEquals(28.0, intervals[0].quantity, 1e-9)
    }

    @Test
    fun odometerGoingBackwardsGivesNoInterval() {
        val entries = listOf(
            fuel(day(2026, 1, 1), 1000.0, 40.0),
            fuel(day(2026, 1, 15), 900.0, 35.0),        // typing mistake
            fuel(day(2026, 2, 1), 1400.0, 30.0),
        )
        val intervals = Stats.economyIntervals(entries, EntryType.FUEL)
        assertEquals(1, intervals.size)
        assertEquals(500.0, intervals[0].distanceKm, 1e-9)
    }

    @Test
    fun fuelAndChargingAreSeparate() {
        val entries = listOf(
            fuel(day(2026, 1, 1), 1000.0, 40.0),
            charge(day(2026, 1, 2), 1050.0, 10.0),
            charge(day(2026, 1, 9), 1250.0, 36.0, 12.0),
            fuel(day(2026, 1, 15), 1500.0, 20.0),
        )
        val fuelIntervals = Stats.economyIntervals(entries, EntryType.FUEL)
        val chargeIntervals = Stats.economyIntervals(entries, EntryType.CHARGE)
        assertEquals(1, fuelIntervals.size)
        assertEquals(20.0, fuelIntervals[0].quantity, 1e-9)
        assertEquals(1, chargeIntervals.size)
        assertEquals(200.0, chargeIntervals[0].distanceKm, 1e-9)
        assertEquals(18.0, ElectricEconomyUnit.KWH_PER_100KM.convert(chargeIntervals[0].quantity, chargeIntervals[0].distanceKm), 1e-9)
    }

    @Test
    fun noDataGivesNoIntervals() {
        assertTrue(Stats.economyIntervals(emptyList(), EntryType.FUEL).isEmpty())
        val single = listOf(fuel(day(2026, 1, 1), 1000.0, 40.0))
        assertTrue(Stats.economyIntervals(single, EntryType.FUEL).isEmpty())
        assertFalse(EnergyStats(emptyList()).hasData)
        assertNull(EnergyStats(emptyList()).costPerKm)
    }

    @Test
    fun summaryOfAPeriod() {
        val entries = listOf(
            fuel(day(2025, 12, 20), 9500.0, 40.0, 70.0),
            fuel(day(2026, 1, 5), 10000.0, 35.0, 60.0),
            expense(day(2026, 1, 10), 250.0, 10100.0),
            fuel(day(2026, 1, 25), 10600.0, 42.0, 75.0),
            charge(day(2026, 2, 2), 10700.0, 20.0, 8.0),
        )
        val january = Stats.summarize(entries, day(2026, 1, 1), day(2026, 1, 31))
        assertEquals(3, january.entryCount)
        assertEquals(135.0, january.fuelCost, 1e-9)
        assertEquals(0.0, january.chargeCost, 1e-9)
        assertEquals(250.0, january.otherCost, 1e-9)
        assertEquals(385.0, january.totalCost, 1e-9)
        assertEquals(77.0, january.fuelLiters, 1e-9)
        assertEquals(600.0, january.distanceKm ?: -1.0, 1e-9)
        assertEquals(385.0 / 600.0, january.costPerKm ?: -1.0, 1e-9)
        // both intervals that end in January count, including the one that started in December
        assertEquals(2, january.fuelEconomy.intervals.size)
        assertEquals(1100.0, january.fuelEconomy.distanceKm, 1e-9)
        assertEquals(77.0, january.fuelEconomy.quantity, 1e-9)
        assertEquals(135.0 / 1100.0, january.fuelEconomy.costPerKm ?: -1.0, 1e-9)

        val all = Stats.summarize(entries, null, null)
        assertEquals(5, all.entryCount)
        assertEquals(463.0, all.totalCost, 1e-9)
        assertEquals(1200.0, all.distanceKm ?: -1.0, 1e-9)
        assertEquals(20.0, all.chargeKwh, 1e-9)
    }

    @Test
    fun summaryWithoutOdometerHasNoDistance() {
        val entries = listOf(expense(day(2026, 1, 10), 250.0), fuel(day(2026, 1, 12), 5000.0, 30.0, 50.0))
        val summary = Stats.summarize(entries, null, null)
        assertNull(summary.distanceKm)
        assertNull(summary.costPerKm)
        assertEquals(300.0, summary.totalCost, 1e-9)
    }

    @Test
    fun monthlyCosts() {
        val entries = listOf(
            fuel(day(2025, 11, 30), 100.0, 30.0, 50.0),      // outside the window
            fuel(day(2025, 12, 1), 500.0, 30.0, 55.0),
            expense(day(2026, 2, 14), 120.0),
            charge(day(2026, 2, 20), 900.0, 30.0, 10.0),
            fuel(day(2026, 3, 1), 1300.0, 30.0, 58.0),       // after the window
        )
        val months = Stats.monthlyCosts(entries, day(2026, 2, 28), 3)
        assertEquals(3, months.size)
        assertEquals(2025, months[0].year)
        assertEquals(12, months[0].month)
        assertEquals(55.0, months[0].energyCost, 1e-9)
        assertEquals(0.0, months[0].otherCost, 1e-9)
        assertEquals(1, months[1].month)
        assertEquals(0.0, months[1].total, 1e-9)
        assertEquals(2, months[2].month)
        assertEquals(10.0, months[2].energyCost, 1e-9)
        assertEquals(120.0, months[2].otherCost, 1e-9)
        assertEquals(130.0, months[2].total, 1e-9)
    }

    @Test
    fun odometerHelpers() {
        val entries = listOf(
            fuel(day(2026, 1, 1), 1000.0, 40.0),
            expense(day(2026, 1, 20), 99.0),
            fuel(day(2026, 1, 15), 1500.0, 35.0),
        )
        assertEquals(1500.0, Stats.latestOdometerKm(entries) ?: -1.0, 1e-9)
        assertNull(Stats.latestOdometerKm(listOf(expense(day(2026, 1, 20), 99.0))))
        assertEquals(1000.0, Stats.highestOdometerUntil(entries, day(2026, 1, 10), 0) ?: -1.0, 1e-9)
        assertEquals(1000.0, Stats.highestOdometerUntil(entries, day(2026, 1, 31), entries[2].id) ?: -1.0, 1e-9)
        assertNull(Stats.highestOdometerUntil(entries, day(2025, 12, 31), 0))
    }

    @Test
    fun newestFirstOrder() {
        val a = fuel(day(2026, 1, 1), 1000.0, 40.0)
        val b = fuel(day(2026, 1, 15), 1500.0, 35.0)
        val c = expense(day(2026, 1, 15), 10.0)
        val sorted = listOf(a, b, c).sortedWith(Stats.NEWEST_FIRST)
        assertEquals(listOf(c.id, b.id, a.id), sorted.map { it.id })
    }
}

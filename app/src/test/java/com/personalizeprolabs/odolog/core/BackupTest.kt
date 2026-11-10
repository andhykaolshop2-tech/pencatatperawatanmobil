package com.personalizeprolabs.odolog.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private fun day(year: Int, month: Int, dayOfMonth: Int): Long = LocalDate.of(year, month, dayOfMonth).toEpochDay()

    private val metric = AppSettings(
        vehicleName = "Golf", powertrain = Powertrain.HYBRID, distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.LITER, fuelEconomyUnit = FuelEconomyUnit.L_PER_100KM,
        electricEconomyUnit = ElectricEconomyUnit.KWH_PER_100KM, currencyCode = "EUR",
    )
    private val american = metric.copy(
        distanceUnit = DistanceUnit.MI, volumeUnit = VolumeUnit.GALLON_US,
        fuelEconomyUnit = FuelEconomyUnit.MPG_US, electricEconomyUnit = ElectricEconomyUnit.MI_PER_KWH,
        currencyCode = "USD",
    )

    private val sample = BackupData(
        vehicle = VehicleInfo("Golf \"GTE\", blau", Powertrain.HYBRID),
        entries = listOf(
            LogEntry(type = EntryType.FUEL, dateEpochDay = day(2026, 3, 1), odometerKm = 45210.0, quantity = 42.1,
                totalCost = 75.32, isFull = true, missedPrevious = false, note = "Autobahn, Super E10"),
            LogEntry(type = EntryType.FUEL, dateEpochDay = day(2026, 3, 9), odometerKm = null, quantity = 12.0,
                totalCost = 21.5, isFull = false, missedPrevious = true),
            LogEntry(type = EntryType.CHARGE, dateEpochDay = day(2026, 3, 10), odometerKm = 45390.5, quantity = 9.874,
                totalCost = 0.0, isFull = true),
            LogEntry(type = EntryType.EXPENSE, dateEpochDay = day(2026, 3, 15), odometerKm = 45500.0, totalCost = 289.9,
                category = ExpenseCategory.TIRES, title = "Sommerreifen", note = "Zeile 1\nZeile 2"),
            LogEntry(type = EntryType.EXPENSE, dateEpochDay = day(2026, 3, 20), totalCost = 0.0,
                category = ExpenseCategory.OTHER, title = ""),
        ),
        reminders = listOf(
            Reminder(title = "Hauptuntersuchung", kind = ReminderKind.INSPECTION, dueEpochDay = day(2027, 6, 30),
                repeatMonths = 24, notifyDaysBefore = 30, note = "Termin früh buchen"),
            Reminder(title = "Service", kind = ReminderKind.SERVICE, dueEpochDay = day(2026, 11, 1),
                repeatMonths = 0, notifyDaysBefore = 0),
        ),
    )

    private fun assertSameData(expected: BackupData, actual: BackupData) {
        assertEquals(expected.vehicle, actual.vehicle)
        assertEquals(expected.entries.size, actual.entries.size)
        for (i in expected.entries.indices) {
            val e = expected.entries[i]
            val a = actual.entries[i]
            val where = "entry $i"
            assertEquals(where, e.type, a.type)
            assertEquals(where, e.dateEpochDay, a.dateEpochDay)
            assertEquals(where, e.odometerKm == null, a.odometerKm == null)
            assertEquals(where, e.odometerKm ?: 0.0, a.odometerKm ?: 0.0, 0.1)
            assertEquals(where, e.quantity, a.quantity, 0.003)
            assertEquals(where, e.totalCost, a.totalCost, 1e-9)
            assertEquals(where, e.isFull, a.isFull)
            assertEquals(where, e.missedPrevious, a.missedPrevious)
            assertEquals(where, e.category, a.category)
            assertEquals(where, e.title, a.title)
            assertEquals(where, e.note, a.note)
        }
        // reminders are written sorted by due date
        assertEquals(expected.reminders.sortedWith(ReminderLogic.BY_DUE_DATE), actual.reminders)
    }

    @Test
    fun roundTripMetric() {
        val csv = Backup.exportCsv(sample, metric)
        val result = Backup.importCsv(csv, slashDatesMonthFirst = false)
        assertTrue(result.recognized)
        assertEquals(0, result.skippedRows)
        assertSameData(sample, result.data)
    }

    @Test
    fun roundTripMilesAndGallons() {
        val csv = Backup.exportCsv(sample, american)
        assertTrue(csv.contains(",mi,"))
        assertTrue(csv.contains(",gal_us,"))
        val result = Backup.importCsv(csv, slashDatesMonthFirst = true)
        assertTrue(result.recognized)
        assertEquals(0, result.skippedRows)
        assertSameData(sample, result.data)
    }

    @Test
    fun roundTripThroughBytes() {
        val bytes = Csv.encode(Backup.exportCsv(sample, metric))
        val result = Backup.importCsv(Csv.decode(bytes), slashDatesMonthFirst = false)
        assertSameData(sample, result.data)
    }

    @Test
    fun exportFormatIsStable() {
        val data = BackupData(
            vehicle = VehicleInfo("My car", Powertrain.COMBUSTION),
            entries = listOf(
                LogEntry(type = EntryType.FUEL, dateEpochDay = day(2026, 3, 1), odometerKm = 45210.0,
                    quantity = 42.1, totalCost = 75.32),
            ),
            reminders = listOf(Reminder(title = "MOT", kind = ReminderKind.INSPECTION, dueEpochDay = day(2027, 6, 30),
                repeatMonths = 12, notifyDaysBefore = 30)),
        )
        val expected = "record,date,odometer,distance_unit,quantity,quantity_unit,total_cost,currency,full," +
            "missed_previous,category,title,note,repeat_months,notify_days_before\r\n" +
            "vehicle,,,,,,,,,,combustion,My car,,,\r\n" +
            "fuel,2026-03-01,45210,km,42.1,l,75.32,EUR,1,0,,,,,\r\n" +
            "reminder,2027-06-30,,,,,,,,,inspection,MOT,,12,30\r\n"
        assertEquals(expected, Backup.exportCsv(data, metric))
    }

    @Test
    fun importsFileSavedByAGermanSpreadsheet() {
        val csv = "record;date;odometer;distance_unit;quantity;quantity_unit;total_cost;currency;full;missed_previous;category;title;note\r\n" +
            "fuel;01.03.2026;45.210,5;km;42,10;l;75,32;EUR;1;0;;;\r\n" +
            "charge;9.3.26;;;9,874;kwh;0;EUR;WAHR;FALSCH;;;\r\n" +
            "expense;15.03.2026;45500;km;;;1.289,90;EUR;;;tyres;Sommerreifen;\r\n"
        val result = Backup.importCsv(csv, slashDatesMonthFirst = false)
        assertTrue(result.recognized)
        assertEquals(0, result.skippedRows)
        val entries = result.data.entries
        assertEquals(3, entries.size)
        assertEquals(day(2026, 3, 1), entries[0].dateEpochDay)
        assertEquals(45210.5, entries[0].odometerKm ?: -1.0, 1e-9)
        assertEquals(42.1, entries[0].quantity, 1e-9)
        assertEquals(75.32, entries[0].totalCost, 1e-9)
        assertEquals(day(2026, 3, 9), entries[1].dateEpochDay)
        assertNull(entries[1].odometerKm)
        assertEquals(9.874, entries[1].quantity, 1e-9)
        assertTrue(entries[1].isFull)
        assertFalse(entries[1].missedPrevious)
        assertEquals(1289.9, entries[2].totalCost, 1e-9)
        assertEquals(ExpenseCategory.TIRES, entries[2].category)
    }

    @Test
    fun convertsUnitsOfEveryRow() {
        val csv = "record,date,odometer,distance_unit,quantity,quantity_unit,total_cost\n" +
            "fuel,2026-03-01,1000,mi,10,gal_us,40\n" +
            "fuel,2026-03-02,1000,km,10,gal_uk,40\n" +
            "fuel,2026-03-03,1000,,10,,40\n"
        val entries = Backup.importCsv(csv, slashDatesMonthFirst = false).data.entries
        assertEquals(1609.344, entries[0].odometerKm ?: -1.0, 1e-9)
        assertEquals(37.85411784, entries[0].quantity, 1e-9)
        assertEquals(45.4609, entries[1].quantity, 1e-9)
        assertEquals(1000.0, entries[2].odometerKm ?: -1.0, 1e-9)      // km and litres are the defaults
        assertEquals(10.0, entries[2].quantity, 1e-9)
    }

    @Test
    fun slashDates() {
        val header = "record,date,quantity,quantity_unit\n"
        // nothing in the file decides the order -> the device hint is used
        val ambiguous = header + "fuel,03/04/2026,10,l\n"
        assertEquals(day(2026, 4, 3), Backup.importCsv(ambiguous, slashDatesMonthFirst = false).data.entries[0].dateEpochDay)
        assertEquals(day(2026, 3, 4), Backup.importCsv(ambiguous, slashDatesMonthFirst = true).data.entries[0].dateEpochDay)
        // one unambiguous date decides for the whole file
        val american = header + "fuel,03/04/2026,10,l\nfuel,12/31/2026,10,l\n"
        assertEquals(day(2026, 3, 4), Backup.importCsv(american, slashDatesMonthFirst = false).data.entries[0].dateEpochDay)
        val british = header + "fuel,03/04/2026,10,l\nfuel,31/12/2026,10,l\n"
        assertEquals(day(2026, 4, 3), Backup.importCsv(british, slashDatesMonthFirst = true).data.entries[0].dateEpochDay)
    }

    @Test
    fun parseDate() {
        assertEquals(day(2026, 11, 5), Backup.parseDate("2026-11-05", true))
        assertEquals(day(2026, 11, 5), Backup.parseDate("2026-11-05 00:00:00", true))
        assertEquals(day(2026, 11, 5), Backup.parseDate("2026/11/5", true))
        assertEquals(day(2026, 11, 5), Backup.parseDate("5.11.2026", true))
        assertEquals(day(2026, 11, 5), Backup.parseDate("05/11/2026", true))
        assertEquals(day(2026, 11, 5), Backup.parseDate("11/05/2026", false))
        assertNull(Backup.parseDate("", true))
        assertNull(Backup.parseDate("31.02.2026", true))
        assertNull(Backup.parseDate("yesterday", true))
    }

    @Test
    fun badRowsAreSkippedAndCounted() {
        val csv = "record,date,quantity,quantity_unit,total_cost,title\n" +
            "fuel,2026-03-01,40,l,70,\n" +
            "fuel,not a date,40,l,70,\n" +       // bad date
            "fuel,2026-03-02,,l,70,\n" +          // no quantity
            "fuel,2026-03-03,-5,l,70,\n" +        // negative quantity
            "banana,2026-03-04,,,,\n" +           // unknown record
            "reminder,2026-03-05,,,,\n" +         // reminder without title
            ",,,,,\n" +                           // blank row: ignored, not counted
            "expense,2026-03-06,,,12.5,Parking\n"
        val result = Backup.importCsv(csv, slashDatesMonthFirst = false)
        assertTrue(result.recognized)
        assertEquals(5, result.skippedRows)
        assertEquals(2, result.data.entries.size)
        assertEquals(12.5, result.data.entries[1].totalCost, 1e-9)
        assertEquals("Parking", result.data.entries[1].title)
    }

    @Test
    fun otherFilesAreNotRecognized() {
        assertFalse(Backup.importCsv("", false).recognized)
        assertFalse(Backup.importCsv("name,amount\nx,1\n", false).recognized)
        assertFalse(Backup.importCsv("just some text", false).recognized)
        val headerOnly = Backup.importCsv("record,date\n", false)
        assertTrue(headerOnly.recognized)
        assertTrue(headerOnly.data.entries.isEmpty())
    }

    @Test
    fun plainNumbers() {
        assertEquals("45210", Backup.plain(45210.0, 1))
        assertEquals("45210.5", Backup.plain(45210.54, 1))
        assertEquals("0", Backup.plain(0.0, 3))
        assertEquals("1.01", Backup.plain(1.005, 2))
        assertEquals("28092.2", Backup.plain(DistanceUnit.MI.fromKm(45210.0), 1))
        assertEquals("", Backup.plain(Double.NaN, 2))
    }

    @Test
    fun duplicatesAreLeftOut() {
        val existing = sample.entries.mapIndexed { index, entry -> entry.copy(id = index + 1L) }
        // importing the same file again adds nothing
        val again = Backup.importCsv(Backup.exportCsv(sample, american), slashDatesMonthFirst = true).data
        assertTrue(Backup.newEntries(existing, again.entries).isEmpty())
        assertTrue(Backup.newReminders(sample.reminders, again.reminders).isEmpty())

        val fresh = LogEntry(type = EntryType.FUEL, dateEpochDay = day(2026, 4, 1), odometerKm = 46000.0, quantity = 40.0, totalCost = 70.0)
        val added = Backup.newEntries(existing, again.entries + fresh + fresh)
        assertEquals(listOf(fresh), added)

        val reminder = Reminder(title = "  service ", kind = ReminderKind.SERVICE, dueEpochDay = day(2026, 11, 1))
        assertTrue(Backup.newReminders(sample.reminders, listOf(reminder)).isEmpty())
        assertEquals(1, Backup.newReminders(sample.reminders, listOf(reminder.copy(dueEpochDay = day(2026, 12, 1)))).size)
    }
}

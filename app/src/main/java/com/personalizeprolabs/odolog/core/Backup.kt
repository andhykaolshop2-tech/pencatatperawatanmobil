package com.personalizeprolabs.odolog.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

data class BackupData(
    val vehicle: VehicleInfo?,
    val entries: List<LogEntry>,
    val reminders: List<Reminder>,
)

data class ImportResult(
    val data: BackupData,
    /** Data rows that could not be understood and were left out. */
    val skippedRows: Int,
    /** False when the file does not look like an Odolog CSV file at all. */
    val recognized: Boolean,
)

/**
 * The CSV backup format ("one file holds everything").
 *
 * One header row, then one row per record. The `record` column says what the row is:
 *   vehicle   title = vehicle name, category = combustion | electric | hybrid
 *   fuel      a fill-up
 *   charge    a charging session
 *   expense   a service or other cost, category = service | repair | inspection | ...
 *   reminder  date = due date, category = service | inspection | other
 *
 * Every row carries its own units (`distance_unit` km|mi, `quantity_unit` l|gal_us|gal_uk|kwh),
 * so a file can be imported on a phone that uses other units. Numbers use a decimal point,
 * dates are ISO (yyyy-MM-dd). Unknown columns are ignored and missing columns get default
 * values, which keeps old and new versions of the app compatible with each other.
 *
 * The importer is deliberately tolerant, because people open such files in spreadsheet
 * programs and save them again: semicolon or tab as delimiter, decimal commas and dates
 * like 31.12.2026 or 12/31/2026 are understood as well.
 */
object Backup {
    const val COL_RECORD = "record"
    const val COL_DATE = "date"
    const val COL_ODOMETER = "odometer"
    const val COL_DISTANCE_UNIT = "distance_unit"
    const val COL_QUANTITY = "quantity"
    const val COL_QUANTITY_UNIT = "quantity_unit"
    const val COL_TOTAL_COST = "total_cost"
    const val COL_CURRENCY = "currency"
    const val COL_FULL = "full"
    const val COL_MISSED_PREVIOUS = "missed_previous"
    const val COL_CATEGORY = "category"
    const val COL_TITLE = "title"
    const val COL_NOTE = "note"
    const val COL_REPEAT_MONTHS = "repeat_months"
    const val COL_NOTIFY_DAYS_BEFORE = "notify_days_before"

    val HEADER: List<String> = listOf(
        COL_RECORD, COL_DATE, COL_ODOMETER, COL_DISTANCE_UNIT, COL_QUANTITY, COL_QUANTITY_UNIT,
        COL_TOTAL_COST, COL_CURRENCY, COL_FULL, COL_MISSED_PREVIOUS, COL_CATEGORY, COL_TITLE,
        COL_NOTE, COL_REPEAT_MONTHS, COL_NOTIFY_DAYS_BEFORE,
    )

    const val RECORD_VEHICLE = "vehicle"
    const val RECORD_REMINDER = "reminder"
    const val UNIT_KWH = "kwh"

    private val EMPTY = BackupData(null, emptyList(), emptyList())

    // ------------------------------------------------------------------ export

    /** Builds the CSV text. Values are written in the units the user has chosen. */
    fun exportCsv(data: BackupData, settings: AppSettings): String {
        val rows = ArrayList<List<String>>()
        rows.add(HEADER)
        val vehicle = data.vehicle
        if (vehicle != null) {
            rows.add(row(record = RECORD_VEHICLE, category = vehicle.powertrain.code, title = vehicle.name))
        }
        val distanceUnit = settings.distanceUnit
        val volumeUnit = settings.volumeUnit
        for (e in data.entries.sortedWith(Stats.CHRONOLOGICAL)) {
            val odometer = e.odometerKm
            val isEnergy = e.type != EntryType.EXPENSE
            rows.add(
                row(
                    record = e.type.code,
                    date = LocalDate.ofEpochDay(e.dateEpochDay).toString(),
                    odometer = if (odometer != null) plain(distanceUnit.fromKm(odometer), 1) else "",
                    distanceUnit = if (odometer != null) distanceUnit.code else "",
                    quantity = when (e.type) {
                        EntryType.FUEL -> plain(volumeUnit.fromLiters(e.quantity), 3)
                        EntryType.CHARGE -> plain(e.quantity, 3)
                        EntryType.EXPENSE -> ""
                    },
                    quantityUnit = when (e.type) {
                        EntryType.FUEL -> volumeUnit.code
                        EntryType.CHARGE -> UNIT_KWH
                        EntryType.EXPENSE -> ""
                    },
                    totalCost = plain(e.totalCost, 4),
                    currency = settings.currencyCode,
                    full = if (isEnergy) flag(e.isFull) else "",
                    missedPrevious = if (isEnergy) flag(e.missedPrevious) else "",
                    category = if (e.type == EntryType.EXPENSE) e.category.code else "",
                    title = if (e.type == EntryType.EXPENSE) e.title else "",
                    note = e.note,
                )
            )
        }
        for (r in data.reminders.sortedWith(ReminderLogic.BY_DUE_DATE)) {
            rows.add(
                row(
                    record = RECORD_REMINDER,
                    date = LocalDate.ofEpochDay(r.dueEpochDay).toString(),
                    category = r.kind.code,
                    title = r.title,
                    note = r.note,
                    repeatMonths = r.repeatMonths.toString(),
                    notifyDaysBefore = r.notifyDaysBefore.toString(),
                )
            )
        }
        return Csv.write(rows)
    }

    private fun row(
        record: String,
        date: String = "",
        odometer: String = "",
        distanceUnit: String = "",
        quantity: String = "",
        quantityUnit: String = "",
        totalCost: String = "",
        currency: String = "",
        full: String = "",
        missedPrevious: String = "",
        category: String = "",
        title: String = "",
        note: String = "",
        repeatMonths: String = "",
        notifyDaysBefore: String = "",
    ): List<String> = listOf(
        record, date, odometer, distanceUnit, quantity, quantityUnit, totalCost, currency,
        full, missedPrevious, category, title, note, repeatMonths, notifyDaysBefore,
    )

    private fun flag(value: Boolean): String = if (value) "1" else "0"

    /** Plain decimal text with a decimal point, at most [decimals] decimals, no trailing zeros. */
    fun plain(value: Double, decimals: Int): String {
        if (value.isNaN() || value.isInfinite()) return ""
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    // ------------------------------------------------------------------ import

    /**
     * @param slashDatesMonthFirst how to read a date like 03/04/2026 when the file itself
     *   gives no hint (true on US devices, false elsewhere).
     */
    fun importCsv(text: String, slashDatesMonthFirst: Boolean): ImportResult {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return ImportResult(EMPTY, 0, false)

        val columns = HashMap<String, Int>()
        val header = rows[0]
        for (i in header.indices) {
            val name = header[i].trim().lowercase(Locale.ROOT)
            if (!columns.containsKey(name)) columns[name] = i
        }
        if (!columns.containsKey(COL_RECORD) || !columns.containsKey(COL_DATE)) {
            return ImportResult(EMPTY, rows.size - 1, false)
        }
        fun cell(row: List<String>, name: String): String {
            val index = columns[name] ?: return ""
            return if (index < row.size) row[index].trim() else ""
        }

        // Decide once per file whether 03/04/2026 means 3 April or March 4.
        var dayFirst = !slashDatesMonthFirst
        for (r in 1 until rows.size) {
            val match = SLASH_DATE.matchEntire(cell(rows[r], COL_DATE)) ?: continue
            val a = match.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            if (a > 12) {
                dayFirst = true
                break
            }
            if (b > 12) {
                dayFirst = false
                break
            }
        }

        var vehicle: VehicleInfo? = null
        val entries = ArrayList<LogEntry>()
        val reminders = ArrayList<Reminder>()
        var skipped = 0

        for (r in 1 until rows.size) {
            val row = rows[r]
            if (row.all { it.isBlank() }) continue
            val record = cell(row, COL_RECORD).lowercase(Locale.ROOT)

            if (record == RECORD_VEHICLE) {
                if (vehicle == null) {
                    vehicle = VehicleInfo(
                        name = cell(row, COL_TITLE),
                        powertrain = Powertrain.fromCode(cell(row, COL_CATEGORY)) ?: Powertrain.COMBUSTION,
                    )
                }
                continue
            }

            val date = parseDate(cell(row, COL_DATE), dayFirst)
            if (date == null) {
                skipped++
                continue
            }

            if (record == RECORD_REMINDER) {
                val title = cell(row, COL_TITLE)
                if (title.isEmpty()) {
                    skipped++
                    continue
                }
                reminders.add(
                    Reminder(
                        title = title,
                        kind = ReminderKind.fromCode(cell(row, COL_CATEGORY)) ?: ReminderKind.OTHER,
                        dueEpochDay = date,
                        repeatMonths = (wholeNumber(cell(row, COL_REPEAT_MONTHS)) ?: 0).coerceIn(0, 600),
                        notifyDaysBefore = (wholeNumber(cell(row, COL_NOTIFY_DAYS_BEFORE)) ?: 14).coerceIn(0, 365),
                        note = cell(row, COL_NOTE),
                    )
                )
                continue
            }

            val type = EntryType.fromCode(record)
            if (type == null) {
                skipped++
                continue
            }
            val odometerRaw = number(cell(row, COL_ODOMETER))
            val odometerKm = if (odometerRaw != null) {
                (DistanceUnit.fromCode(cell(row, COL_DISTANCE_UNIT)) ?: DistanceUnit.KM).toKm(odometerRaw)
            } else {
                null
            }
            val cost = number(cell(row, COL_TOTAL_COST)) ?: 0.0
            val note = cell(row, COL_NOTE)

            if (type == EntryType.EXPENSE) {
                entries.add(
                    LogEntry(
                        type = type,
                        dateEpochDay = date,
                        odometerKm = odometerKm,
                        totalCost = cost,
                        category = ExpenseCategory.fromCode(cell(row, COL_CATEGORY)) ?: ExpenseCategory.OTHER,
                        title = cell(row, COL_TITLE),
                        note = note,
                    )
                )
                continue
            }

            val quantityRaw = number(cell(row, COL_QUANTITY))
            if (quantityRaw == null || quantityRaw <= 0.0) {
                skipped++
                continue
            }
            val quantity = if (type == EntryType.FUEL) {
                (VolumeUnit.fromCode(cell(row, COL_QUANTITY_UNIT)) ?: VolumeUnit.LITER).toLiters(quantityRaw)
            } else {
                quantityRaw
            }
            entries.add(
                LogEntry(
                    type = type,
                    dateEpochDay = date,
                    odometerKm = odometerKm,
                    quantity = quantity,
                    totalCost = cost,
                    isFull = parseFlag(cell(row, COL_FULL), true),
                    missedPrevious = parseFlag(cell(row, COL_MISSED_PREVIOUS), false),
                    note = note,
                )
            )
        }
        return ImportResult(BackupData(vehicle, entries, reminders), skipped, true)
    }

    private val ISO_DATE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})(?:[T ].*)?$""")
    private val YMD_SLASH_DATE = Regex("""^(\d{4})/(\d{1,2})/(\d{1,2})$""")
    private val DOT_DATE = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{2}|\d{4})$""")
    private val SLASH_DATE = Regex("""^(\d{1,2})/(\d{1,2})/(\d{2}|\d{4})$""")

    /** Returns the epoch day, or null when the text is not a date. */
    fun parseDate(text: String, slashDayFirst: Boolean): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        ISO_DATE.matchEntire(t)?.let { m -> return epochDay(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
        YMD_SLASH_DATE.matchEntire(t)?.let { m -> return epochDay(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
        DOT_DATE.matchEntire(t)?.let { m -> return epochDay(m.groupValues[3], m.groupValues[2], m.groupValues[1]) }
        SLASH_DATE.matchEntire(t)?.let { m ->
            return if (slashDayFirst) {
                epochDay(m.groupValues[3], m.groupValues[2], m.groupValues[1])
            } else {
                epochDay(m.groupValues[3], m.groupValues[1], m.groupValues[2])
            }
        }
        return null
    }

    private fun epochDay(year: String, month: String, day: String): Long? {
        var y = year.toInt()
        if (year.length == 2) y += 2000
        return try {
            LocalDate.of(y, month.toInt(), day.toInt()).toEpochDay()
        } catch (e: DateTimeException) {
            null
        }
    }

    /** A non-negative number written with a decimal point or a decimal comma. */
    private fun number(text: String): Double? {
        val t = text.replace(" ", "").replace("\u00A0", "").replace("\u202F", "")
        if (t.isEmpty()) return null
        val lastComma = t.lastIndexOf(',')
        val lastDot = t.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            lastComma >= 0 ->
                if (t.indexOf(',') == lastComma) t.replace(',', '.') else t.replace(",", "")
            lastDot >= 0 ->
                if (t.indexOf('.') == lastDot) t else t.replace(".", "")
            else -> t
        }
        for (ch in normalized) {
            if (!(ch in '0'..'9' || ch == '.')) return null
        }
        val value = normalized.toDoubleOrNull() ?: return null
        return if (value.isNaN() || value.isInfinite() || value < 0.0) null else value
    }

    private fun wholeNumber(text: String): Int? {
        val value = number(text) ?: return null
        return if (value > 100000.0) null else value.toInt()
    }

    private fun parseFlag(text: String, default: Boolean): Boolean = when (text.lowercase(Locale.ROOT)) {
        "1", "true", "yes", "y", "x", "ja", "j", "wahr" -> true
        "0", "false", "no", "n", "nein", "falsch" -> false
        else -> default
    }

    // -------------------------------------------------------------- duplicates

    /** Entries of [incoming] that are not already in [existing] (and not repeated in [incoming]). */
    fun newEntries(existing: List<LogEntry>, incoming: List<LogEntry>): List<LogEntry> {
        val byDay = HashMap<Long, ArrayList<LogEntry>>()
        for (e in existing) byDay.getOrPut(e.dateEpochDay) { ArrayList() }.add(e)
        val result = ArrayList<LogEntry>()
        for (e in incoming) {
            val sameDay = byDay.getOrPut(e.dateEpochDay) { ArrayList() }
            if (sameDay.none { sameEntry(it, e) }) {
                sameDay.add(e)
                result.add(e)
            }
        }
        return result
    }

    private fun sameEntry(a: LogEntry, b: LogEntry): Boolean {
        if (a.type != b.type || a.dateEpochDay != b.dateEpochDay) return false
        val oa = a.odometerKm
        val ob = b.odometerKm
        if ((oa == null) != (ob == null)) return false
        if (oa != null && ob != null && abs(oa - ob) > 0.5) return false
        if (abs(a.quantity - b.quantity) > 0.01) return false
        if (abs(a.totalCost - b.totalCost) > 0.005) return false
        if (a.type == EntryType.EXPENSE) {
            if (a.category != b.category || a.title.trim() != b.title.trim()) return false
        }
        return true
    }

    /** Reminders of [incoming] that are not already in [existing]. */
    fun newReminders(existing: List<Reminder>, incoming: List<Reminder>): List<Reminder> {
        val known = ArrayList(existing)
        val result = ArrayList<Reminder>()
        for (r in incoming) {
            val duplicate = known.any {
                it.dueEpochDay == r.dueEpochDay && it.kind == r.kind && it.title.trim().equals(r.title.trim(), ignoreCase = true)
            }
            if (!duplicate) {
                known.add(r)
                result.add(r)
            }
        }
        return result
    }
}

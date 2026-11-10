package com.personalizeprolabs.odolog.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.ExpenseCategory
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.Reminder
import com.personalizeprolabs.odolog.core.ReminderKind

/**
 * The local SQLite database (plain SQLiteOpenHelper, no extra library).
 *
 * All methods block; call them from a background thread. Use them through [Repository].
 *
 * Both tables already have a `vehicle_id` column (always 1 in version 1 of the app), so
 * that support for several vehicles can be added later without rewriting the tables.
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_ENTRIES (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "vehicle_id INTEGER NOT NULL DEFAULT 1, " +
                "type TEXT NOT NULL, " +
                "date INTEGER NOT NULL, " +
                "odometer_km REAL, " +
                "quantity REAL NOT NULL DEFAULT 0, " +
                "total_cost REAL NOT NULL DEFAULT 0, " +
                "is_full INTEGER NOT NULL DEFAULT 1, " +
                "missed_previous INTEGER NOT NULL DEFAULT 0, " +
                "category TEXT NOT NULL DEFAULT 'other', " +
                "title TEXT NOT NULL DEFAULT '', " +
                "note TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL("CREATE INDEX idx_entries_date ON $TABLE_ENTRIES (date)")
        db.execSQL(
            "CREATE TABLE $TABLE_REMINDERS (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "vehicle_id INTEGER NOT NULL DEFAULT 1, " +
                "title TEXT NOT NULL, " +
                "kind TEXT NOT NULL DEFAULT 'service', " +
                "due_date INTEGER NOT NULL, " +
                "repeat_months INTEGER NOT NULL DEFAULT 0, " +
                "notify_days_before INTEGER NOT NULL DEFAULT 14, " +
                "note TEXT NOT NULL DEFAULT '', " +
                "notified_stage INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first schema. Later versions must migrate step by step
        // (ALTER TABLE ...) and must never drop the user's data.
    }

    // ---------------------------------------------------------------- entries

    fun loadEntries(): List<LogEntry> {
        val result = ArrayList<LogEntry>()
        readableDatabase.query(TABLE_ENTRIES, null, null, null, null, null, null).use { c ->
            val iId = c.getColumnIndexOrThrow("id")
            val iType = c.getColumnIndexOrThrow("type")
            val iDate = c.getColumnIndexOrThrow("date")
            val iOdometer = c.getColumnIndexOrThrow("odometer_km")
            val iQuantity = c.getColumnIndexOrThrow("quantity")
            val iCost = c.getColumnIndexOrThrow("total_cost")
            val iFull = c.getColumnIndexOrThrow("is_full")
            val iMissed = c.getColumnIndexOrThrow("missed_previous")
            val iCategory = c.getColumnIndexOrThrow("category")
            val iTitle = c.getColumnIndexOrThrow("title")
            val iNote = c.getColumnIndexOrThrow("note")
            while (c.moveToNext()) {
                val type = EntryType.fromCode(c.getString(iType)) ?: continue
                result.add(
                    LogEntry(
                        id = c.getLong(iId),
                        type = type,
                        dateEpochDay = c.getLong(iDate),
                        odometerKm = if (c.isNull(iOdometer)) null else c.getDouble(iOdometer),
                        quantity = c.getDouble(iQuantity),
                        totalCost = c.getDouble(iCost),
                        isFull = c.getInt(iFull) != 0,
                        missedPrevious = c.getInt(iMissed) != 0,
                        category = ExpenseCategory.fromCode(c.getString(iCategory)) ?: ExpenseCategory.OTHER,
                        title = c.getString(iTitle) ?: "",
                        note = c.getString(iNote) ?: "",
                    )
                )
            }
        }
        return result
    }

    /** Inserts the entry (id 0) or updates it. Returns the row id. */
    fun saveEntry(entry: LogEntry): Long {
        val db = writableDatabase
        val values = entryValues(entry)
        if (entry.id == 0L) return db.insertOrThrow(TABLE_ENTRIES, null, values)
        db.update(TABLE_ENTRIES, values, "id = ?", arrayOf(entry.id.toString()))
        return entry.id
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete(TABLE_ENTRIES, "id = ?", arrayOf(id.toString()))
    }

    private fun entryValues(entry: LogEntry): ContentValues {
        val values = ContentValues()
        values.put("type", entry.type.code)
        values.put("date", entry.dateEpochDay)
        val odometer = entry.odometerKm
        if (odometer != null) values.put("odometer_km", odometer) else values.putNull("odometer_km")
        values.put("quantity", entry.quantity)
        values.put("total_cost", entry.totalCost)
        values.put("is_full", if (entry.isFull) 1 else 0)
        values.put("missed_previous", if (entry.missedPrevious) 1 else 0)
        values.put("category", entry.category.code)
        values.put("title", entry.title)
        values.put("note", entry.note)
        return values
    }

    // -------------------------------------------------------------- reminders

    fun loadReminders(): List<Reminder> {
        val result = ArrayList<Reminder>()
        readableDatabase.query(TABLE_REMINDERS, null, null, null, null, null, null).use { c ->
            val iId = c.getColumnIndexOrThrow("id")
            val iTitle = c.getColumnIndexOrThrow("title")
            val iKind = c.getColumnIndexOrThrow("kind")
            val iDue = c.getColumnIndexOrThrow("due_date")
            val iRepeat = c.getColumnIndexOrThrow("repeat_months")
            val iNotify = c.getColumnIndexOrThrow("notify_days_before")
            val iNote = c.getColumnIndexOrThrow("note")
            val iStage = c.getColumnIndexOrThrow("notified_stage")
            while (c.moveToNext()) {
                result.add(
                    Reminder(
                        id = c.getLong(iId),
                        title = c.getString(iTitle) ?: "",
                        kind = ReminderKind.fromCode(c.getString(iKind)) ?: ReminderKind.OTHER,
                        dueEpochDay = c.getLong(iDue),
                        repeatMonths = c.getInt(iRepeat),
                        notifyDaysBefore = c.getInt(iNotify),
                        note = c.getString(iNote) ?: "",
                        notifiedStage = c.getInt(iStage),
                    )
                )
            }
        }
        return result
    }

    /** Inserts the reminder (id 0) or updates it. Returns the row id. */
    fun saveReminder(reminder: Reminder): Long {
        val db = writableDatabase
        val values = reminderValues(reminder)
        if (reminder.id == 0L) return db.insertOrThrow(TABLE_REMINDERS, null, values)
        db.update(TABLE_REMINDERS, values, "id = ?", arrayOf(reminder.id.toString()))
        return reminder.id
    }

    fun deleteReminder(id: Long) {
        writableDatabase.delete(TABLE_REMINDERS, "id = ?", arrayOf(id.toString()))
    }

    fun setReminderStage(id: Long, stage: Int) {
        val values = ContentValues()
        values.put("notified_stage", stage)
        writableDatabase.update(TABLE_REMINDERS, values, "id = ?", arrayOf(id.toString()))
    }

    private fun reminderValues(reminder: Reminder): ContentValues {
        val values = ContentValues()
        values.put("title", reminder.title)
        values.put("kind", reminder.kind.code)
        values.put("due_date", reminder.dueEpochDay)
        values.put("repeat_months", reminder.repeatMonths)
        values.put("notify_days_before", reminder.notifyDaysBefore)
        values.put("note", reminder.note)
        values.put("notified_stage", reminder.notifiedStage)
        return values
    }

    // ------------------------------------------------------------------- bulk

    /**
     * Inserts many rows in one transaction (CSV import). With [deleteExisting] the old
     * data is removed in the same transaction, so a failed import never leaves the
     * database half empty.
     */
    fun insertAll(entries: List<LogEntry>, reminders: List<Reminder>, deleteExisting: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (deleteExisting) {
                db.delete(TABLE_ENTRIES, null, null)
                db.delete(TABLE_REMINDERS, null, null)
            }
            for (entry in entries) db.insertOrThrow(TABLE_ENTRIES, null, entryValues(entry))
            for (reminder in reminders) db.insertOrThrow(TABLE_REMINDERS, null, reminderValues(reminder))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteAll() {
        insertAll(emptyList(), emptyList(), deleteExisting = true)
    }

    companion object {
        const val DATABASE_NAME = "odolog.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_ENTRIES = "entries"
        private const val TABLE_REMINDERS = "reminders"
    }
}

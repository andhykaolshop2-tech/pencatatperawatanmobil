package com.personalizeprolabs.odolog.data

import com.personalizeprolabs.odolog.core.Backup
import com.personalizeprolabs.odolog.core.BackupData
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.Reminder
import com.personalizeprolabs.odolog.core.ReminderLogic
import com.personalizeprolabs.odolog.core.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How many rows an import actually added. */
data class ImportCounts(val entries: Int, val reminders: Int)

/**
 * Single source of truth for the log book. The whole data set is small (a few thousand
 * rows after many years), so it is simply kept in memory and re-read after every change.
 * The UI observes [entries] and [reminders].
 */
class Repository(private val database: AppDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val entriesState = MutableStateFlow<List<LogEntry>>(emptyList())
    private val remindersState = MutableStateFlow<List<Reminder>>(emptyList())
    private val loadedState = MutableStateFlow(false)

    /** Newest first. */
    val entries: StateFlow<List<LogEntry>> get() = entriesState

    /** Most urgent first. */
    val reminders: StateFlow<List<Reminder>> get() = remindersState

    /** False until the database has been read for the first time. */
    val loaded: StateFlow<Boolean> get() = loadedState

    init {
        scope.launch {
            synchronized(lock) { reload() }
            loadedState.value = true
        }
    }

    private fun reload() {
        entriesState.value = database.loadEntries().sortedWith(Stats.NEWEST_FIRST)
        remindersState.value = database.loadReminders().sortedWith(ReminderLogic.BY_DUE_DATE)
    }

    /** Runs a database change and refreshes the in-memory lists, one change at a time. */
    private fun <T> change(block: () -> T): T = synchronized(lock) {
        val result = block()
        reload()
        result
    }

    suspend fun saveEntry(entry: LogEntry): Long = withContext(Dispatchers.IO) {
        change { database.saveEntry(entry) }
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        change { database.deleteEntry(id) }
    }

    suspend fun saveReminder(reminder: Reminder): Long = withContext(Dispatchers.IO) {
        change { database.saveReminder(reminder) }
    }

    suspend fun deleteReminder(id: Long) = withContext(Dispatchers.IO) {
        change { database.deleteReminder(id) }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        change { database.deleteAll() }
    }

    /**
     * Stores imported data. With [replace] everything that was there before is removed;
     * otherwise only rows that are not in the log book yet are added.
     */
    suspend fun importData(data: BackupData, replace: Boolean): ImportCounts = withContext(Dispatchers.IO) {
        change {
            val newEntries = if (replace) data.entries else Backup.newEntries(database.loadEntries(), data.entries)
            val newReminders = if (replace) data.reminders else Backup.newReminders(database.loadReminders(), data.reminders)
            database.insertAll(
                newEntries.map { it.copy(id = 0) },
                newReminders.map { it.copy(id = 0, notifiedStage = 0) },
                deleteExisting = replace,
            )
            ImportCounts(newEntries.size, newReminders.size)
        }
    }

    // Blocking variants for the reminder check, which runs outside the UI on its own thread.

    fun loadRemindersBlocking(): List<Reminder> = synchronized(lock) { database.loadReminders() }

    fun setReminderStageBlocking(id: Long, stage: Int) {
        change { database.setReminderStage(id, stage) }
    }
}

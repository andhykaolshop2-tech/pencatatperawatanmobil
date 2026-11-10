package com.personalizeprolabs.odolog.core

import java.time.LocalDate

enum class ReminderStatus { LATER, UPCOMING, DUE_TODAY, OVERDUE }

/** Date arithmetic for reminders. All dates are epoch days. */
object ReminderLogic {
    const val STAGE_NONE = 0
    const val STAGE_UPCOMING = 1
    const val STAGE_DUE = 2

    /** Positive = days left, 0 = due today, negative = days overdue. */
    fun daysUntilDue(reminder: Reminder, todayEpochDay: Long): Long = reminder.dueEpochDay - todayEpochDay

    fun status(reminder: Reminder, todayEpochDay: Long): ReminderStatus {
        val days = daysUntilDue(reminder, todayEpochDay)
        return when {
            days < 0 -> ReminderStatus.OVERDUE
            days == 0L -> ReminderStatus.DUE_TODAY
            days <= reminder.notifyDaysBefore -> ReminderStatus.UPCOMING
            else -> ReminderStatus.LATER
        }
    }

    /**
     * The notification stage a reminder should have reached by today. A notification is
     * shown whenever this is higher than [Reminder.notifiedStage]; so every reminder
     * notifies at most twice: once when it comes up and once when it is due.
     */
    fun stageFor(reminder: Reminder, todayEpochDay: Long): Int = when (status(reminder, todayEpochDay)) {
        ReminderStatus.LATER -> STAGE_NONE
        ReminderStatus.UPCOMING -> STAGE_UPCOMING
        ReminderStatus.DUE_TODAY, ReminderStatus.OVERDUE -> STAGE_DUE
    }

    /**
     * Due date of the next round after the reminder was marked as done today, or null
     * when it does not repeat. Done on time (or early): the interval is counted from the
     * old due date. Done late: it is counted from today.
     */
    fun nextDueEpochDay(reminder: Reminder, todayEpochDay: Long): Long? {
        if (reminder.repeatMonths <= 0) return null
        val base = if (todayEpochDay <= reminder.dueEpochDay) reminder.dueEpochDay else todayEpochDay
        return LocalDate.ofEpochDay(base).plusMonths(reminder.repeatMonths.toLong()).toEpochDay()
    }

    /** Most urgent first. */
    val BY_DUE_DATE: Comparator<Reminder> = Comparator { a, b ->
        var c = a.dueEpochDay.compareTo(b.dueEpochDay)
        if (c == 0) c = a.id.compareTo(b.id)
        c
    }
}

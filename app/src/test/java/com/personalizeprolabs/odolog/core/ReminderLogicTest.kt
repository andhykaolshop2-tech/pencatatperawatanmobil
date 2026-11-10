package com.personalizeprolabs.odolog.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderLogicTest {
    private fun day(year: Int, month: Int, dayOfMonth: Int): Long = LocalDate.of(year, month, dayOfMonth).toEpochDay()

    private val due = day(2026, 12, 1)
    private val reminder = Reminder(title = "Service", dueEpochDay = due, repeatMonths = 12, notifyDaysBefore = 14)

    @Test
    fun statusAndStage() {
        assertEquals(ReminderStatus.LATER, ReminderLogic.status(reminder, due - 15))
        assertEquals(ReminderLogic.STAGE_NONE, ReminderLogic.stageFor(reminder, due - 15))

        assertEquals(ReminderStatus.UPCOMING, ReminderLogic.status(reminder, due - 14))
        assertEquals(ReminderStatus.UPCOMING, ReminderLogic.status(reminder, due - 1))
        assertEquals(ReminderLogic.STAGE_UPCOMING, ReminderLogic.stageFor(reminder, due - 1))

        assertEquals(ReminderStatus.DUE_TODAY, ReminderLogic.status(reminder, due))
        assertEquals(ReminderLogic.STAGE_DUE, ReminderLogic.stageFor(reminder, due))

        assertEquals(ReminderStatus.OVERDUE, ReminderLogic.status(reminder, due + 1))
        assertEquals(ReminderLogic.STAGE_DUE, ReminderLogic.stageFor(reminder, due + 30))
    }

    @Test
    fun onlyOnTheDay() {
        val sameDayOnly = reminder.copy(notifyDaysBefore = 0)
        assertEquals(ReminderStatus.LATER, ReminderLogic.status(sameDayOnly, due - 1))
        assertEquals(ReminderStatus.DUE_TODAY, ReminderLogic.status(sameDayOnly, due))
    }

    @Test
    fun daysUntilDue() {
        assertEquals(10L, ReminderLogic.daysUntilDue(reminder, due - 10))
        assertEquals(0L, ReminderLogic.daysUntilDue(reminder, due))
        assertEquals(-3L, ReminderLogic.daysUntilDue(reminder, due + 3))
    }

    @Test
    fun nextDueDate() {
        // done early or on the day: counted from the old due date
        assertEquals(day(2027, 12, 1), ReminderLogic.nextDueEpochDay(reminder, due - 20))
        assertEquals(day(2027, 12, 1), ReminderLogic.nextDueEpochDay(reminder, due))
        // done late: counted from today
        assertEquals(day(2028, 2, 15), ReminderLogic.nextDueEpochDay(reminder, day(2027, 2, 15)))
        // end of month is handled by the calendar
        val endOfMonth = reminder.copy(dueEpochDay = day(2026, 8, 31), repeatMonths = 6)
        assertEquals(day(2027, 2, 28), ReminderLogic.nextDueEpochDay(endOfMonth, day(2026, 8, 1)))
        // no repeat
        assertNull(ReminderLogic.nextDueEpochDay(reminder.copy(repeatMonths = 0), due))
    }

    @Test
    fun sortedByDueDate() {
        val a = reminder.copy(id = 1, dueEpochDay = due + 5)
        val b = reminder.copy(id = 2, dueEpochDay = due)
        val c = reminder.copy(id = 3, dueEpochDay = due)
        assertEquals(listOf(2L, 3L, 1L), listOf(a, b, c).sortedWith(ReminderLogic.BY_DUE_DATE).map { it.id })
    }
}

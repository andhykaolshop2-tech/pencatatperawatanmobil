package com.personalizeprolabs.odolog.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules the daily reminder check.
 *
 * It deliberately uses an INEXACT alarm (setAndAllowWhileIdle): reminders are about days,
 * not minutes, and inexact alarms need no special permission (no SCHEDULE_EXACT_ALARM /
 * USE_EXACT_ALARM). Android may deliver the alarm some time after 09:00.
 *
 * Alarms are lost when the phone restarts or the app is updated, so the check is
 * scheduled again from OdologApp.onCreate and from ReminderReceiver (boot / update).
 */
object ReminderScheduler {
    const val ACTION_CHECK = "com.personalizeprolabs.odolog.action.CHECK_REMINDERS"
    private const val REQUEST_CODE = 100
    private const val CHECK_HOUR = 9

    fun scheduleNextCheck(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_CHECK)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextCheckMillis(), pendingIntent)
        } catch (e: SecurityException) {
            // Not expected for inexact alarms. Without the alarm, reminders are still
            // visible (and marked as due) inside the app.
        }
    }

    /** The next 09:00 local time, as epoch milliseconds. */
    private fun nextCheckMillis(): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(CHECK_HOUR, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

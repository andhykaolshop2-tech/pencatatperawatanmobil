package com.personalizeprolabs.odolog.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.personalizeprolabs.odolog.MainActivity
import com.personalizeprolabs.odolog.OdologApp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.Reminder
import com.personalizeprolabs.odolog.core.ReminderLogic
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shows the notifications for reminders that are coming up or due. */
object ReminderNotifier {
    private const val CHANNEL_ID = "reminders"
    private const val BRAND_COLOR = 0xFF0B5F6A.toInt()

    // All reminder notifications form one group with a summary of our own. Without it Android
    // bundles them itself, and a tap on that bundle opens the app without saying why.
    private const val GROUP_KEY = "com.personalizeprolabs.odolog.REMINDERS"
    private const val SUMMARY_ID = Int.MAX_VALUE

    /** Safe to call on every app start; it also refreshes the channel name after a language change. */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.description = context.getString(R.string.notification_channel_description)
        manager.createNotificationChannel(channel)
    }

    /**
     * Blocking; call from a background thread. Every reminder notifies at most twice:
     * when it comes up (the chosen number of days before) and when it is due.
     */
    fun checkAndNotify(context: Context) {
        val app = context.applicationContext as? OdologApp ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Notifications switched off (or not allowed yet): keep the stages untouched, so
        // that the reminder is shown as soon as the user allows notifications.
        if (!manager.areNotificationsEnabled()) return

        val today = LocalDate.now().toEpochDay()
        var posted = false
        for (reminder in app.repository.loadRemindersBlocking()) {
            val stage = ReminderLogic.stageFor(reminder, today)
            if (stage <= reminder.notifiedStage) continue
            manager.notify(notificationId(reminder.id), buildNotification(context, reminder, today))
            app.repository.setReminderStageBlocking(reminder.id, stage)
            posted = true
        }
        if (posted) manager.notify(SUMMARY_ID, buildSummary(context))
    }

    /** Removes the notification of a reminder that was changed, completed or deleted. */
    fun cancel(context: Context, reminderId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val id = notificationId(reminderId)
        manager.cancel(id)
        // The summary goes when no reminder notification is left.
        val othersLeft = try {
            manager.activeNotifications.any { it.id != id && it.id != SUMMARY_ID && it.notification.group == GROUP_KEY }
        } catch (e: Exception) {
            true
        }
        if (!othersLeft) manager.cancel(SUMMARY_ID)
    }

    private fun buildNotification(context: Context, reminder: Reminder, today: Long): Notification {
        val days = ReminderLogic.daysUntilDue(reminder, today)
        val dueDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(LocalDate.ofEpochDay(reminder.dueEpochDay))
        val text = when {
            days > 0 -> context.getString(
                R.string.notification_text_upcoming,
                dueDate,
                context.resources.getQuantityString(R.plurals.in_days, days.toInt(), days.toInt()),
            )
            days == 0L -> context.getString(R.string.notification_text_today)
            else -> context.getString(R.string.notification_text_overdue, dueDate)
        }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(reminder.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setContentIntent(openRemindersIntent(context, notificationId(reminder.id)))
            .setAutoCancel(true)
            .build()
    }

    /** What Android shows for the collapsed group; it is hidden while there is only one reminder. */
    private fun buildSummary(context: Context): Notification =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(context.getString(R.string.notification_channel_name))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setContentIntent(openRemindersIntent(context, SUMMARY_ID))
            .setAutoCancel(true)
            .build()

    /** Opens the app on the reminders tab. */
    private fun openRemindersIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(reminderId: Long): Int = (reminderId % Int.MAX_VALUE).toInt()
}

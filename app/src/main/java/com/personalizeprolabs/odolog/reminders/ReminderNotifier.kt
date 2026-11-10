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
        for (reminder in app.repository.loadRemindersBlocking()) {
            val stage = ReminderLogic.stageFor(reminder, today)
            if (stage <= reminder.notifiedStage) continue
            manager.notify(notificationId(reminder), buildNotification(context, reminder, today))
            app.repository.setReminderStageBlocking(reminder.id, stage)
        }
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
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(reminder),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(reminder.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun notificationId(reminder: Reminder): Int = (reminder.id % Int.MAX_VALUE).toInt()
}

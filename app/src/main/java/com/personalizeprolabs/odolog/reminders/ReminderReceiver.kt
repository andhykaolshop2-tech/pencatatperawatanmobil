package com.personalizeprolabs.odolog.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the daily alarm, and the "phone started" / "app updated" broadcasts.
 * In every case it does the same thing: check the reminders, then schedule the next check.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            try {
                ReminderNotifier.checkAndNotify(appContext)
                ReminderScheduler.scheduleNextCheck(appContext)
            } catch (e: Exception) {
                // A failed check must never crash the app. The next check tries again.
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

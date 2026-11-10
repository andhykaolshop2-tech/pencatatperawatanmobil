package com.personalizeprolabs.odolog

import android.app.Application
import com.personalizeprolabs.odolog.data.AppDatabase
import com.personalizeprolabs.odolog.data.Repository
import com.personalizeprolabs.odolog.data.SettingsStore
import com.personalizeprolabs.odolog.reminders.ReminderNotifier
import com.personalizeprolabs.odolog.reminders.ReminderScheduler

/**
 * Application object: owns the two long-lived objects of the app (settings and the log
 * book). There is no dependency-injection library; screens get them through
 * `context.applicationContext as OdologApp`.
 */
class OdologApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = Repository(AppDatabase(this))
        ReminderNotifier.createChannel(this)
        ReminderScheduler.scheduleNextCheck(this)
    }
}

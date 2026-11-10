package com.personalizeprolabs.odolog

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.personalizeprolabs.odolog.ui.OdologRoot
import com.personalizeprolabs.odolog.ui.OdologTheme

/** The only activity. All screens are Jetpack Compose functions in the `ui` package. */
class MainActivity : ComponentActivity() {

    /** Refreshed in onResume, because the user can change it in the system settings. */
    private var notificationsEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A tap on a reminder notification opens the reminders tab (only on a fresh start).
        val openReminders = savedInstanceState == null &&
            intent?.getBooleanExtra(EXTRA_OPEN_REMINDERS, false) == true
        setContent {
            OdologTheme {
                OdologRoot(startOnReminders = openReminders, notificationsEnabled = notificationsEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(NotificationManager::class.java)
        notificationsEnabled = manager?.areNotificationsEnabled() ?: true
    }

    companion object {
        const val EXTRA_OPEN_REMINDERS = "com.personalizeprolabs.odolog.extra.OPEN_REMINDERS"
    }
}

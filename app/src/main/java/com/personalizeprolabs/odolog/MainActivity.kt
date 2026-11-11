package com.personalizeprolabs.odolog

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.personalizeprolabs.odolog.ui.OdologRoot
import com.personalizeprolabs.odolog.ui.OdologTheme

/** The only activity. All screens are Jetpack Compose functions in the `ui` package. */
class MainActivity : ComponentActivity() {

    /** Refreshed in onResume, because the user can change it in the system settings. */
    private var notificationsEnabled by mutableStateOf(true)

    /** Goes up by one every time a reminder notification was tapped. */
    private var openRemindersRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not after a rotation or a restore: there the same intent would be handled twice.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            OdologTheme {
                OdologRoot(
                    openRemindersRequest = openRemindersRequest,
                    notificationsEnabled = notificationsEnabled,
                )
            }
        }
    }

    /** The app was already open when a reminder notification was tapped (launchMode singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_REMINDERS, false)) {
            intent.removeExtra(EXTRA_OPEN_REMINDERS)
            openRemindersRequest++
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

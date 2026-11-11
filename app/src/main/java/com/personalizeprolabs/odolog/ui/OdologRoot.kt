package com.personalizeprolabs.odolog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.personalizeprolabs.odolog.OdologApp
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.Fmt

// Navigation is a single saved string: the app is one level deep (the main screen with
// its tabs, and one detail screen on top of it), so no navigation library is needed.
private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ENTRY = "entry"
private const val ROUTE_REMINDER = "reminder"

/** Root of the UI: collects the app state and decides which screen is shown. */
@Composable
fun OdologRoot(openRemindersRequest: Int, notificationsEnabled: Boolean) {
    val app = LocalContext.current.applicationContext as OdologApp
    val settings by app.settings.settings.collectAsState()
    val setupDone by app.settings.setupDone.collectAsState()
    val loaded by app.repository.loaded.collectAsState()
    val entries by app.repository.entries.collectAsState()
    val reminders by app.repository.reminders.collectAsState()

    val locale = LocalConfiguration.current.locales[0]
    val fmt = remember(settings, locale) { Fmt(locale, settings) }

    var route by rememberSaveable { mutableStateOf(ROUTE_MAIN) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    val goBack = { route = ROUTE_MAIN }

    // A tap on a reminder notification: show the reminders tab, whatever was open before.
    LaunchedEffect(openRemindersRequest) {
        if (openRemindersRequest > 0) {
            route = ROUTE_MAIN
            tab = TAB_REMINDERS
        }
    }

    CompositionLocalProvider(LocalFmt provides fmt) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val parts = route.split("/")
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                !setupDone -> SetupScreen(
                    settings = settings,
                    onChange = { app.settings.update(it) },
                    onDone = {
                        app.settings.update(settings)
                        app.settings.markSetupDone()
                    },
                )

                parts[0] == ROUTE_SETTINGS -> {
                    BackHandler(onBack = goBack)
                    SettingsScreen(settings = settings, entries = entries, reminders = reminders, onBack = goBack)
                }

                parts[0] == ROUTE_ENTRY -> {
                    BackHandler(onBack = goBack)
                    EntryEditScreen(
                        type = EntryType.fromCode(parts.getOrNull(1)) ?: EntryType.FUEL,
                        entryId = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
                        entries = entries,
                        onDone = goBack,
                    )
                }

                parts[0] == ROUTE_REMINDER -> {
                    BackHandler(onBack = goBack)
                    ReminderEditScreen(
                        reminderId = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                        reminders = reminders,
                        onDone = goBack,
                    )
                }

                else -> {
                    BackHandler(enabled = tab != TAB_HOME) { tab = TAB_HOME }
                    MainScreen(
                        tab = tab,
                        onTabChange = { tab = it },
                        settings = settings,
                        entries = entries,
                        reminders = reminders,
                        notificationsEnabled = notificationsEnabled,
                        onAddEntry = { type -> route = ROUTE_ENTRY + "/" + type.code + "/0" },
                        onEditEntry = { entry -> route = ROUTE_ENTRY + "/" + entry.type.code + "/" + entry.id },
                        onAddReminder = { route = ROUTE_REMINDER + "/0" },
                        onEditReminder = { reminder -> route = ROUTE_REMINDER + "/" + reminder.id },
                        onOpenSettings = { route = ROUTE_SETTINGS },
                    )
                }
            }
        }
    }
}

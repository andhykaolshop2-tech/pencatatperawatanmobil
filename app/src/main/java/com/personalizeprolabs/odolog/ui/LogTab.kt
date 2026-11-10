@file:OptIn(ExperimentalMaterial3Api::class)

package com.personalizeprolabs.odolog.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.personalizeprolabs.odolog.R
import com.personalizeprolabs.odolog.core.EntryType
import com.personalizeprolabs.odolog.core.LogEntry
import com.personalizeprolabs.odolog.core.Stats

private const val FILTER_ALL = "all"

/** The complete log book, newest first, with a filter per kind of entry. */
@Composable
fun LogTab(entries: List<LogEntry>, onEditEntry: (LogEntry) -> Unit, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    val typesPresent = remember(entries) { EntryType.values().filter { type -> entries.any { it.type == type } } }
    // A filter for a kind of entry that no longer exists falls back to "all".
    val activeFilter = if (typesPresent.any { it.code == filter }) filter else FILTER_ALL
    val shown = remember(entries, activeFilter) {
        if (activeFilter == FILTER_ALL) entries else entries.filter { it.type.code == activeFilter }
    }
    val fuelIntervals = remember(entries) {
        Stats.economyIntervals(entries, EntryType.FUEL).associateBy { it.endEntryId }
    }
    val chargeIntervals = remember(entries) {
        Stats.economyIntervals(entries, EntryType.CHARGE).associateBy { it.endEntryId }
    }

    Column(modifier = modifier) {
        if (typesPresent.size > 1) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = activeFilter == FILTER_ALL,
                    onClick = { filter = FILTER_ALL },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                for (type in typesPresent) {
                    FilterChip(
                        selected = activeFilter == type.code,
                        onClick = { filter = type.code },
                        label = { Text(stringResource(entryTypeLabelRes(type))) },
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.log_empty_title),
                text = stringResource(R.string.log_empty_text),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(shown, key = { it.id }) { entry ->
                    val interval = if (entry.type == EntryType.CHARGE) chargeIntervals[entry.id] else fuelIntervals[entry.id]
                    EntryRow(entry = entry, economy = interval, onClick = { onEditEntry(entry) })
                    HorizontalDivider()
                }
            }
        }
    }
}

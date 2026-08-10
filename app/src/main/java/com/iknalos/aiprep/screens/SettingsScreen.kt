package com.iknalos.aiprep.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.DailySyncWorker
import com.iknalos.aiprep.ui.ColSpacer
import com.iknalos.aiprep.ui.Cyan
import com.iknalos.aiprep.ui.Good
import com.iknalos.aiprep.ui.OutlineButton
import com.iknalos.aiprep.ui.Panel
import com.iknalos.aiprep.ui.PrimaryButton
import com.iknalos.aiprep.ui.RowSpacer
import com.iknalos.aiprep.ui.SectionTitle
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var autoUpdate by remember { mutableStateOf(vm.settings.autoUpdate) }
    val canInstall = vm.updater.canInstall()
    val silentSupported = vm.updater.silentInstallSupported()
    val lastSync = vm.settings.lastSyncMillis

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Updates", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "New questions arrive on their own and need no install. The app itself " +
                        "updates in the background around 4am.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // The one thing that genuinely needs the user, so it goes first and loudly.
        if (!canInstall) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag("action needed", Warn)
                    }
                    ColSpacer(8)
                    Text(
                        "Allow this app to install updates",
                        style = MaterialTheme.typography.titleMedium
                    )
                    ColSpacer(6)
                    Text(
                        "Android requires one manual approval before any app can install " +
                            "updates, including apps from the Play Store. Grant it once and " +
                            "every future update installs silently with no further prompts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ColSpacer(12)
                    PrimaryButton(
                        "Open Android settings",
                        { vm.requestInstallPermission() },
                        Modifier.fillMaxWidth()
                    )
                    ColSpacer(6)
                    Text(
                        "Turn on \"Allow from this source\", then come back here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic updates", style = MaterialTheme.typography.titleMedium)
                        ColSpacer(3)
                        Text(
                            "Daily at about 4am, on Wi-Fi, when the battery is not low.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RowSpacer(10)
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = {
                            autoUpdate = it
                            vm.setAutoUpdate(it)
                        }
                    )
                }
                ColSpacer(12)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (silentSupported) {
                        Tag("silent install ready", Good)
                    } else if (!canInstall) {
                        Tag("waiting on permission", Warn)
                    } else {
                        Tag("will ask once per update", Warn)
                    }
                }
                if (!silentSupported && canInstall) {
                    ColSpacer(8)
                    Text(
                        "Fully silent updates need Android 12 or newer. On this device Android " +
                            "will show a short confirmation for each update.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("CHECK NOW")
                ColSpacer(8)
                Text(
                    if (vm.syncMessage.isBlank()) "Fetches news, new questions, and any new app version."
                    else vm.syncMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
                ColSpacer(12)
                Row(Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        if (vm.syncing) "Checking..." else "Check for updates",
                        { vm.syncNow() },
                        Modifier.weight(1f),
                        enabled = !vm.syncing
                    )
                }
                val pending = vm.pendingUpdate
                if (pending != null && !canInstall) {
                    ColSpacer(8)
                    OutlineButton(
                        "Grant permission to install ${pending.versionName}",
                        { vm.requestInstallPermission() },
                        Modifier.fillMaxWidth(),
                        Warn
                    )
                }
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("STATUS")
                ColSpacer(10)
                InfoRow("App version", vm.updater.currentVersionName)
                InfoRow(
                    "Question bank",
                    "v${vm.contentVersion} · ${vm.allCards.size} cards · " +
                        "${vm.allFlashCards.size} flashcards"
                )
                InfoRow(
                    "Content source",
                    if (vm.usingRemoteContent) "downloaded (over the air)" else "bundled in app"
                )
                InfoRow(
                    "Last check",
                    if (lastSync == 0L) "not yet"
                    else SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(lastSync))
                )
                if (vm.settings.lastSyncNote.isNotBlank()) {
                    InfoRow("Last result", vm.settings.lastSyncNote)
                }
                if (vm.settings.lastInstallStatus.isNotBlank()) {
                    InfoRow("Last install", vm.settings.lastInstallStatus)
                }
                InfoRow("News items", "${vm.news.feed.items.size}")
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("HOW THIS WORKS")
                ColSpacer(8)
                Text(
                    "Questions and lessons are published to the project's GitHub Pages site and " +
                        "fetched by the app, so adding new material never requires an install. " +
                        "The copy inside the app is only an offline fallback.\n\n" +
                        "App updates are downloaded from GitHub Releases, checked against a " +
                        "SHA-256 published alongside them, and installed with the same signing " +
                        "key, which is what lets Android skip the confirmation dialog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColSpacer(12)
                OutlineButton(
                    "Run the daily job now",
                    { DailySyncWorker.runNow(vm.getApplication()) },
                    Modifier.fillMaxWidth(),
                    Cyan
                )
                ColSpacer(6)
                Text(
                    "Queues the exact background job that runs at 4am, so you can confirm it works.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { ColSpacer(24) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        RowSpacer(10)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.3f)
        )
    }
    ColSpacer(7)
}

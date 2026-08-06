package com.iknalos.aiprep.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.ui.ColSpacer
import com.iknalos.aiprep.ui.Cyan
import com.iknalos.aiprep.ui.OutlineButton
import com.iknalos.aiprep.ui.Panel
import com.iknalos.aiprep.ui.RowSpacer
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.Warn

@Composable
fun NewsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val state = vm.news

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "What's new in AI",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlineButton(
                        if (state.loading) "..." else "Refresh",
                        { vm.refreshNews() },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                ColSpacer(4)
                Text(
                    "Refreshed daily from research and lab announcements. Being able to talk about " +
                        "something from the last month is worth a surprising amount in interviews.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.feed.generated.isNotBlank() || state.offline) {
                    ColSpacer(10)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.feed.generated.isNotBlank()) {
                            Tag("updated ${state.feed.generated}", Cyan)
                            RowSpacer(6)
                        }
                        if (state.offline) Tag("showing cached", Warn)
                    }
                }
            }
        }

        if (state.feed.items.isEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Text("No updates loaded yet", style = MaterialTheme.typography.titleMedium)
                    ColSpacer(6)
                    Text(
                        "Tap refresh once you have a connection. The feed is a static file " +
                            "published by this project, so there is nothing to sign up for.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(state.feed.items, key = { it.id }) { item ->
            Panel(
                Modifier.fillMaxWidth(),
                onClick = if (item.url.isBlank()) null else {
                    {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                            )
                        } catch (e: Exception) {
                            // No browser available; nothing useful to do.
                        }
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.source.isNotBlank()) {
                        Tag(item.source, Cyan)
                        RowSpacer(6)
                    }
                    if (item.published.isNotBlank()) {
                        Text(
                            item.published,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ColSpacer(8)
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.summary.isNotBlank()) {
                    ColSpacer(6)
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.tags.isNotEmpty()) {
                    ColSpacer(10)
                    Row {
                        item.tags.take(3).forEach { t ->
                            Tag(t, MaterialTheme.colorScheme.primary)
                            RowSpacer(6)
                        }
                    }
                }
                if (item.url.isNotBlank()) {
                    ColSpacer(10)
                    Text(
                        "Open source",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item { ColSpacer(24) }
    }
}

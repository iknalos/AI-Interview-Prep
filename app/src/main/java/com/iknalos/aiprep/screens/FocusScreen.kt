package com.iknalos.aiprep.screens

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.Difficulty
import com.iknalos.aiprep.ui.Chip
import com.iknalos.aiprep.ui.ColSpacer
import com.iknalos.aiprep.ui.Cyan
import com.iknalos.aiprep.ui.OutlineButton
import com.iknalos.aiprep.ui.Panel
import com.iknalos.aiprep.ui.PrimaryButton
import com.iknalos.aiprep.ui.RowSpacer
import com.iknalos.aiprep.ui.SectionTitle
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.difficultyColor

/**
 * Topic and difficulty selection. Everything else in the app draws from this,
 * so it doubles as the "what am I working on" screen.
 */
@Composable
fun FocusScreen(vm: AppViewModel, onDone: () -> Unit) {
    val selectedTopics = vm.progress.selectedTopics
    val selectedDiffs = vm.progress.selectedDifficulties
    val pool = vm.filteredCards()
    val stats = vm.topicStats().associateBy { it.topic.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Focus", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Pick what you want to get expert at. Study, quiz and mock interview all " +
                        "draw from this selection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("DIFFICULTY")
                ColSpacer(10)
                Row(Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEach { d ->
                        Chip(
                            label = d.label,
                            selected = d.key in selectedDiffs,
                            onClick = { vm.toggleDifficulty(d.key) },
                            accent = difficultyColor(d)
                        )
                        RowSpacer(8)
                    }
                }
                ColSpacer(10)
                Text(
                    if (selectedDiffs.isEmpty()) "All levels included"
                    else "Only: " + selectedDiffs.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("TOPICS", Modifier.weight(1f))
                OutlineButton(
                    "All",
                    { vm.selectAllTopics() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RowSpacer(8)
                OutlineButton(
                    "Clear",
                    { vm.clearFilters() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(vm.topics, key = { it.id }) { topic ->
            val on = topic.id in selectedTopics
            val s = stats[topic.id]
            Panel(Modifier.fillMaxWidth(), onClick = { vm.toggleTopic(topic.id) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            topic.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ColSpacer(3)
                        Text(
                            buildString {
                                append("${topic.cardCount} cards")
                                if (s != null && s.seen > 0) {
                                    append(" · ${s.mastered} mastered")
                                    append(" · ${s.accuracy}% accuracy")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RowSpacer(10)
                    Tag(
                        if (on) "included" else "off",
                        if (on) Cyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Column {
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "${pool.size} cards match your focus",
                        style = MaterialTheme.typography.titleMedium
                    )
                    ColSpacer(4)
                    Text(
                        if (selectedTopics.isEmpty() && selectedDiffs.isEmpty())
                            "No filters set, so everything is included."
                        else "Selecting nothing at all is the same as selecting everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ColSpacer(12)
                PrimaryButton("Done", onDone, Modifier.fillMaxWidth(), enabled = pool.isNotEmpty())
                ColSpacer(24)
            }
        }
    }
}

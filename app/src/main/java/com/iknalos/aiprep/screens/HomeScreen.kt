package com.iknalos.aiprep.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.ui.Bar
import com.iknalos.aiprep.ui.ColSpacer
import com.iknalos.aiprep.ui.Cyan
import com.iknalos.aiprep.ui.Good
import com.iknalos.aiprep.ui.OutlineButton
import com.iknalos.aiprep.ui.Panel
import com.iknalos.aiprep.ui.PrimaryButton
import com.iknalos.aiprep.ui.RowSpacer
import com.iknalos.aiprep.ui.SectionTitle
import com.iknalos.aiprep.ui.StatTile
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.Warn

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onStudy: () -> Unit,
    onQuiz: () -> Unit,
    onMock: () -> Unit,
    onLearn: () -> Unit,
    onNews: () -> Unit,
    onStats: () -> Unit,
    onFilters: () -> Unit
) {
    val pool = vm.filteredCards()
    val due = vm.dueCards().size
    val fresh = vm.newCards().size
    val goal = vm.progress.dailyGoal
    val doneToday = vm.reviewsToday()
    val streak = vm.streak()
    val filtersActive =
        vm.progress.selectedTopics.isNotEmpty() || vm.progress.selectedDifficulties.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("AI Interview Prep", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${vm.allCards.size} questions across ${vm.topics.size} topics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Today's goal
        item {
            Panel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("TODAY")
                        Text(
                            "$doneToday of $goal reviews",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    if (streak > 0) {
                        Tag("$streak day streak", Warn)
                    }
                }
                ColSpacer(10)
                Bar(
                    fraction = if (goal == 0) 0f else doneToday.toFloat() / goal,
                    height = 10,
                    color = if (doneToday >= goal) Good else MaterialTheme.colorScheme.primary
                )
                ColSpacer(12)
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatTile("$due", "Due now", Modifier.weight(1f), Warn)
                    RowSpacer(10)
                    StatTile("$fresh", "Unseen", Modifier.weight(1f), Cyan)
                    RowSpacer(10)
                    StatTile("${vm.masteredCount()}", "Mastered", Modifier.weight(1f), Good)
                }
            }
        }

        // Primary actions
        item {
            Panel {
                SectionTitle("PRACTICE")
                ColSpacer(10)
                PrimaryButton(
                    text = if (due > 0) "Review $due due cards" else "Study ${minOf(fresh, goal)} new cards",
                    onClick = onStudy,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pool.isNotEmpty()
                )
                ColSpacer(8)
                Row(Modifier.fillMaxWidth()) {
                    OutlineButton("Scored quiz", onQuiz, Modifier.weight(1f))
                    RowSpacer(8)
                    OutlineButton("Mock interview", onMock, Modifier.weight(1f), Cyan)
                }
                ColSpacer(8)
                Row(Modifier.fillMaxWidth()) {
                    OutlineButton(
                        "Lessons",
                        onLearn,
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RowSpacer(8)
                    OutlineButton(
                        "Progress",
                        onStats,
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Filters
        item {
            Panel(onClick = onFilters) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("FOCUS")
                        Text(
                            if (filtersActive) filterSummary(vm) else "All topics, all difficulties",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${pool.size} cards in your current selection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RowSpacer(8)
                    Tag("Change", MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Latest AI update
        val latest = vm.news.feed.items.firstOrNull()
        if (latest != null) {
            item {
                Panel(onClick = onNews) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle("LATEST IN AI")
                        RowSpacer(8)
                        if (vm.news.offline) Tag("offline", Warn)
                    }
                    ColSpacer(6)
                    Text(
                        latest.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (latest.summary.isNotBlank()) {
                        ColSpacer(4)
                        Text(
                            latest.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ColSpacer(8)
                    Text(
                        "${vm.news.feed.items.size} updates · tap to read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Weakest topics, so the next session has an obvious target
        val weak = vm.topicStats()
            .filter { it.seen > 0 }
            .sortedBy { it.accuracy }
            .take(3)
        if (weak.isNotEmpty()) {
            item {
                Panel {
                    SectionTitle("WEAKEST TOPICS")
                    ColSpacer(10)
                    weak.forEachIndexed { i, s ->
                        if (i > 0) ColSpacer(12)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                s.topic.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${s.accuracy}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = accuracyColor(s.accuracy)
                            )
                        }
                        ColSpacer(4)
                        Bar(
                            fraction = s.accuracy / 100f,
                            height = 6,
                            color = accuracyColor(s.accuracy)
                        )
                    }
                }
            }
        }

        item { ColSpacer(24) }
    }
}

private fun filterSummary(vm: AppViewModel): String {
    val t = vm.progress.selectedTopics
    val d = vm.progress.selectedDifficulties
    val topicPart = when {
        t.isEmpty() -> "All topics"
        t.size == 1 -> vm.topics.firstOrNull { it.id in t }?.name ?: "1 topic"
        else -> "${t.size} topics"
    }
    val diffPart = if (d.isEmpty()) "all levels" else d.joinToString(", ")
    return "$topicPart · $diffPart"
}

@Composable
fun accuracyColor(pct: Int) = when {
    pct >= 80 -> Good
    pct >= 55 -> Warn
    else -> MaterialTheme.colorScheme.error
}

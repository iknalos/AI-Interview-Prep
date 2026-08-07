package com.iknalos.aiprep.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.ui.Bar
import com.iknalos.aiprep.ui.ColSpacer
import com.iknalos.aiprep.ui.Cyan
import com.iknalos.aiprep.ui.Good
import com.iknalos.aiprep.ui.OutlineButton
import com.iknalos.aiprep.ui.Panel
import com.iknalos.aiprep.ui.RowSpacer
import com.iknalos.aiprep.ui.SectionTitle
import com.iknalos.aiprep.ui.StatTile
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.Warn
import kotlin.math.roundToInt

@Composable
fun StatsScreen(vm: AppViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    val stats = vm.topicStats()
    val totalCards = vm.allCards.size
    val touched = vm.progress.cards.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Progress", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Mastered means a card has survived several correct reviews and is now " +
                        "scheduled two weeks or more out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth()) {
                StatTile("${vm.streak()}", "Day streak", Modifier.weight(1f), Warn)
                RowSpacer(8)
                StatTile("${vm.overallAccuracy()}%", "Accuracy", Modifier.weight(1f), Cyan)
                RowSpacer(8)
                StatTile("${vm.masteredCount()}", "Mastered", Modifier.weight(1f), Good)
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("COVERAGE")
                ColSpacer(8)
                Text(
                    "$touched of $totalCards cards seen",
                    style = MaterialTheme.typography.titleMedium
                )
                ColSpacer(8)
                Bar(
                    fraction = touched.toFloat() / totalCards.coerceAtLeast(1),
                    height = 10,
                    color = Cyan
                )
                ColSpacer(12)
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "Best streak ${vm.progress.bestStreak} days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${vm.dueCards().size} due now",
                        style = MaterialTheme.typography.labelSmall,
                        color = Warn
                    )
                }
            }
        }

        // Daily goal control
        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("DAILY GOAL")
                ColSpacer(8)
                Text(
                    "${vm.progress.dailyGoal} reviews per day",
                    style = MaterialTheme.typography.titleMedium
                )
                ColSpacer(10)
                Row(Modifier.fillMaxWidth()) {
                    listOf(10, 20, 30, 50).forEach { g ->
                        val selected = vm.progress.dailyGoal == g
                        Box(Modifier.weight(1f)) {
                            OutlineButton(
                                "$g",
                                { vm.setDailyGoal(g) },
                                Modifier.fillMaxWidth(),
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RowSpacer(6)
                    }
                }
            }
        }

        item { SectionTitle("BY TOPIC") }

        items(stats, key = { it.topic.id }) { s ->
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.topic.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (s.due > 0) {
                        Tag("${s.due} due", Warn)
                        RowSpacer(6)
                    }
                    Text(
                        if (s.seen == 0) "—" else "${s.accuracy}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (s.seen == 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else accuracyColor(s.accuracy)
                    )
                }
                ColSpacer(8)
                Bar(
                    fraction = s.mastered.toFloat() / s.topic.cardCount.coerceAtLeast(1),
                    height = 6,
                    color = Good
                )
                ColSpacer(6)
                Text(
                    "${s.mastered} mastered · ${s.seen} seen · ${s.topic.cardCount} total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Session history sparkline
        val history = vm.progress.quizzes.takeLast(14)
        if (history.isNotEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("LAST ${history.size} SESSIONS")
                    ColSpacer(12)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        history.forEach { r ->
                            val pct = if (r.total == 0) 0
                            else (100.0 * r.correct / r.total).roundToInt()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .height((8 + (56 * pct / 100)).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accuracyColor(pct))
                            )
                        }
                    }
                    ColSpacer(8)
                    Text(
                        "Bar height is score. Mock sessions count self-graded points.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("RESET")
                ColSpacer(8)
                if (!confirmReset) {
                    Text(
                        "Clears all review history, streaks and scores. Your topic focus and " +
                            "daily goal are kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ColSpacer(10)
                    OutlineButton(
                        "Reset progress",
                        { confirmReset = true },
                        Modifier.fillMaxWidth(),
                        MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "This cannot be undone. Reset everything?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    ColSpacer(10)
                    Row(Modifier.fillMaxWidth()) {
                        OutlineButton(
                            "Cancel",
                            { confirmReset = false },
                            Modifier.weight(1f),
                            MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RowSpacer(8)
                        OutlineButton(
                            "Yes, reset",
                            {
                                vm.resetProgress()
                                confirmReset = false
                            },
                            Modifier.weight(1f),
                            MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item { ColSpacer(24) }
    }
}

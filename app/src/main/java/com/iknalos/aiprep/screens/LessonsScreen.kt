package com.iknalos.aiprep.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.ui.Bar
import com.iknalos.aiprep.ui.ColSpacer
import com.iknalos.aiprep.ui.Cyan
import com.iknalos.aiprep.ui.EmptyState
import com.iknalos.aiprep.ui.Good
import com.iknalos.aiprep.ui.OutlineButton
import com.iknalos.aiprep.ui.Panel
import com.iknalos.aiprep.ui.PrimaryButton
import com.iknalos.aiprep.ui.RowSpacer
import com.iknalos.aiprep.ui.SectionTitle
import com.iknalos.aiprep.ui.Tag

@Composable
fun LessonsScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val stats = vm.topicStats().associateBy { it.topic.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Lessons", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Read the topic, then drill it. Each lesson is written the way you would " +
                        "explain it in an interview.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(vm.lessons, key = { it.topicId }) { lesson ->
            val s = stats[lesson.topicId]
            val read = lesson.topicId in vm.progress.lessonsRead
            Panel(Modifier.fillMaxWidth(), onClick = { onOpen(lesson.topicId) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lesson.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (read) Tag("read", Good)
                }
                ColSpacer(4)
                Text(
                    lesson.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColSpacer(10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag("${lesson.readMinutes} min read", Cyan)
                    RowSpacer(6)
                    Tag("${s?.topic?.cardCount ?: 0} cards", MaterialTheme.colorScheme.primary)
                }
                if (s != null && s.seen > 0) {
                    ColSpacer(10)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${s.mastered} of ${s.topic.cardCount} mastered",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${s.accuracy}% accuracy",
                            style = MaterialTheme.typography.labelSmall,
                            color = accuracyColor(s.accuracy)
                        )
                    }
                    ColSpacer(5)
                    Bar(
                        fraction = s.mastered.toFloat() / s.topic.cardCount.coerceAtLeast(1),
                        height = 5,
                        color = Good
                    )
                }
            }
        }

        item { ColSpacer(24) }
    }
}

@Composable
fun LessonDetailScreen(vm: AppViewModel, topicId: String, onBack: () -> Unit, onDrill: () -> Unit) {
    val lesson = vm.lessons.firstOrNull { it.topicId == topicId }

    LaunchedEffect(topicId) {
        if (lesson != null) vm.markLessonRead(topicId)
    }

    if (lesson == null) {
        EmptyState("Lesson not found", "Go back and pick another topic.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                OutlineButton(
                    "Back to lessons",
                    onBack,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColSpacer(14)
                Text(lesson.title, style = MaterialTheme.typography.headlineMedium)
                ColSpacer(4)
                Text(
                    lesson.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColSpacer(10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag("${lesson.readMinutes} min", Cyan)
                    RowSpacer(6)
                    Tag("${lesson.sections.size} sections", MaterialTheme.colorScheme.primary)
                }
            }
        }

        items(lesson.sections) { section ->
            Panel(Modifier.fillMaxWidth()) {
                Text(section.heading, style = MaterialTheme.typography.titleMedium, color = Cyan)
                ColSpacer(10)
                Text(section.body, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Column {
                SectionTitle("NOW TEST IT")
                ColSpacer(8)
                PrimaryButton(
                    "Drill ${lesson.title} questions",
                    {
                        vm.clearFilters()
                        vm.toggleTopic(topicId)
                        onDrill()
                    },
                    Modifier.fillMaxWidth()
                )
                ColSpacer(6)
                Text(
                    "This sets your focus to this topic only, then opens the quiz.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColSpacer(24)
            }
        }
    }
}

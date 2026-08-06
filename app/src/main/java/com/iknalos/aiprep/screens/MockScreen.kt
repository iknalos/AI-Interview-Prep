package com.iknalos.aiprep.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.ui.Bad
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
import com.iknalos.aiprep.ui.StatTile
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.Warn
import com.iknalos.aiprep.ui.difficultyColor
import kotlin.math.roundToInt

@Composable
fun MockScreen(vm: AppViewModel, onExit: () -> Unit) {
    val session = vm.mock

    if (session == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Mock interview", style = MaterialTheme.typography.headlineSmall)
            ColSpacer(6)
            Text(
                "Open-ended questions with no options to pick from. Say your answer out loud, " +
                    "in full, as you would to an interviewer. Then compare it against a strong " +
                    "answer and grade yourself honestly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColSpacer(16)
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("GRADE YOURSELF LIKE THIS")
                ColSpacer(8)
                Text("Missed it — you could not give a coherent answer.", style = MaterialTheme.typography.bodySmall)
                ColSpacer(4)
                Text("Partial — right idea, but you left out something an interviewer would probe.", style = MaterialTheme.typography.bodySmall)
                ColSpacer(4)
                Text("Solid — you covered the substance and could defend it.", style = MaterialTheme.typography.bodySmall)
            }
            ColSpacer(20)
            Row(Modifier.fillMaxWidth()) {
                PrimaryButton("5 questions", { vm.startMock(5) }, Modifier.weight(1f))
                RowSpacer(8)
                PrimaryButton("10 questions", { vm.startMock(10) }, Modifier.weight(1f))
            }
            ColSpacer(12)
            Text(
                "Weighted toward medium and hard questions from your current focus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val history = vm.progress.quizzes.filter { it.mode == "mock" }.takeLast(5).reversed()
            if (history.isNotEmpty()) {
                ColSpacer(24)
                SectionTitle("RECENT MOCKS")
                ColSpacer(8)
                history.forEach { r ->
                    val pct = if (r.total == 0) 0 else (100.0 * r.correct / r.total).roundToInt()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${r.correct} / ${r.total} points",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$pct%",
                            style = MaterialTheme.typography.labelLarge,
                            color = accuracyColor(pct)
                        )
                    }
                }
            }
            ColSpacer(24)
        }
        return
    }

    if (session.finished) {
        val max = session.cards.size * 2
        val pct = if (max == 0) 0 else (100.0 * session.points / max).roundToInt()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            ColSpacer(20)
            Text("Mock complete", style = MaterialTheme.typography.headlineMedium)
            ColSpacer(4)
            Text(
                mockVerdict(pct),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColSpacer(18)
            Row(Modifier.fillMaxWidth()) {
                StatTile("$pct%", "Self score", Modifier.weight(1f), accuracyColor(pct))
                RowSpacer(10)
                StatTile("${session.points}/$max", "Points", Modifier.weight(1f), Cyan)
            }

            ColSpacer(18)
            SectionTitle("QUESTION BY QUESTION")
            ColSpacer(8)
            session.cards.forEachIndexed { i, c ->
                val g = session.grades[i]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        c.question,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                    RowSpacer(8)
                    Tag(gradeLabel(g), gradeColor(g))
                }
            }

            ColSpacer(18)
            PrimaryButton("New mock", { vm.startMock(session.cards.size) }, Modifier.fillMaxWidth())
            ColSpacer(8)
            OutlineButton("Back to home", {
                vm.endMock()
                onExit()
            }, Modifier.fillMaxWidth())
            ColSpacer(24)
        }
        return
    }

    val card = session.current
    if (card == null) {
        EmptyState("No questions", "Adjust your topic focus and try again.")
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Question ${session.index + 1} of ${session.cards.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RowSpacer(10)
            Bar(
                fraction = session.index.toFloat() / session.cards.size,
                modifier = Modifier.weight(1f),
                height = 6
            )
            RowSpacer(10)
            OutlineButton("End", {
                vm.endMock()
                onExit()
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ColSpacer(14)

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(card.topicName, Cyan)
                RowSpacer(6)
                Tag(card.difficulty.label, difficultyColor(card.difficulty))
            }
            ColSpacer(12)
            Text(card.question, style = MaterialTheme.typography.titleLarge)
        }

        ColSpacer(16)

        if (!session.revealed) {
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    "Answer out loud now, in full sentences, as if the interviewer is listening. " +
                        "Aim for structure: the direct answer first, then the mechanism, then the " +
                        "tradeoff or caveat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ColSpacer(12)
            PrimaryButton("Show a strong answer", { vm.revealMock() }, Modifier.fillMaxWidth())
        } else {
            Panel(Modifier.fillMaxWidth()) {
                SectionTitle("A STRONG ANSWER")
                ColSpacer(8)
                Text(card.modelAnswer, style = MaterialTheme.typography.bodyMedium)
                ColSpacer(14)
                SectionTitle("KEY POINT")
                ColSpacer(6)
                Text(
                    card.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ColSpacer(16)
            SectionTitle("HOW DID YOURS COMPARE?")
            ColSpacer(10)
            OutlineButton("Missed it", { vm.gradeMock(0) }, Modifier.fillMaxWidth(), Bad)
            ColSpacer(6)
            OutlineButton("Partial", { vm.gradeMock(1) }, Modifier.fillMaxWidth(), Warn)
            ColSpacer(6)
            OutlineButton("Solid", { vm.gradeMock(2) }, Modifier.fillMaxWidth(), Good)
            ColSpacer(8)
            Text(
                "Be strict. Grading yourself generously here is the fastest way to be surprised " +
                    "in a real interview.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ColSpacer(24)
    }
}

@Composable
private fun gradeColor(g: Int) = when (g) {
    2 -> Good
    1 -> Warn
    0 -> Bad
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun gradeLabel(g: Int): String = when (g) {
    2 -> "Solid"
    1 -> "Partial"
    0 -> "Missed"
    else -> "—"
}

private fun mockVerdict(pct: Int): String = when {
    pct >= 90 -> "You can hold this conversation. Keep the hard topics warm."
    pct >= 70 -> "Strong. Work the partials into full answers."
    pct >= 45 -> "The knowledge is there but the delivery is not yet. Practise out loud."
    else -> "Read the lessons for these topics, then come back to the mock."
}

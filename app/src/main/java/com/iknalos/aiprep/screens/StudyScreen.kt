package com.iknalos.aiprep.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iknalos.aiprep.AppViewModel
import com.iknalos.aiprep.Grade
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
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.Warn
import com.iknalos.aiprep.ui.difficultyColor

@Composable
fun StudyScreen(vm: AppViewModel, onExit: () -> Unit) {
    val session = vm.study

    if (session == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Spaced repetition", style = MaterialTheme.typography.headlineSmall)
            ColSpacer(6)
            Text(
                "Cards you get wrong come back this session and again tomorrow. Cards you know " +
                    "stretch out to weeks, so your time goes where it is needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColSpacer(20)
            Row(Modifier.fillMaxWidth()) {
                PrimaryButton("10 cards", { vm.startStudy(10) }, Modifier.weight(1f))
                RowSpacer(8)
                PrimaryButton("20 cards", { vm.startStudy(20) }, Modifier.weight(1f))
                RowSpacer(8)
                PrimaryButton("40 cards", { vm.startStudy(40) }, Modifier.weight(1f))
            }
            ColSpacer(12)
            Text(
                "${vm.dueCards().size} due · ${vm.newCards().size} unseen · " +
                    "${vm.filteredCards().size} in current focus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (session.done) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Session complete", style = MaterialTheme.typography.headlineSmall)
            ColSpacer(6)
            Text(
                "${session.graded} reviews · ${vm.reviewsToday()} today · " +
                    "${vm.streak()} day streak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColSpacer(20)
            PrimaryButton("Study more", { vm.startStudy(20) }, Modifier.fillMaxWidth())
            ColSpacer(8)
            OutlineButton("Back to home", {
                vm.endStudy()
                onExit()
            }, Modifier.fillMaxWidth())
        }
        return
    }

    val card = session.current
    if (card == null) {
        EmptyState("Nothing to study", "Adjust your topic focus and try again.")
        return
    }
    val state = vm.stateOf(card.id)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Progress header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${session.index + 1} / ${session.queue.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RowSpacer(10)
            Bar(
                fraction = session.index.toFloat() / session.queue.size,
                modifier = Modifier.weight(1f),
                height = 6
            )
            RowSpacer(10)
            OutlineButton("End", {
                vm.endStudy()
                onExit()
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ColSpacer(14)

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(card.topicName, Cyan)
                RowSpacer(6)
                Tag(card.difficulty.label, difficultyColor(card.difficulty))
                RowSpacer(6)
                if (state.reps == 0) {
                    Tag("new", MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Tag("rep ${state.reps}", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ColSpacer(12)
            Text(card.question, style = MaterialTheme.typography.titleLarge)

            if (session.revealed) {
                ColSpacer(16)
                SectionTitle("ANSWER")
                ColSpacer(6)
                Text(
                    card.options[card.answer],
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Good
                )
                ColSpacer(14)
                SectionTitle("WHY")
                ColSpacer(6)
                Text(card.explanation, style = MaterialTheme.typography.bodyMedium)
                ColSpacer(14)
                SectionTitle("HOW TO SAY IT OUT LOUD")
                ColSpacer(6)
                Text(
                    card.modelAnswer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ColSpacer(16)

        if (!session.revealed) {
            Text(
                "Answer it in your head, out loud if you can, then reveal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ColSpacer(10)
            PrimaryButton("Reveal answer", { vm.revealStudy() }, Modifier.fillMaxWidth())
        } else {
            SectionTitle("HOW WELL DID YOU KNOW IT?")
            ColSpacer(10)
            Row(Modifier.fillMaxWidth()) {
                OutlineButton("Again", { vm.gradeStudy(Grade.AGAIN) }, Modifier.weight(1f), Bad)
                RowSpacer(6)
                OutlineButton("Hard", { vm.gradeStudy(Grade.HARD) }, Modifier.weight(1f), Warn)
            }
            ColSpacer(6)
            Row(Modifier.fillMaxWidth()) {
                OutlineButton("Good", { vm.gradeStudy(Grade.GOOD) }, Modifier.weight(1f), Good)
                RowSpacer(6)
                OutlineButton("Easy", { vm.gradeStudy(Grade.EASY) }, Modifier.weight(1f), Cyan)
            }
            ColSpacer(8)
            Text(
                nextIntervalHint(state.reps),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ColSpacer(24)
    }
}

private fun nextIntervalHint(reps: Int): String = when (reps) {
    0 -> "Again brings it back now · Good schedules it for tomorrow"
    1 -> "Again brings it back now · Good schedules it about 6 days out"
    else -> "Again brings it back now · Good pushes it further out each time"
}

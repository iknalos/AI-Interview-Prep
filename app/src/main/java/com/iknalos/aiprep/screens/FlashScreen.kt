package com.iknalos.aiprep.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.iknalos.aiprep.ui.StatTile
import com.iknalos.aiprep.ui.Tag
import com.iknalos.aiprep.ui.VisualBlock
import com.iknalos.aiprep.ui.Warn
import com.iknalos.aiprep.ui.difficultyColor
import kotlin.math.roundToInt

/**
 * Flashcards: one prompt, two options, instant feedback.
 *
 * The whole point is a short loop with no dead taps, so choosing an option is also
 * the reveal, and the only decision afterwards is whether to read why. Explanations
 * are opt-in rather than always shown, which is what keeps a run of cards quick.
 */
@Composable
fun FlashScreen(
    vm: AppViewModel,
    onLesson: (String) -> Unit,
    onExit: () -> Unit
) {
    val session = vm.flash

    if (session == null) {
        FlashIntro(vm)
        return
    }

    if (session.finished) {
        FlashResults(vm, onExit)
        return
    }

    val card = session.current
    if (card == null) {
        EmptyState("No flashcards", "Widen your topic focus and try again.")
        return
    }

    val picked = session.chosen[session.index]
    val answered = session.locked
    val gotIt = answered && picked == card.answer

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${session.index + 1} / ${session.cards.size}",
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
            if (session.run >= 2) {
                Tag("${session.run} in a row", Warn)
            } else {
                Text(
                    "${session.correctCount} right",
                    style = MaterialTheme.typography.labelLarge,
                    color = Good
                )
            }
        }

        ColSpacer(14)

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(card.topicName, Cyan)
                RowSpacer(6)
                Tag(card.difficulty.label, difficultyColor(card.difficulty))
            }
            ColSpacer(12)
            Text(card.prompt, style = MaterialTheme.typography.titleLarge)

            val visual = card.visual
            if (visual != null) {
                ColSpacer(14)
                VisualBlock(visual, Modifier.fillMaxWidth())
            }
        }

        ColSpacer(16)

        card.options.forEachIndexed { i, option ->
            FlashOption(
                text = option,
                index = i,
                selected = picked == i,
                isCorrect = i == card.answer,
                revealed = answered,
                onClick = { vm.answerFlash(i) }
            )
            ColSpacer(10)
        }

        if (answered) {
            ColSpacer(4)
            Text(
                if (gotIt) "Correct" else "Not this one",
                style = MaterialTheme.typography.titleMedium,
                color = if (gotIt) Good else MaterialTheme.colorScheme.error
            )

            if (session.explained) {
                ColSpacer(10)
                Panel(Modifier.fillMaxWidth()) {
                    SectionTitle("WHY")
                    ColSpacer(6)
                    Text(card.explanation, style = MaterialTheme.typography.bodyMedium)
                    ColSpacer(12)
                    OutlineButton(
                        "Read the ${card.topicName} lesson",
                        { onLesson(card.topicId) },
                        Modifier.fillMaxWidth(),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ColSpacer(14)
            Row(Modifier.fillMaxWidth()) {
                OutlineButton(
                    if (session.explained) "Hide explanation" else "Learn more",
                    { vm.toggleFlashExplanation() },
                    Modifier.weight(1f),
                    Cyan
                )
                RowSpacer(10)
                PrimaryButton(
                    if (session.index + 1 >= session.cards.size) "Finish" else "Next",
                    { vm.nextFlash() },
                    Modifier.weight(1f)
                )
            }
        } else {
            ColSpacer(4)
            Text(
                "Pick one. You will see the answer straight away.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ColSpacer(16)
        OutlineButton(
            "End session",
            {
                vm.endFlash()
                onExit()
            },
            Modifier.fillMaxWidth(),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ColSpacer(24)
    }
}

@Composable
private fun FlashIntro(vm: AppViewModel) {
    val pool = vm.filteredFlashCards()
    val withVisuals = pool.count { it.visual != null }
    val unseen = pool.count { vm.progress.cards[it.id] == null }
    val history = vm.progress.quizzes.filter { it.mode == "flash" }.takeLast(5).reversed()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Flashcards", style = MaterialTheme.typography.headlineSmall)
        ColSpacer(6)
        Text(
            "Two options, one right, instant feedback. Some cards are a flow, a table or a " +
                "diagram to read rather than a paragraph. Tap Learn more whenever you want the why.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ColSpacer(20)

        if (pool.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("Nothing in this focus", style = MaterialTheme.typography.titleMedium)
                ColSpacer(6)
                Text(
                    "No flashcards match your current topic and difficulty selection. Widen the " +
                        "focus on the home screen to see them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth()) {
            PrimaryButton("10 cards", { vm.startFlash(10) }, Modifier.weight(1f))
            RowSpacer(8)
            PrimaryButton("20 cards", { vm.startFlash(20) }, Modifier.weight(1f))
            RowSpacer(8)
            PrimaryButton("40 cards", { vm.startFlash(40) }, Modifier.weight(1f))
        }
        ColSpacer(12)
        Text(
            "${pool.size} in your focus · $unseen unseen · $withVisuals with a visual",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (history.isNotEmpty()) {
            ColSpacer(24)
            SectionTitle("RECENT RUNS")
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
                        "${r.correct}/${r.total}",
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
    }
}

@Composable
private fun FlashResults(vm: AppViewModel, onExit: () -> Unit) {
    val session = vm.flash ?: return
    val total = session.cards.size
    val pct = if (total == 0) 0 else (100.0 * session.correctCount / total).roundToInt()
    val missed = session.cards.indices.filter { session.chosen[it] != session.cards[it].answer }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        ColSpacer(20)
        Text("Run complete", style = MaterialTheme.typography.headlineMedium)
        ColSpacer(4)
        Text(
            flashVerdict(pct),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ColSpacer(18)
        Row(Modifier.fillMaxWidth()) {
            StatTile("$pct%", "Score", Modifier.weight(1f), accuracyColor(pct))
            RowSpacer(10)
            StatTile("${session.correctCount}/$total", "Correct", Modifier.weight(1f), Cyan)
            RowSpacer(10)
            StatTile("${session.bestRun}", "Best run", Modifier.weight(1f), Warn)
        }

        if (missed.isNotEmpty()) {
            ColSpacer(18)
            SectionTitle("WHAT YOU MISSED")
            ColSpacer(8)
            missed.forEach { i ->
                val c = session.cards[i]
                Panel(Modifier.fillMaxWidth()) {
                    Text(c.prompt, style = MaterialTheme.typography.titleMedium)
                    ColSpacer(8)
                    Text(
                        "Correct: ${c.options[c.answer]}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Good
                    )
                    ColSpacer(6)
                    Text(
                        c.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ColSpacer(8)
            }
        }

        ColSpacer(10)
        PrimaryButton("Another $total", { vm.startFlash(total) }, Modifier.fillMaxWidth())
        ColSpacer(8)
        OutlineButton("Back to home", {
            vm.endFlash()
            onExit()
        }, Modifier.fillMaxWidth())
        ColSpacer(24)
    }
}

/**
 * A tappable option. Deliberately tall: with only two of them there is room, and a
 * big target is what makes this mode usable one-handed.
 */
@Composable
private fun FlashOption(
    text: String,
    index: Int,
    selected: Boolean,
    isCorrect: Boolean,
    revealed: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val accent = when {
        revealed && isCorrect -> Good
        revealed && selected -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val bg = when {
        revealed && isCorrect -> Good.copy(alpha = 0.16f)
        revealed && selected -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    // Wrong options fade back once the answer is in, so the eye lands on the right one.
    val textColor = when {
        revealed && !isCorrect && !selected -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(bg)
            .border(if (revealed && (isCorrect || selected)) 2.dp else 1.dp, accent, shape)
            .clickable(enabled = !revealed) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.2f))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                ('A' + index).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        RowSpacer(14)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (revealed && isCorrect) {
            RowSpacer(8)
            Text("✓", style = MaterialTheme.typography.titleLarge, color = Good)
        } else if (revealed && selected) {
            RowSpacer(8)
            Text(
                "✕",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun flashVerdict(pct: Int): String = when {
    pct >= 90 -> "Near perfect. Try a harder focus."
    pct >= 70 -> "Good run. The misses below are worth a read."
    pct >= 50 -> "Coin-flip territory on some of these. Read the why."
    else -> "Start with the topic lesson, then come back."
}

package com.iknalos.aiprep.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iknalos.aiprep.FlashVisual
import com.iknalos.aiprep.VisualKind

/**
 * Draws the picture attached to a flashcard.
 *
 * Everything except `IMAGE` is drawn from structured JSON rather than pixels, so a
 * diagram inherits the app's theme and stays sharp at any density. `IMAGE` decodes
 * base64 bytes carried in the same JSON, which is the escape hatch for content that
 * really is a photograph or a screenshot.
 */
@Composable
fun VisualBlock(visual: FlashVisual, modifier: Modifier = Modifier) {
    Column(modifier) {
        when (visual.kind) {
            VisualKind.FLOWCHART -> Flowchart(visual.steps)
            VisualKind.TABLE -> DataTable(visual.headers, visual.rows)
            VisualKind.DIAGRAM -> Preformatted(visual.text, Cyan)
            VisualKind.CODE -> Preformatted(visual.text, MaterialTheme.colorScheme.primary)
            VisualKind.IMAGE -> Base64Image(visual.imageData)
        }
        if (visual.caption.isNotBlank()) {
            ColSpacer(8)
            Text(
                visual.caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Boxes stacked top to bottom with arrows between them. */
@Composable
private fun Flowchart(steps: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        steps.forEachIndexed { i, step ->
            if (i > 0) {
                Text(
                    "↓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * A comparison grid. Columns share the width evenly, which suits the two- and
 * three-column comparisons these cards use and avoids measuring text by hand.
 */
@Composable
private fun DataTable(headers: List<String>, rows: List<List<String>>) {
    val shape = RoundedCornerShape(12.dp)
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, line, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            headers.forEach { h ->
                Text(
                    h,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )
            }
        }
        rows.forEachIndexed { r, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (r % 2 == 1) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        else Color.Transparent
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                // Ragged rows are a content bug, not a crash: pad short ones out.
                for (c in headers.indices) {
                    Text(
                        row.getOrElse(c) { "" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Monospace block for ASCII diagrams and code. Scrolls sideways rather than
 * wrapping, because wrapping an aligned diagram destroys it.
 */
@Composable
private fun Preformatted(text: String, accent: Color) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .horizontalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            softWrap = false,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Base64Image(data: String) {
    // Decoding is cached against the payload so scrolling does not re-decode it.
    val bitmap: ImageBitmap? = remember(data) {
        try {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap == null) return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    )
}

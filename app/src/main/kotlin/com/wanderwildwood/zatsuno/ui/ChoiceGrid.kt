package com.wanderwildwood.zatsuno.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD

/**
 * Choices as a grid of tiles, from the mushroom journal's ChoiceGrid without its drawings: every
 * tile in a row the same height, its label centred, so the grid reads as a grid. Chosen is a
 * heavy border, "not sure" a dotted one; no fill, no colour, nothing moves.
 *
 * Three to a row when every label is short (Yes, No, Not sure), two otherwise.
 */
@Composable
fun ChoiceGrid(
    choices: List<Pair<String, String>>,
    chosen: Set<String>,
    dotted: Set<String> = emptySet(),
    across: Int = if (choices.all { it.second.length <= 10 }) 3 else 2,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in choices.chunked(across)) ChoiceRow(row, chosen, dotted, across, onPick)
    }
}

/**
 * The same grid as list items, one row of tiles to an item, for a list that turns a page at a
 * time: a grid in one item taller than the screen left its lower tiles where no page could
 * show them. [start] is the margin the rows sit in.
 */
fun LazyListScope.choiceRows(
    key: String,
    choices: List<Pair<String, String>>,
    chosen: () -> Set<String>,
    dotted: Set<String> = emptySet(),
    start: Dp = 20.dp,
    onPick: (String) -> Unit,
) {
    val across = if (choices.all { it.second.length <= 10 }) 3 else 2
    choices.chunked(across).forEachIndexed { i, row ->
        item(key = "$key:$i") {
            Box(Modifier.padding(start = start, end = 20.dp, bottom = 6.dp)) { ChoiceRow(row, chosen(), dotted, across, onPick) }
        }
    }
}

@Composable
private fun ChoiceRow(
    row: List<Pair<String, String>>,
    chosen: Set<String>,
    dotted: Set<String>,
    across: Int,
    onPick: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((id, label) in row) {
            Tile(label, id in chosen, id in dotted, Modifier.weight(1f).fillMaxHeight()) { onPick(id) }
        }
        // A lone last choice keeps the others' width rather than stretching across.
        repeat(across - row.size) { Column(Modifier.weight(1f)) {} }
    }
}

@Composable
private fun Tile(label: String, selected: Boolean, dotted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val edge = when {
        selected && dotted -> Modifier.dottedBorder(3.dp)
        selected -> Modifier.border(BorderStroke(3.dp, Color.Black), shape)
        else -> Modifier.border(BorderStroke(1.dp, Color.Black), shape)
    }
    Column(
        modifier = modifier
            .then(edge)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TextMMD(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

/** A dotted edge: "not sure", and a page still to check. */
fun Modifier.dottedBorder(width: androidx.compose.ui.unit.Dp = 2.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = Color.Black,
        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
    )
}

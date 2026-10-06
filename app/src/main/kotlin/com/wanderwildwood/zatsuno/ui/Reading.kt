package com.wanderwildwood.zatsuno.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.wanderwildwood.zatsuno.card.CardStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.aid.Block
import com.wanderwildwood.zatsuno.aid.Page
import com.wanderwildwood.zatsuno.compass.CompassModel
import com.wanderwildwood.zatsuno.knots.Knot

/** Text that can be selected, with Define and the other text apps behind the selection ⋮. */
@Composable
private fun Readable(content: @Composable () -> Unit) {
    SelectionContainer(Modifier.textActions()) { content() }
}

/**
 * A first-aid page, a block to a row so a swipe moves it a few steps at a time. Its sources
 * and the line about training close every page. "Calling for help" also carries the live
 * position, with the buttons that use it.
 */
@Composable
fun AidScreen(page: Page, model: CompassModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val sources = stringResource(R.string.aid_sources)
    val disclaimer = stringResource(R.string.aid_disclaimer)
    val hasPosition = page.blocks.any { it == Block.Position }
    if (hasPosition) RunWhileShown(model)
    Screen(
        title = page.title,
        onBack = onBack,
        actions = {
            BarButton(Icons.Share, stringResource(R.string.cd_share)) {
                shareText(context, page.title, page.asText(sources, disclaimer))
            }
        },
    ) { modifier ->
        val card = remember { if (hasPosition) CardStore.load(context) else null }
        LazyColumnMMD(modifier = modifier) {
            if (card != null && !card.isEmpty) {
                item(key = "card") { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { CardLines(card, bordered = true) } }
            }
            page.blocks.forEachIndexed { i, block ->
                item(key = i) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { BlockView(block, model) }
                }
            }
            item(key = "foot") {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
                    Readable { TextMMD(text = "$sources ${page.source}", style = MaterialTheme.typography.labelSmall) }
                    Spacer(Modifier.height(8.dp))
                    TextMMD(text = disclaimer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BlockView(block: Block, model: CompassModel) {
    val body = MaterialTheme.typography.bodyMedium
    when (block) {
        is Block.Heading -> Readable {
            TextMMD(text = block.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
        }
        is Block.Step -> Row(Modifier.padding(vertical = 3.dp)) {
            TextMMD(text = "${block.number}.", style = body, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
            Readable { TextMMD(text = block.text, style = body) }
        }
        is Block.Point -> Row(Modifier.padding(vertical = 3.dp)) {
            TextMMD(text = "–", style = body, modifier = Modifier.width(26.dp))
            Readable { TextMMD(text = block.text, style = body) }
        }
        is Block.Urgent -> Readable {
            TextMMD(text = block.text, style = body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        }
        // What has changed, and who says so: set apart by a rule round it, not by colour.
        is Block.Note -> Readable {
            TextMMD(
                text = block.text,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .border(BorderStroke(1.dp, Color.Black), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )
        }
        is Block.Para -> Readable { TextMMD(text = block.text, style = body, modifier = Modifier.padding(vertical = 3.dp)) }
        Block.Position -> PositionCard(model)
    }
}

/** The live position on the "Calling for help" page, and the presses that send it or call. */
@Composable
private fun PositionCard(model: CompassModel) {
    val context = LocalContext.current
    val live by model.live.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp)
            .border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        TextMMD(text = stringResource(R.string.pos_title), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        val fix = live.fix
        if (fix == null) {
            LocationPrompt(live) { model.refresh() }
        } else {
            PositionLines(fix, big = true)
            Spacer(Modifier.height(10.dp))
            WideButton(stringResource(R.string.pos_share)) { sharePosition(context, fix) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            WideButton(stringResource(R.string.pos_dial, "911"), Modifier.weight(1f)) { dial(context, "911") }
            WideButton(stringResource(R.string.pos_dial, "112"), Modifier.weight(1f)) { dial(context, "112") }
        }
    }
}

/** A knot: what it is for, then each step as a drawing with a sentence under it. */
@Composable
fun KnotScreen(knot: Knot, onBack: () -> Unit) {
    val context = LocalContext.current
    val name = stringResource(knot.name)
    val use = stringResource(knot.use)
    val captions = knot.steps.map { stringResource(it.caption) }
    Screen(
        title = name,
        onBack = onBack,
        actions = {
            BarButton(Icons.Share, stringResource(R.string.cd_share)) {
                val text = buildString {
                    appendLine(name)
                    appendLine(use)
                    appendLine()
                    captions.forEachIndexed { i, c -> appendLine("${i + 1}. $c") }
                }.trimEnd()
                shareText(context, name, text)
            }
        },
    ) { modifier ->
        // One step to a swipe: each is most of a screen tall.
        LazyColumnMMD(modifier = modifier, scrollStep = 1) {
            item(key = "use") {
                Readable {
                    TextMMD(text = use, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp))
                }
            }
            knot.steps.forEachIndexed { i, step ->
                item(key = i) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        TextMMD(text = stringResource(R.string.knot_step, (i + 1).toString()),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Image(
                            painter = painterResource(step.drawing),
                            contentDescription = captions[i],
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
                        )
                        Readable { TextMMD(text = captions[i], style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

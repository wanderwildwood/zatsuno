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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.wanderwildwood.zatsuno.card.CardStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.zatsuno.Given
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.aid.Block
import com.wanderwildwood.zatsuno.aid.Page
import com.wanderwildwood.zatsuno.aid.SeeLinks
import com.wanderwildwood.zatsuno.aid.TickStore
import com.mudita.mmd.components.checkbox.CheckboxMMD
import java.io.File
import com.wanderwildwood.zatsuno.compass.CompassModel
import com.wanderwildwood.zatsuno.compass.Fix
import com.wanderwildwood.zatsuno.knots.Knot

/** Text that can be selected, with Define and the other text apps behind the selection ⋮. */
@Composable
private fun Readable(content: @Composable () -> Unit) {
    SelectionContainer(Modifier.textActions()) { content() }
}

/**
 * A first-aid page, a block to a row so a swipe moves it a few steps at a time. It opens on
 * what to do now; the rest waits under a More row that opens and closes in place, with the
 * sources at its foot. The line about training closes every page, More open or not. "Calling
 * for help" also carries the live position, with the buttons that use it.
 *
 * [openMore] opens it ready open, for a search that found its words under More.
 *
 * A "See Shock" in the text is a link to that page ([titles], [onOpenPage]): the title
 * underlined, nothing more. [readOut] is the verbal report from "Patient assessment", shown under
 * the position on "Calling for help" so it can be read to the call-taker.
 *
 * On a checklist page (`checklist: yes`) the points are tick boxes. The ticks are kept on the
 * phone and come back after a restart; "Clear ticks" sits behind the ⋮ in the bar.
 */
@Composable
fun AidScreen(
    page: Page,
    model: CompassModel,
    onBack: () -> Unit,
    given: Given? = null,
    onDropGiven: () -> Unit = {},
    openMore: Boolean = false,
    titles: Map<String, String> = emptyMap(),
    onOpenPage: (String) -> Unit = {},
    readOut: String? = null,
) {
    val context = LocalContext.current
    val link: (String) -> AnnotatedString = remember(page.id, titles) { { text -> linked(text, titles, page.id, onOpenPage) } }
    val sources = stringResource(R.string.aid_sources)
    val disclaimer = stringResource(R.string.aid_disclaimer)
    val hasPosition = page.blocks.any { it == Block.Position }
    if (hasPosition) RunWhileShown(model)
    var moreOpen by rememberSaveable(page.id) { mutableStateOf(openMore) }
    // Its own per page: a page opened from a "See" link starts at its top, not where the last one was.
    val list = rememberSaveable(page.id, saver = LazyListState.Saver) { LazyListState() }
    val ticks = remember { TickStore(File(context.noBackupFilesDir, "ticks")) }
    var ticked by remember(page.id) { mutableStateOf(if (page.checklist) ticks.load(page.id) else emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    val onTick: (String) -> Unit = { ticked = ticks.toggle(page.id, it) }
    Screen(
        title = page.title,
        onBack = onBack,
        actions = {
            BarButton(Icons.Share, stringResource(R.string.cd_share)) {
                shareText(context, page.title, page.asText(sources, disclaimer, ticked))
            }
            if (page.checklist) BarButton(Icons.More, stringResource(R.string.cd_more)) { menuOpen = true }
        },
    ) { modifier ->
        val card = remember { if (hasPosition) CardStore.load(context) else null }
        val showCard = card != null && !card.isEmpty
        val hasMore = page.rest.isNotEmpty()
        // Opened from a search that found its words under More: start at the More row.
        LaunchedEffect(page.id) {
            if (openMore && hasMore) list.scrollToItem(page.now.size + if (showCard) 1 else 0)
        }
        LazyColumnMMD(modifier = modifier, state = list) {
            if (showCard) {
                item(key = "card") { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { CardLines(card!!, bordered = true) } }
            }
            page.now.forEachIndexed { i, block ->
                item(key = i) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { BlockView(block, model, given, onDropGiven, link, page.checklist, ticked, onTick, titles, onOpenPage) }
                }
                // The open note, right under the position it starts with.
                if (block == Block.Position && readOut != null) {
                    item(key = "readout") { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { ReadOutBox(readOut) } }
                }
            }
            if (hasMore) {
                item(key = "more") { MoreRow(moreOpen) { moreOpen = !moreOpen } }
            }
            if (moreOpen || !hasMore) {
                page.rest.forEachIndexed { i, block ->
                    item(key = page.more + i) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { BlockView(block, model, given, onDropGiven, link, page.checklist, ticked, onTick, titles, onOpenPage) }
                    }
                }
                item(key = "sources") {
                    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                        Readable { TextMMD(text = "$sources ${page.source}", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            item(key = "foot") {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = if (moreOpen || !hasMore) 8.dp else 18.dp, bottom = 16.dp)) {
                    TextMMD(text = disclaimer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (menuOpen) {
        EInkDialog(onDismiss = { menuOpen = false }) {
            TextMMD(
                text = stringResource(R.string.ticks_clear),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable {
                    menuOpen = false
                    ticks.clear(page.id)
                    ticked = emptySet()
                }.padding(vertical = 12.dp),
            )
        }
    }
}

/** The row between what to do now and the rest: More, with its chevron, opening in place. */
@Composable
private fun MoreRow(open: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        HorizontalDividerMMD()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(if (open) R.string.aid_less else R.string.aid_more), onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            TextMMD(text = stringResource(R.string.aid_more), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.ExpandLess else Icons.ExpandMore, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
        HorizontalDividerMMD()
    }
}

/** [text] with each page it sends the reader to underlined, and opening that page when tapped. */
private fun linked(text: String, titles: Map<String, String>, from: String, onOpen: (String) -> Unit): AnnotatedString {
    val links = SeeLinks.find(text, titles, from)
    if (links.isEmpty()) return AnnotatedString(text)
    val style = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        append(text)
        for (l in links) addLink(LinkAnnotation.Clickable(l.page, style) { onOpen(l.page) }, l.start, l.end)
    }
}

@Composable
private fun BlockView(
    block: Block, model: CompassModel, given: Given?, onDropGiven: () -> Unit, link: (String) -> AnnotatedString,
    checklist: Boolean, ticked: Set<String>, onTick: (String) -> Unit, titles: Map<String, String>, onOpenPage: (String) -> Unit,
) {
    val body = MaterialTheme.typography.bodyMedium
    when (block) {
        is Block.Heading -> Readable {
            TextMMD(text = block.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
        }
        is Block.Step -> Row(Modifier.padding(vertical = 3.dp)) {
            TextMMD(text = "${block.number}.", style = body, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
            Readable { TextMMD(text = link(block.text), style = body) }
        }
        // A checklist's point: a box and the words, the whole row taking the tap.
        is Block.Point -> if (checklist) Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onTick(block.text) }.padding(vertical = 3.dp),
        ) {
            CheckboxMMD(checked = block.text in ticked, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            TextMMD(text = link(block.text), style = body)
        } else Row(Modifier.padding(vertical = 3.dp)) {
            TextMMD(text = "–", style = body, modifier = Modifier.width(26.dp))
            Readable { TextMMD(text = link(block.text), style = body) }
        }
        is Block.Urgent -> Readable {
            TextMMD(text = link(block.text), style = body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        }
        // What has changed, and who says so: set apart by a rule round it, not by colour.
        is Block.Note -> Readable {
            TextMMD(
                text = link(block.text),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .border(BorderStroke(1.dp, Color.Black), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )
        }
        is Block.Para -> Readable { TextMMD(text = link(block.text), style = body, modifier = Modifier.padding(vertical = 3.dp)) }
        Block.Position -> PositionCard(model, given, onDropGiven)
        Block.Sos -> SosBlock(titles, onOpenPage)
        Block.Lightning -> LightningBlock()
    }
}

/**
 * The live position on the "Calling for help" page, and the presses that send it or call. A
 * position another app sent ([given]: a point on Topo's map, say) stands in its place, under
 * the name it came with, until the reader asks for the phone's own.
 */
@Composable
private fun PositionCard(model: CompassModel, given: Given?, onDropGiven: () -> Unit) {
    val context = LocalContext.current
    val live by model.live.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp)
            .border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        TextMMD(text = given?.let { it.label ?: stringResource(R.string.pos_given) } ?: stringResource(R.string.pos_title),
            style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        val fix = live.fix
        if (given != null) {
            val sent = remember(given) { Fix(given.lat, given.lon, null, 0L, 0L, 0f) }
            PositionLines(sent, big = true, quality = false)
            Spacer(Modifier.height(10.dp))
            WideButton(stringResource(R.string.pos_share)) { shareGiven(context, given) }
            Spacer(Modifier.height(8.dp))
            WideButton(stringResource(R.string.pos_use_phone)) { onDropGiven() }
        } else if (fix == null) {
            LocationPrompt(live) { model.refresh() }
        } else {
            PositionLines(fix, big = true)
            // A fix from before location was switched off or refused: still worth reading out,
            // with its age beside it, but say why it will not get newer.
            if (!live.permitted || !live.locationOn) {
                Spacer(Modifier.height(8.dp))
                LocationPrompt(live) { model.refresh() }
            }
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

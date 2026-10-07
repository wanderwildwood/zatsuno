package com.wanderwildwood.zatsuno.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wanderwildwood.zatsuno.Given
import com.wanderwildwood.zatsuno.Opening
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.aid.Pages
import com.wanderwildwood.zatsuno.compass.CompassModel
import com.wanderwildwood.zatsuno.key.Keys
import com.wanderwildwood.zatsuno.key.Stage
import com.wanderwildwood.zatsuno.knots.Knots
import com.wanderwildwood.zatsuno.search.Entry
import com.wanderwildwood.zatsuno.search.Kind
import com.wanderwildwood.zatsuno.search.Search

/** Where the app can be, as plain strings so the stack survives the process being stopped. */
object Route {
    const val HOME = "home"
    const val AID = "aid"
    const val KNOTS = "knots"
    const val COMPASS = "compass"
    const val SEARCH = "search"
    const val CARD = "card"
    const val CARD_EDIT = "card:edit"
    const val KEY = "key"
    const val NOTE = "key:note"
    const val READ_OUT = "key:readout"
    fun aid(id: String, more: Boolean = false) = if (more) "aid:$id:more" else "aid:$id"
    fun knot(id: String) = "knot:$id"
}

/**
 * The whole app: a stack of screens over the home screen. Back pops one.
 *
 * Opened by another app at a page ([request]), that page is the whole stack, so Back goes
 * straight back to the app that sent it there rather than through this one's home screen.
 */
@Composable
fun FieldKitApp(
    model: CompassModel = viewModel(),
    request: Opening.Request? = null,
    onRequestHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val stack = rememberSaveable(saver = stackSaver()) { mutableStateListOf(Route.HOME) }
    var about by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var keyStage by rememberSaveable { mutableStateOf(Stage.SCENE.name) }
    // A position another app sent for "Calling for help", until the reader goes back to the phone's own.
    var givenSaved by rememberSaveable { mutableStateOf<String?>(null) }
    val given = remember(givenSaved) { Given.restore(givenSaved) }
    fun open(route: String) { stack.add(route) }
    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        else if (stack.last() != Route.HOME) (context as? Activity)?.finish()
    }
    BackHandler(enabled = stack.size > 1) { back() }

    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        stack.clear()
        when (r) {
            is Opening.Request.Aid -> stack.add(Route.aid(r.page))
            is Opening.Request.Call -> {
                givenSaved = r.given?.save()
                stack.add(Route.aid(Pages.CALL))
            }
            // The recheck reminder, tapped: the key at Watch, over the home screen.
            Opening.Request.Watch -> {
                stack.add(Route.HOME)
                stack.add(Route.KEY)
                keyStage = Stage.WATCH.name
            }
        }
        onRequestHandled()
    }

    val pages = remember { Pages.all(context) }
    val titles = remember(pages) { pages.associate { it.title to it.id } }
    val keyState = remember { KeyState(context, Keys.get(context), Keys.words(context), pages.associate { it.id to it.title }) }
    val route = stack.last()
    // A sent position belongs to the visit that brought it; the call page opened later is the phone's own.
    LaunchedEffect(route) { if (route != Route.aid(Pages.CALL) && route != Route.aid(Pages.CALL, more = true)) givenSaved = null }
    when {
        route == Route.HOME -> HomeScreen(::open, { open(Route.SEARCH) }, { about = true }, keyState.incident?.let { keyState.note.time(it.started) })
        route == Route.KEY -> KeyScreen(
            state = keyState,
            stage = Stage.valueOf(keyStage),
            onStage = { keyStage = it.name },
            model = model,
            onOpenPage = { open(Route.aid(it)) },
            onNote = { open(Route.NOTE) },
            onReadOut = { open(Route.READ_OUT) },
            onBack = ::back,
        )
        route == Route.NOTE -> NoteScreen(keyState, model, onReadOut = { open(Route.READ_OUT) }, onBack = ::back) {
            keyStage = Stage.SCENE.name
            back()
        }
        route == Route.READ_OUT -> ReadOutScreen(keyState, model, ::back)
        route == Route.AID -> ListScreen(
            title = stringResource(R.string.home_aid),
            rows = pages.map { it.title to null },
            onBack = ::back,
            onPick = { open(Route.aid(pages[it].id)) },
        )
        route.startsWith("aid:") -> {
            val id = route.removePrefix("aid:").substringBefore(':')
            val page = pages.firstOrNull { it.id == id }
            if (page == null) back()
            else AidScreen(
                page, model, ::back, given, onDropGiven = { givenSaved = null }, openMore = route.endsWith(":more"),
                titles = titles, onOpenPage = { open(Route.aid(it)) },
                readOut = keyState.incident?.let { keyState.note.readOut(it) },
            )
        }
        route == Route.KNOTS -> {
            val rows = Knots.all.map { stringResource(it.name) to stringResource(it.use) }
            ListScreen(stringResource(R.string.home_knots), rows, ::back) { open(Route.knot(Knots.all[it].id)) }
        }
        route.startsWith("knot:") -> {
            val knot = Knots.find(route.removePrefix("knot:"))
            if (knot == null) back() else KnotScreen(knot, ::back)
        }
        route == Route.COMPASS -> CompassScreen(model, ::back)
        route == Route.CARD -> CardScreen(::back) { open(Route.CARD_EDIT) }
        route == Route.CARD_EDIT -> CardEditScreen(::back)
        route == Route.SEARCH -> {
            val compassTitle = stringResource(R.string.home_compass)
            val compassKeys = stringResource(R.string.compass_keywords)
            val cardTitle = stringResource(R.string.card_title)
            val cardKeys = stringResource(R.string.card_keywords)
            val knotTexts = Knots.all.map { k ->
                Triple(k, stringResource(k.name), (listOf(stringResource(k.use)) + k.steps.map { stringResource(it.caption) }).joinToString("\n"))
            }
            val entries = remember(pages) {
                pages.map { Entry(Kind.AID, it.id, it.title, it.keywords, it.searchText()) } +
                    knotTexts.map { (k, name, body) -> Entry(Kind.KNOT, k.id, name, emptyList(), body) } +
                    Entry(Kind.COMPASS, "compass", compassTitle, compassKeys.split(','), "") +
                    Entry(Kind.CARD, "card", cardTitle, cardKeys.split(','), "")
            }
            val hits = remember(query, entries) { Search.find(entries, query) }
            SearchScreen(query, { query = it }, hits, ::back) { hit ->
                when (hit.entry.kind) {
                    Kind.AID -> {
                        val page = pages.first { it.id == hit.entry.id }
                        open(Route.aid(page.id, more = Search.onlyIn(page.restText(), page.nowText(), query)))
                    }
                    Kind.KNOT -> open(Route.knot(hit.entry.id))
                    Kind.COMPASS -> open(Route.COMPASS)
                    Kind.CARD -> open(Route.CARD)
                }
            }
        }
        else -> back()
    }
    if (about) AboutDialog(onDismiss = { about = false })
}

private fun stackSaver() = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf<String>().apply { addAll(it) } },
)

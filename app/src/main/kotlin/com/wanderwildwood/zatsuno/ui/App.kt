package com.wanderwildwood.zatsuno.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
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
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.aid.Pages
import com.wanderwildwood.zatsuno.compass.CompassModel
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
    fun aid(id: String) = "aid:$id"
    fun knot(id: String) = "knot:$id"
}

/** The whole app: a stack of screens over the home screen. Back pops one. */
@Composable
fun FieldKitApp(model: CompassModel = viewModel()) {
    val context = LocalContext.current
    val stack = rememberSaveable(saver = stackSaver()) { mutableStateListOf(Route.HOME) }
    var about by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    fun open(route: String) { stack.add(route) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }

    val pages = remember { Pages.all(context) }
    val route = stack.last()
    when {
        route == Route.HOME -> HomeScreen(::open, { open(Route.SEARCH) }, { about = true })
        route == Route.AID -> ListScreen(
            title = stringResource(R.string.home_aid),
            rows = pages.map { it.title to null },
            onBack = ::back,
            onPick = { open(Route.aid(pages[it].id)) },
        )
        route.startsWith("aid:") -> {
            val page = pages.firstOrNull { it.id == route.removePrefix("aid:") }
            if (page == null) back() else AidScreen(page, model, ::back)
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
                    Kind.AID -> open(Route.aid(hit.entry.id))
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

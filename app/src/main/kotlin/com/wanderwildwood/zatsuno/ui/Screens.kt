package com.wanderwildwood.zatsuno.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.search.Hit
import com.wanderwildwood.zatsuno.search.Kind

/** The bar every screen has: a title, Back where there is somewhere to go, and its own actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBarMMD(
        title = { TextMMD(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
        actions = { actions() },
    )
}

/** A screen: the bar, then whatever it holds, on the panel's white. */
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { Bar(title, onBack, actions) },
    ) { padding -> content(Modifier.padding(padding).fillMaxSize()) }
}

/**
 * The home screen. The call for help comes first, because it is what is wanted in a hurry,
 * then the three parts. Search and About sit in the bar.
 */
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onSearch: () -> Unit, onAbout: () -> Unit, keyStarted: String? = null) {
    Screen(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = {
            BarButton(Icons.Search, stringResource(R.string.cd_search), onSearch)
            BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
        },
    ) { modifier ->
        Column(modifier) {
            HomeRow(stringResource(R.string.home_call)) { onOpen(Route.aid(com.wanderwildwood.zatsuno.aid.Pages.CALL)) }
            HorizontalDividerMMD()
            HomeRow(stringResource(R.string.home_card)) { onOpen(Route.CARD) }
            HorizontalDividerMMD()
            HomeRow(stringResource(R.string.home_key), keyStarted?.let { stringResource(R.string.home_key_started, it) }) { onOpen(Route.KEY) }
            HorizontalDividerMMD()
            HomeRow(stringResource(R.string.home_aid)) { onOpen(Route.AID) }
            HorizontalDividerMMD()
            HomeRow(stringResource(R.string.home_knots)) { onOpen(Route.KNOTS) }
            HorizontalDividerMMD()
            HomeRow(stringResource(R.string.home_compass)) { onOpen(Route.COMPASS) }
            HorizontalDividerMMD()
        }
    }
}

/** A row of the home screen; [line] under it only while there is something to say (a note open since …). */
@Composable
private fun HomeRow(label: String, line: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (line == null) 26.dp else 14.dp),
    ) {
        TextMMD(text = label, style = MaterialTheme.typography.headlineSmall)
        if (line != null) TextMMD(text = line, style = MaterialTheme.typography.labelSmall)
    }
}

/** A plain list of names to choose from, with an optional line under each. */
@Composable
fun ListScreen(
    title: String,
    rows: List<Pair<String, String?>>,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
) {
    Screen(title, onBack) { modifier ->
        LazyColumnMMD(modifier = modifier) {
            rows.forEachIndexed { i, (name, line) ->
                item(key = i) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(i) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        TextMMD(text = name, style = MaterialTheme.typography.bodyLarge)
                        if (line != null) TextMMD(text = line, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** One field, and what it finds across first aid, knots and the compass. */
@Composable
fun SearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    hits: List<Hit>,
    onBack: () -> Unit,
    onOpen: (Hit) -> Unit,
) {
    Screen(stringResource(R.string.cd_search), onBack) { modifier ->
        Column(modifier) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
                TextFieldMMD(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { TextMMD(text = stringResource(R.string.search_hint), style = MaterialTheme.typography.labelSmall) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f).focusRequester(focus).textActions(),
                )
                if (query.isNotEmpty()) BarButton(Icons.Close, stringResource(R.string.cd_clear)) { onQuery("") }
            }
            if (query.isNotBlank() && hits.isEmpty()) {
                TextMMD(
                    text = stringResource(R.string.search_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                hits.forEach { hit ->
                    item(key = "${hit.entry.kind}:${hit.entry.id}") {
                        val kind = stringResource(
                            when (hit.entry.kind) {
                                Kind.AID -> R.string.kind_aid
                                Kind.KNOT -> R.string.kind_knot
                                Kind.COMPASS -> R.string.kind_compass
                                Kind.CARD -> R.string.kind_card
                            },
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(hit) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            TextMMD(text = hit.entry.title, style = MaterialTheme.typography.bodyLarge)
                            TextMMD(
                                text = if (hit.snippet != null) "$kind · ${hit.snippet}" else kind,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

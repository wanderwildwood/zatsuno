package com.wanderwildwood.zatsuno.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.card.CardStore
import com.wanderwildwood.zatsuno.compass.CompassModel
import com.wanderwildwood.zatsuno.key.Answers
import com.wanderwildwood.zatsuno.key.Atom
import com.wanderwildwood.zatsuno.key.Fields
import com.wanderwildwood.zatsuno.key.Incident
import com.wanderwildwood.zatsuno.key.IncidentStore
import com.wanderwildwood.zatsuno.key.Key
import com.wanderwildwood.zatsuno.key.Note
import com.wanderwildwood.zatsuno.key.Question
import com.wanderwildwood.zatsuno.key.Ranking
import com.wanderwildwood.zatsuno.key.Recheck
import com.wanderwildwood.zatsuno.key.Stage
import com.wanderwildwood.zatsuno.key.Words
import com.wanderwildwood.zatsuno.key.rank
import com.wanderwildwood.zatsuno.key.vitalRows
import kotlinx.coroutines.delay

/**
 * The open incident, and every change to it. Each change is one timed event, saved at once,
 * and the recheck alarm is set again from it.
 */
class KeyState(
    private val context: Context,
    val key: Key,
    val words: Words,
    val titles: Map<String, String>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var incident by mutableStateOf(IncidentStore.load(context))
        private set

    val note: Note get() = Note(key, words, titles)
    val answers: Answers get() = incident?.answers() ?: Answers()
    val ranking: Ranking get() = rank(key, answers)

    private fun change(make: (Long) -> Incident.Event) {
        val t = now()
        val base = incident ?: Incident(t)
        val e = make(t)
        // Typing a line is one event, not one per letter: the latest edit replaces the one before.
        val last = base.events.lastOrNull()
        val next = if (e is Incident.Text && last is Incident.Text && last.field == e.field) base.copy(events = base.events.dropLast(1) + e)
            else base + e
        incident = next
        IncidentStore.save(context, next)
    }

    /** One answer; the same one again takes it back. */
    fun answer(q: String, a: String) {
        val current = answers.single(q) ?: if (q in answers.notSure) Key.NOT_SURE else null
        change { Incident.Answer(it, q, if (current == a) "" else a) }
    }

    /** One more pick, or one fewer, for a pick-all question. */
    fun toggle(q: String, a: String) {
        val picked = incident?.picked(q).orEmpty()
        val next = if (a in picked) picked - a else picked + a
        change { Incident.Answer(it, q, next.joinToString(",")) }
    }

    fun write(field: String, text: String) = change { Incident.Text(it, field, text) }
    fun done(text: String) = change { Incident.Done(it, text.trim()) }
    fun check(avpu: String?, pulse: Int?, breaths: Int?, skin: List<String>, rhythm: String?, quality: String?) {
        change { Incident.Check(it, avpu, pulse, breaths, skin, rhythm, quality) }
        if (avpu != null && answers.single("avpu") != avpu) answer("avpu", avpu)
        Recheck.seen(context)
    }

    /** Where the phone is, kept once and again when it is five minutes stale. */
    fun where(lat: Double, lon: Double, accuracy: Float?, at: Long) {
        val inc = incident ?: return
        val last = inc.where()
        if (last == null || at - last.at > 5 * 60_000L) change { Incident.Where(at, lat, lon, accuracy) }
    }

    fun startOver() {
        IncidentStore.clear(context)
        incident = null
    }

    fun text(field: String): String = incident?.texts()?.get(field)?.text.orEmpty()

    /** The words for why a page is there, in the person's own answers. */
    fun say(atom: Atom): String = when {
        atom.question == "pulse" -> incident?.checks()?.lastOrNull()?.pulse?.let { words.get("note_pulse", it.toString()) } ?: atom.toString()
        atom.question == "resp" -> incident?.checks()?.lastOrNull()?.breaths?.let { words.get("note_breaths", it.toString()) } ?: atom.toString()
        atom.question == "vskin" -> incident?.checks()?.lastOrNull()?.skin?.joinToString(", ") { words.get("key_skin_$it") } ?: atom.toString()
        key.question(atom.question)?.grid == true -> {
            val picks = answers.sure[atom.question].orEmpty().filter { atom.holds(Answers(mapOf(atom.question to setOf(it)))) }
            picks.joinToString(", ") { words.place(it.substringBefore('.')) + " " + words.finding(it.substringAfter('.')) }
        }
        key.question(atom.question)?.id == "what" -> words.short(atom.question) + ": " + words.answer(atom.question, atom.answer)
        atom.answer == "yes" -> words.short(atom.question)
        atom.answer == "no" -> words.short(atom.question) + ": " + words.answer(atom.question, "no")
        else -> words.answer(atom.question, atom.answer)
    }
}

/**
 * The bold lines a life threat puts at the top of every stage, each with its page under it.
 * CPR's is for someone unresponsive: while they are alert or answer to voice, gasping is
 * respiratory distress, and that line shows in its place.
 */
private fun alarms(ranking: Ranking, answers: Answers) = ALARMS.filter { (page, _) ->
    ranking.fits.any { it.page == page } && !(page == "cpr" && answers.single("avpu") in setOf("alert", "voice"))
}

private val ALARMS = listOf(
    "cpr" to R.string.key_alarm_cpr,
    "bleeding" to R.string.key_alarm_bleeding,
    "choking" to R.string.key_alarm_choking,
    "anaphylaxis" to R.string.key_alarm_anaphylaxis,
    "breathing" to R.string.key_alarm_breathing,
)

/** Short, for the strip. */
private val STAGE_NAMES = listOf(
    R.string.key_stage_scene, R.string.key_stage_primary, R.string.key_stage_call,
    R.string.key_stage_secondary, R.string.key_stage_treat, R.string.key_stage_monitor,
)

/** In full, for the heading of each stage and the button to it. */
private val STAGE_FULL = listOf(
    R.string.key_stage_scene_full, R.string.key_stage_primary_full, R.string.key_stage_call_full,
    R.string.key_stage_secondary_full, R.string.key_stage_treat_full, R.string.key_stage_monitor_full,
)

/** SCTM words for the vital signs, in three rows: color, temperature, moisture. */
private val SKIN = listOf(listOf("pink", "pale", "grey", "flushed", "blue"), listOf("warm", "cool", "hot"), listOf("dry", "clammy", "sweaty"))

/** HR rhythm and RR quality, one of each. */
private val RHYTHM = listOf("regular", "irregular")
private val QUALITY = listOf("easy", "labored", "noisy")

/**
 * Patient assessment: the Patient Assessment System, one stage on screen at a time under a
 * strip that names all six. Nothing is locked; tapping a stage goes there. Answers gather the
 * problem list as they go, and every page opens at its "Do this now" part.
 */
@Composable
fun KeyScreen(
    state: KeyState,
    stage: Stage,
    onStage: (Stage) -> Unit,
    model: CompassModel,
    onOpenPage: (String) -> Unit,
    onNote: () -> Unit,
    onReadOut: () -> Unit,
    onBack: () -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(stage) { list.scrollToItem(0) }
    val context = LocalContext.current
    val draft = remember { VitalsDraft() }
    LaunchedEffect(draft.counting) {
        if (draft.counting) { delay(15_000); Recheck.buzz(context); draft.counting = false }
    }
    if (stage == Stage.CALL) RecordWhere(state, model)
    Screen(
        title = stringResource(R.string.key_title),
        onBack = onBack,
        actions = { BarText(stringResource(R.string.key_note), onNote) },
    ) { modifier ->
        Column(modifier) {
            StageStrip(stage, onStage)
            HorizontalDividerMMD()
            val ranking = state.ranking
            val alarms = alarms(ranking, state.answers)
            // A new life threat goes to the top of the list, and the list goes to it at once.
            LaunchedEffect(alarms.map { it.first }) { if (alarms.isNotEmpty()) list.scrollToItem(0) }
            LazyColumnMMD(modifier = Modifier.fillMaxWidth().weight(1f), state = list) {
                for ((page, line) in alarms) {
                    run {
                        item(key = "alarm:$page") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                                TextMMD(text = stringResource(line), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                PageRow(state.titles[page] ?: page, null) { onOpenPage(page) }
                            }
                        }
                    }
                }
                item(key = "stage-head") { Heading(stringResource(STAGE_FULL[stage.ordinal])) }
                when (stage) {
                    Stage.SCENE -> scene(state, onOpenPage)
                    Stage.PRIMARY -> {
                        questions(state, Stage.PRIMARY)
                        item(key = "expose") { Reminder(stringResource(R.string.key_expose)) }
                    }
                    Stage.CALL -> call(state, ranking, onOpenPage, onReadOut)
                    Stage.SECONDARY -> secondary(state, ranking, draft)
                    Stage.TREAT -> treat(state, ranking, onOpenPage, onStage)
                    Stage.MONITOR -> monitor(state, draft, onStage)
                }
                val next = Stage.entries.getOrNull(stage.ordinal + 1)
                if (next != null && !(stage == Stage.SCENE && state.answers.single("safe") == "no")) {
                    item(key = "next") {
                        WideButton(stringResource(R.string.key_next, stringResource(STAGE_FULL[next.ordinal])),
                            Modifier.fillMaxWidth().padding(20.dp)) { onStage(next) }
                    }
                }
            }
        }
    }
}

/** Keeps the phone's position in the note while a screen that needs it is open. */
@Composable
private fun RecordWhere(state: KeyState, model: CompassModel) {
    RunWhileShown(model)
    val live by model.live.collectAsState()
    LaunchedEffect(live.fix) { live.fix?.let { state.where(it.lat, it.lon, it.accuracy, it.time) } }
}

@Composable
private fun BarText(label: String, onClick: () -> Unit) {
    TextMMD(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** Scene · Primary · Call · Secondary · Treat · Monitor, the current one in bold. Two rows if need be. */
@Composable
private fun StageStrip(current: Stage, onStage: (Stage) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (s in Stage.entries) {
            TextMMD(
                text = stringResource(STAGE_NAMES[s.ordinal]),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { onStage(s) }
                    .then(if (s == current) Modifier.border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(6.dp)) else Modifier)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

/** A page to open, with why it is there under it. */
@Composable
private fun PageRow(title: String, why: String?, dotted: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (dotted) Modifier.dottedBorder() else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (dotted) 10.dp else 0.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyLarge)
            if (why != null) TextMMD(text = why, style = MaterialTheme.typography.labelSmall)
        }
        TextMMD(text = "›", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Heading(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp))
}

/** A step with no answer: gloves on, expose. */
@Composable
private fun Reminder(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
}

/** The answers on a question's buttons: "Not sure" last, or "Not decided yet" on a decision. */
private fun choices(state: KeyState, q: Question): List<Pair<String, String>> {
    val w = state.words
    return q.answers.map { it to w.answer(q.id, it) } + when {
        q.many -> emptyList()
        q.decision -> listOf(Key.NOT_SURE to w.decided(q.id, Key.NOT_SURE))
        else -> listOf(Key.NOT_SURE to w.answer(q.id, Key.NOT_SURE))
    }
}

/** One question: its margin letter, the words, and the answers as tiles, "Not sure" last. */
@Composable
private fun QuestionView(state: KeyState, q: Question) {
    val w = state.words
    val a = state.answers
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)) {
        TextMMD(text = q.label.orEmpty(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            modifier = Modifier.width(16.dp).padding(top = 3.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(text = w.question(q.id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (q.tag != null) TextMMD(text = q.tag, style = MaterialTheme.typography.labelSmall)
            }
            if (q.many) TextMMD(text = stringResource(R.string.key_pick_all), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            val chosen = a.sure[q.id].orEmpty() + if (q.id in a.notSure) setOf(Key.NOT_SURE) else emptySet()
            ChoiceGrid(choices(state, q), chosen, dotted = setOf(Key.NOT_SURE)) { pick ->
                if (q.many) state.toggle(q.id, pick) else state.answer(q.id, pick)
            }
        }
    }
}

/** The questions of a stage (and part) that are showing: follow-ups once what they follow holds. */
private fun shown(state: KeyState, stage: Stage, ranking: Ranking? = null, part: String? = null): List<Question> {
    val a = state.answers
    val settling = ranking?.still?.mapNotNull { it.ask }?.toSet().orEmpty()
    return state.key.questions.filter { q ->
        q.stage == stage && !q.grid && q.id != "you" && (part == null || q.part == part) &&
            (q.after.isEmpty() && !q.settle || q.after.any { it.holds(a) } || q.id in settling || q.id in a.sure || q.id in a.notSure)
    }
}

private fun LazyListScope.questions(state: KeyState, stage: Stage, ranking: Ranking? = null, part: String? = null) {
    for (q in shown(state, stage, ranking, part)) question(state, q)
}

/** A question as list items: its words, then a row of answers to an item. */
private fun LazyListScope.question(state: KeyState, q: Question) {
    item(key = "q:${q.id}") { QuestionHead(state, q) }
    choiceRows("a:${q.id}", choices(state, q), { chosen(state, q) }, dotted = setOf(Key.NOT_SURE), start = 20.dp) { pick ->
        if (q.many) state.toggle(q.id, pick) else state.answer(q.id, pick)
    }
}

private fun chosen(state: KeyState, q: Question): Set<String> {
    val a = state.answers
    return a.sure[q.id].orEmpty() + if (q.id in a.notSure) setOf(Key.NOT_SURE) else emptySet()
}

@Composable
private fun QuestionHead(state: KeyState, q: Question) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 6.dp)) {
        TextMMD(text = q.label.orEmpty(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            modifier = Modifier.width(16.dp).padding(top = 3.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(text = state.words.question(q.id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (q.tag != null) TextMMD(text = q.tag, style = MaterialTheme.typography.labelSmall)
            }
            if (q.many) TextMMD(text = stringResource(R.string.key_pick_all), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun LazyListScope.scene(state: KeyState, onOpenPage: (String) -> Unit) {
    val safe = state.key.question("safe")!!
    question(state, safe)
    item(key = "bsi") { Reminder(stringResource(R.string.key_bsi)) }
    if (state.answers.single("safe") == "no") {
        item(key = "unsafe") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                TextMMD(text = stringResource(R.string.key_unsafe), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                PageRow(state.titles["call"] ?: "", null) { onOpenPage("call") }
                PageRow(state.titles["scene"] ?: "", null) { onOpenPage("scene") }
            }
        }
        return
    }
    for (q in shown(state, Stage.SCENE).filter { it.id != "safe" }) question(state, q)
    item(key = "f:${Fields.RESOURCES}") { TextLine(state, Fields.RESOURCES, null) }
    item(key = "scene-page") {
        Column(Modifier.padding(horizontal = 20.dp)) { PageRow(state.titles["scene"] ?: "", null) { onOpenPage("scene") } }
    }
}

private fun LazyListScope.call(state: KeyState, ranking: Ranking, onOpenPage: (String) -> Unit, onReadOut: () -> Unit) {
    val flags = ranking.fits.firstOrNull { it.page == "call" }?.why.orEmpty()
    item(key = "flags") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (flags.isEmpty()) {
                TextMMD(text = stringResource(R.string.key_flags_none), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            } else {
                TextMMD(text = stringResource(R.string.key_flags), style = MaterialTheme.typography.bodyMedium)
                TextMMD(text = flags.map(state::say).distinct().joinToString("; "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            PageRow(state.titles["call"] ?: "", null) { onOpenPage("call") }
            PageRow(stringResource(R.string.key_read_out), null, onClick = onReadOut)
        }
    }
    questions(state, Stage.CALL)
}

/**
 * The secondary assessment: chief complaint, head-to-toe exam, vital signs, SAMPLE history
 * (OPQRST under its S), then the focused checks. For an alert patient with an illness rather
 * than an injury, the history comes before the exam, as courses teach.
 */
private fun LazyListScope.secondary(state: KeyState, ranking: Ranking, draft: VitalsDraft) {
    val a = state.answers
    val medical = a.sure["what"]?.contains("by_itself") == true && a.single("avpu") == "alert"
    item(key = "chief-head") { Heading(stringResource(R.string.key_look_see)) }
    questions(state, Stage.SECONDARY, ranking, "chief")
    if (medical) { sample(state, ranking); exam(state); vitals(state, draft) }
    else { exam(state); vitals(state, draft); sample(state, ranking) }
    val focused = shown(state, Stage.SECONDARY, ranking, "focused")
    if (focused.isNotEmpty()) {
        item(key = "focused-head") { Heading(stringResource(R.string.key_focused)) }
        for (q in focused) {
            if (q.id == "face" || q.id == "befast" && focused.none { it.id == "face" }) {
                item(key = "stroke-head") {
                    TextMMD(text = stringResource(R.string.key_stroke_check), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 6.dp))
                }
            }
            question(state, q)
        }
    }
}

/** The head-to-toe exam: a row of findings at each place, DOTS and the rest. */
private fun LazyListScope.exam(state: KeyState) {
    val inc = state.incident
    val body = state.key.questions.first { it.grid }
    item(key = "body-head") {
        Heading(stringResource(R.string.key_look_body))
        TextMMD(text = stringResource(R.string.key_look_body_hint), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp))
    }
    // The place over its findings, so each finding has the full width: "deformed" fits on one line.
    for (place in body.places) item(key = "body:$place") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp)) {
            TextMMD(text = state.words.place(place), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 3.dp))
            val picked = inc?.picked(body.id).orEmpty()
            ChoiceGrid(
                choices = body.findingsAt(place).map { "$place.$it" to state.words.finding(it) },
                chosen = picked,
                across = 3,
            ) { state.toggle(body.id, it) }
        }
    }
}

/** The SAMPLE history: whether the patient is the phone's owner, then each letter, with OPQRST under S. */
private fun LazyListScope.sample(state: KeyState, ranking: Ranking) {
    val a = state.answers
    item(key = "sample-head") { Heading(stringResource(R.string.key_sample)) }
    question(state, state.key.question("you")!!)
    if (a.single("you") == "yes") item(key = "card") { CardOffer(state) }
    item(key = "f:who") { TextLine(state, Fields.WHO, null) }
    val pain = a.sure["seen"].orEmpty().any { it in setOf("pain", "chest_pain", "hurt_limb") }
    for ((f, _) in Fields.SAMPLE) {
        item(key = "f:$f") { TextLine(state, f, null) }
        if (f == "symptoms" && pain) {
            item(key = "opqrst-head") {
                TextMMD(text = stringResource(R.string.key_opqrst), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp))
            }
            for ((o, _) in Fields.OPQRST) item(key = "f:$o") { TextLine(state, o, null) }
        }
    }
    questions(state, Stage.SECONDARY, ranking, "sample")
}

/** The emergency card's allergies and medications, offered only when the patient is its owner. */
@Composable
private fun CardOffer(state: KeyState) {
    val context = LocalContext.current
    val card = remember { CardStore.load(context) }
    val lines = listOfNotNull(
        card.allergies.trim().takeIf { it.isNotEmpty() }?.let { stringResource(R.string.card_allergies_short) + ": " + it },
        card.medications.trim().takeIf { it.isNotEmpty() }?.let { stringResource(R.string.card_medications_short) + ": " + it },
    )
    if (lines.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
            .border(BorderStroke(1.dp, Color.Black), RoundedCornerShape(8.dp)).padding(10.dp),
    ) {
        TextMMD(text = stringResource(R.string.key_card_offer, lines.joinToString(". ")), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        WideButton(stringResource(R.string.key_card_use)) {
            if (card.allergies.isNotBlank()) state.write("allergies", card.allergies.trim())
            if (card.medications.isNotBlank()) state.write("medicines", card.medications.trim())
        }
    }
}

/** A line written in, under its letter: SAMPLE and OPQRST. */
@Composable
private fun TextLine(state: KeyState, field: String, letter: String?) {
    var value by rememberSaveable(field) { mutableStateOf(state.text(field)) }
    // Filled from the card, or emptied by Start over: the field follows the note.
    val stored = state.text(field)
    LaunchedEffect(stored) { if (stored != value) value = stored }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 2.dp)) {
        TextMMD(text = letter.orEmpty(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            modifier = Modifier.width(16.dp).padding(top = 3.dp))
        Column(Modifier.weight(1f)) {
            TextMMD(text = state.words.get("key_field_$field"), style = MaterialTheme.typography.labelSmall)
            TextFieldMMD(
                value = value,
                onValueChange = { value = it; state.write(field, it) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().textActions(),
            )
        }
    }
}

private fun LazyListScope.treat(state: KeyState, ranking: Ranking, onOpenPage: (String) -> Unit, onStage: (Stage) -> Unit) {
    val skip = setOf("call", "scene")
    val fits = ranking.fits.filter { it.page !in skip }
    item(key = "fits-head") { Heading(stringResource(R.string.key_fits)) }
    if (fits.isEmpty()) item(key = "fits-none") {
        TextMMD(text = stringResource(R.string.key_fits_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
    }
    for (f in fits) item(key = "fit:${f.page}") {
        Column(Modifier.padding(horizontal = 20.dp)) {
            PageRow(state.titles[f.page] ?: f.page, f.why.map(state::say).filter { it.isNotEmpty() }.distinct().joinToString(", ")) { onOpenPage(f.page) }
        }
    }
    if (ranking.still.isNotEmpty()) {
        item(key = "still-head") { HorizontalDividerMMD(); Heading(stringResource(R.string.key_still)) }
        for (s in ranking.still) item(key = "still:${s.page}") {
            var asking by rememberSaveable(s.page) { mutableStateOf(false) }
            Column(Modifier.padding(horizontal = 20.dp)) {
                val ask = s.ask?.let { state.key.question(it) }
                // Settled by the vital signs rather than a question: say which, and go there.
                val vital = s.ask?.takeIf { it in Key.DERIVED }
                PageRow(
                    state.titles[s.page] ?: s.page,
                    ask?.let { state.words.question(it.id) } ?: vital?.let { state.words.question(it) } ?: stringResource(R.string.key_cannot_settle),
                    dotted = true,
                ) { if (ask != null) asking = !asking else if (vital != null) onStage(Stage.MONITOR) else onOpenPage(s.page) }
                if (asking && ask != null) Box(Modifier.padding(bottom = 6.dp)) { QuestionView(state, ask) }
            }
        }
    }
    if (ranking.ruledOut.isNotEmpty()) item(key = "ruled") {
        var open by rememberSaveable { mutableStateOf(false) }
        Column(Modifier.padding(horizontal = 20.dp)) {
            HorizontalDividerMMD()
            PageRow(stringResource(R.string.key_ruled_out, ranking.ruledOut.size.toString()), null) { open = !open }
            if (open) for (r in ranking.ruledOut) {
                PageRow(state.titles[r.page] ?: r.page,
                    stringResource(R.string.key_ruled_by, r.by.joinToString(", ") { "${state.words.short(it.question)}: ${state.words.answer(it.question, it.answer)}" }, state.note.time(r.at))) { onOpenPage(r.page) }
            }
        }
    }
    item(key = "not-fine") {
        TextMMD(text = stringResource(R.string.key_not_fine), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }
    item(key = "done") { DoneLine(state) }
    item(key = "decided") {
        TextMMD(text = stringResource(R.string.key_decided), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
    }
    questions(state, Stage.TREAT)
}

@Composable
private fun DoneLine(state: KeyState) {
    var value by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        TextFieldMMD(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            placeholder = { TextMMD(text = stringResource(R.string.key_done_hint), style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.fillMaxWidth().textActions(),
        )
        Spacer(Modifier.height(6.dp))
        WideButton(stringResource(R.string.key_done_add)) { if (value.isNotBlank()) { state.done(value); value = "" } }
    }
}

/** The check being entered under "now", until it is saved. */
class VitalsDraft {
    var avpu by mutableStateOf<String?>(null)
    var pulse by mutableStateOf("")
    var rhythm by mutableStateOf<String?>(null)
    var breaths by mutableStateOf("")
    var quality by mutableStateOf<String?>(null)
    var skin by mutableStateOf(listOf<String>())
    var counting by mutableStateOf(false)

    fun clear() { avpu = null; pulse = ""; rhythm = null; breaths = ""; quality = null; skin = emptyList() }
}

/**
 * The vital signs: one column per set, the newest on the right, and the next set entered under
 * "now": LOR, HR with its rhythm, RR with its quality, and SCTM. HR and RR are counted for 15
 * seconds and multiplied by four; "Count 15 s" buzzes once at the end, and nothing on screen
 * moves while it counts. A row to an item, so the list can turn a page between them. The
 * secondary assessment takes the first set; Monitoring takes each one after.
 */
private fun LazyListScope.vitals(state: KeyState, draft: VitalsDraft) {
    val w = state.words
    val checks = state.incident?.checks().orEmpty().takeLast(3)
    item(key = "v:head") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            TextMMD(text = stringResource(R.string.key_vitals), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp))
            if (checks.isNotEmpty()) {
                val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                for ((name, cells) in vitalRows(w, checks) { state.note.time(it) }) Row(Modifier.fillMaxWidth()) {
                    TextMMD(text = name, style = mono, modifier = Modifier.width(64.dp))
                    for (c in cells) TextMMD(text = c, style = mono, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            TextMMD(text = stringResource(R.string.key_now), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            TextMMD(text = w.question("avpu"), style = MaterialTheme.typography.bodySmall)
        }
    }
    choiceRows("v:avpu", listOf("alert", "voice", "pain", "none").map { it to w.answer("avpu", it) }, { setOfNotNull(draft.avpu) }) {
        draft.avpu = if (draft.avpu == it) null else it
    }
    item(key = "v:pulse") { Box(Modifier.padding(horizontal = 20.dp)) { CountField(stringResource(R.string.key_pulse_15), draft.pulse) { draft.pulse = it } } }
    choiceRows("v:rhythm", RHYTHM.map { it to w.get("key_rhythm_$it") }, { setOfNotNull(draft.rhythm) }) {
        draft.rhythm = if (draft.rhythm == it) null else it
    }
    item(key = "v:breaths") { Box(Modifier.padding(horizontal = 20.dp)) { CountField(stringResource(R.string.key_breaths_15), draft.breaths) { draft.breaths = it } } }
    choiceRows("v:quality", QUALITY.map { it to w.get("key_quality_$it") }, { setOfNotNull(draft.quality) }) {
        draft.quality = if (draft.quality == it) null else it
    }
    item(key = "v:skin") {
        TextMMD(text = stringResource(R.string.key_skin), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp))
    }
    SKIN.forEachIndexed { i, row ->
        choiceRows("v:skin$i", row.map { it to w.get("key_skin_$it") }, { draft.skin.toSet() }) { s ->
            // One word from each row: tapping another in the same row swaps it.
            draft.skin = if (s in draft.skin) draft.skin - s else draft.skin.filterNot { it in row } + s
        }
    }
    item(key = "v:buttons") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            WideButton(stringResource(if (draft.counting) R.string.key_counting else R.string.key_count), Modifier.weight(1f)) { if (!draft.counting) draft.counting = true }
            WideButton(stringResource(R.string.key_save_check), Modifier.weight(1f)) {
                val p = draft.pulse.toIntOrNull()?.times(4)
                val b = draft.breaths.toIntOrNull()?.times(4)
                if (draft.avpu != null || p != null || b != null || draft.skin.isNotEmpty() || draft.rhythm != null || draft.quality != null) {
                    val order = SKIN.flatten()
                    state.check(draft.avpu, p, b, draft.skin.sortedBy(order::indexOf), draft.rhythm, draft.quality)
                    draft.clear()
                }
            }
        }
    }
}

/** Monitoring: the vital signs again, when the next reassessment is due, and the way back to the primary assessment. */
private fun LazyListScope.monitor(state: KeyState, draft: VitalsDraft, onStage: (Stage) -> Unit) {
    vitals(state, draft)
    state.incident?.let { inc ->
        item(key = "v:next") {
            TextMMD(text = stringResource(R.string.key_next_check, state.note.time(inc.nextCheck(state.key))),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
    }
    item(key = "recheck") {
        WideButton(stringResource(R.string.key_recheck), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) { onStage(Stage.PRIMARY) }
    }
}

@Composable
private fun CountField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = label, style = MaterialTheme.typography.labelSmall)
            TextFieldMMD(
                value = value,
                onValueChange = { v -> onChange(v.filter(Char::isDigit).take(3)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val n = value.toIntOrNull()
        TextMMD(text = if (n != null) stringResource(R.string.key_per_minute, (n * 4).toString()) else "",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp).padding(start = 10.dp))
    }
}

/**
 * The SOAP note, top to bottom, with Send and "Verbal report" in the bar. At its foot an
 * armed row: "Start over", tapped twice. It clears the note and is the only way to.
 */
@Composable
fun NoteScreen(state: KeyState, model: CompassModel, onReadOut: () -> Unit, onBack: () -> Unit, onCleared: () -> Unit) {
    val context = LocalContext.current
    RecordWhere(state, model)
    val inc = state.incident
    val text = inc?.let { state.note.text(it) }
    Screen(
        title = stringResource(R.string.key_note),
        onBack = onBack,
        actions = {
            BarText(stringResource(R.string.key_read_out), onReadOut)
            if (text != null) BarButton(Icons.Share, stringResource(R.string.key_share)) { shareText(context, context.getString(R.string.key_title), text) }
        },
    ) { modifier ->
        val empty = stringResource(R.string.key_note_empty)
        LazyColumnMMD(modifier = modifier) {
            val lines = text?.lines() ?: listOf(empty)
            lines.forEachIndexed { i, l ->
                item(key = i) {
                    SelectionContainer(Modifier.textActions()) {
                        TextMMD(text = l, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                    }
                }
            }
            if (inc != null) item(key = "start-over") {
                var armed by remember { mutableStateOf(false) }
                LaunchedEffect(armed) { if (armed) { delay(4_000); armed = false } }
                Column(Modifier.padding(top = 20.dp, bottom = 16.dp)) {
                    HorizontalDividerMMD()
                    TextMMD(
                        text = stringResource(if (armed) R.string.key_start_over_armed else R.string.key_start_over),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (armed) { state.startOver(); onCleared() } else armed = true
                        }.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                    HorizontalDividerMMD()
                }
            }
        }
    }
}

/** The verbal report: the note in the order a call-taker asks for it, large enough to read out. */
@Composable
fun ReadOutScreen(state: KeyState, model: CompassModel, onBack: () -> Unit) {
    val context = LocalContext.current
    RecordWhere(state, model)
    val inc = state.incident
    val text = inc?.let { state.note.readOut(it) } ?: stringResource(R.string.key_note_empty)
    Screen(
        title = stringResource(R.string.key_read_out),
        onBack = onBack,
        actions = { BarButton(Icons.Share, stringResource(R.string.key_share)) { shareText(context, context.getString(R.string.key_title), text) } },
    ) { modifier ->
        LazyColumnMMD(modifier = modifier) {
            text.lines().forEachIndexed { i, l ->
                item(key = i) {
                    SelectionContainer(Modifier.textActions()) {
                        TextMMD(text = l, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/** The open note on "Calling for help", under the position: the verbal report, in a box. */
@Composable
fun ReadOutBox(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)
            .border(BorderStroke(1.dp, Color.Black), RoundedCornerShape(12.dp)).padding(14.dp),
    ) {
        TextMMD(text = stringResource(R.string.key_read_out), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        SelectionContainer(Modifier.textActions()) { TextMMD(text = text, style = MaterialTheme.typography.bodyMedium) }
    }
}

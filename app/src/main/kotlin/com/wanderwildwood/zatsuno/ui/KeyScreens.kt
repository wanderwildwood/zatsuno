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
import androidx.compose.foundation.layout.heightIn
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
import com.wanderwildwood.zatsuno.key.Patients
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

    /** Every patient open, the oldest first. [incident] is the one showing. */
    var patients by mutableStateOf(IncidentStore.patients(context))
        private set

    /** What "Undo" would put back ([com.wanderwildwood.zatsuno.key.Assessments.KEPT] or OPEN), while it still can. */
    var undoable by mutableStateOf(IncidentStore.undoable(context))
        private set

    /** The patient as the lists and the reminder name them. */
    fun label(i: Incident): String = Patients.label(i, patients, words)

    private fun reload() {
        incident = IncidentStore.load(context)
        patients = IncidentStore.patients(context)
        kept = IncidentStore.kept(context)
        undoable = IncidentStore.undoable(context)
    }

    /** Shows patient [id]; null is a new one, begun by the first answer. */
    fun show(id: String?) {
        IncidentStore.show(context, id)
        reload()
    }

    /** Clears this patient and shows a new one: the answers, the note and the reminder for it go (Undo puts them back). */
    fun reset() {
        val id = incident?.id ?: return
        IncidentStore.reset(context, id)
        reload()
    }

    /** Puts back what was last deleted, discarded or cleared. */
    fun undo() {
        IncidentStore.undo(context)
        reload()
    }

    val note: Note get() = Note(key, words, titles, twentyFourHour = android.text.format.DateFormat.is24HourFormat(context))
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
        patients = IncidentStore.patients(context)
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
    fun check(avpu: String?, pulse: Int?, breaths: Int?, skin: List<String>, rhythm: String?, quality: String?, pupils: String?) {
        change { Incident.Check(it, avpu, pulse, breaths, skin, rhythm, quality, pupils) }
        if (avpu != null && answers.single("avpu") != avpu) answer("avpu", avpu)
        Recheck.seen(context)
    }

    /** Where the phone is, kept once and again when it is five minutes stale. */
    fun where(lat: Double, lon: Double, accuracy: Float?, at: Long) {
        val inc = incident ?: return
        val last = inc.where()
        if (last == null || at - last.at > 5 * 60_000L) change { Incident.Where(at, lat, lon, accuracy) }
    }

    /** The note of the last assessment ended with "Keep the note", for the home screen. */
    var kept by mutableStateOf(IncidentStore.kept(context))
        private set

    /** Ends the assessment: keep the note as the last one, or discard it. All that belongs to it is cleared, reminder included. */
    fun end(keep: Boolean) {
        val id = incident?.id ?: return
        IncidentStore.end(context, id, keep)
        reload()
    }

    fun deleteKept() {
        IncidentStore.deleteKept(context)
        reload()
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

/** The pupils: equal and reacting (PERRL), or not. "Not checked" is the third choice, and saves nothing. */
private val PUPILS = listOf(Incident.EQUAL, Incident.UNEQUAL)
private const val PUPILS_NOT_CHECKED = "not_checked"

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
    onEnd: () -> Unit,
    onPatients: () -> Unit,
    onAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(stage) { list.scrollToItem(0) }
    val context = LocalContext.current
    val draft = remember(state.incident?.id) { VitalsDraft() }
    LaunchedEffect(draft.counting) {
        if (draft.counting) { delay(15_000); Recheck.buzz(context); draft.counting = false }
    }
    if (stage == Stage.CALL) RecordWhere(state, model)
    Screen(
        title = state.incident?.takeIf { state.patients.size > 1 }?.let { state.label(it) } ?: stringResource(R.string.key_title_bar),
        onBack = onBack,
        actions = {
            if (state.incident != null) BarText(stringResource(R.string.key_end), onEnd)
            BarText(stringResource(R.string.key_note), onNote)
        },
    ) { modifier ->
        Column(modifier) {
            StageStrip(stage, onStage)
            HorizontalDividerMMD()
            val ranking = state.ranking
            val alarms = alarms(ranking, state.answers)
            // A new life threat goes to the top of the list, and the list goes to it at once.
            LaunchedEffect(alarms.map { it.first }) { if (alarms.isNotEmpty()) list.scrollToItem(0) }
            PagedColumn(Modifier.fillMaxWidth().weight(1f), state = list) {
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
                    Stage.SCENE -> scene(state, onOpenPage, onPatients, onAnother)
                    Stage.PRIMARY -> {
                        questions(state, Stage.PRIMARY)
                        item(key = "expose") { Reminder(stringResource(R.string.key_expose)) }
                    }
                    Stage.CALL -> call(state, ranking, onOpenPage, onReadOut)
                    Stage.SECONDARY -> secondary(state, ranking, draft)
                    Stage.TREAT -> treat(state, ranking, onOpenPage, onStage)
                    Stage.MONITOR -> monitor(state, draft, onStage, onEnd, onPatients, onAnother)
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
                    .heightIn(min = 48.dp)
                    .clickable { onStage(s) }
                    .then(if (s == current) Modifier.border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(6.dp)) else Modifier)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Who the note is about: the patients already open (one tap to switch), the name, and a way to
 * begin another. The name can come from the emergency card when the patient is the owner.
 */
@Composable
private fun PatientBlock(state: KeyState, onPatients: () -> Unit, onAnother: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        if (state.patients.size > 1) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                PageRow(stringResource(R.string.key_patients, state.patients.size), null, onClick = onPatients)
            }
        }
        TextLine(state, Fields.NAME, null)
        val card = remember { CardStore.load(context) }
        if (card.name.isNotBlank() && state.text(Fields.NAME).isBlank()) {
            WideButton(stringResource(R.string.key_name_card), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                state.write(Fields.NAME, card.name.trim())
            }
        }
        if (state.incident != null) {
            WideButton(stringResource(R.string.key_another), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), onAnother)
        }
    }
}

/** A button that asks twice: the first tap arms it for 6 seconds and says so. */
@Composable
fun ArmedButton(label: Int, armedLabel: Int, modifier: Modifier = Modifier, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(6_000); armed = false } }
    WideButton(stringResource(if (armed) armedLabel else label), modifier) { if (armed) { armed = false; onConfirm() } else armed = true }
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

private fun LazyListScope.scene(state: KeyState, onOpenPage: (String) -> Unit, onPatients: () -> Unit, onAnother: () -> Unit) {
    val safe = state.key.question("safe")!!
    item(key = "patient") { PatientBlock(state, onPatients, onAnother) }
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
    // Filled from the card, or emptied when the assessment ends: the field follows the note.
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
    var pupils by mutableStateOf<String?>(null)
    var counting by mutableStateOf(false)

    fun clear() { avpu = null; pulse = ""; rhythm = null; breaths = ""; quality = null; skin = emptyList(); pupils = null }
}

/**
 * The vital signs: one column per set, the newest on the right, and the next set entered under
 * "now": LOR, HR with its rhythm, RR with its quality, SCTM and the pupils. HR and RR are counted for 15
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
    item(key = "v:pupils") {
        TextMMD(text = stringResource(R.string.key_pupils), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp))
    }
    choiceRows("v:pupils", PUPILS.map { it to w.answer("pupils", it) } + (PUPILS_NOT_CHECKED to w.get("key_pupils_not_checked")),
        { setOf(draft.pupils ?: PUPILS_NOT_CHECKED) }) {
        draft.pupils = if (it == PUPILS_NOT_CHECKED) null else it
    }
    item(key = "v:buttons") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            WideButton(stringResource(if (draft.counting) R.string.key_counting else R.string.key_count), Modifier.weight(1f)) { if (!draft.counting) draft.counting = true }
            WideButton(stringResource(R.string.key_save_check), Modifier.weight(1f)) {
                val p = draft.pulse.toIntOrNull()?.times(4)
                val b = draft.breaths.toIntOrNull()?.times(4)
                if (draft.avpu != null || p != null || b != null || draft.skin.isNotEmpty() || draft.rhythm != null || draft.quality != null || draft.pupils != null) {
                    val order = SKIN.flatten()
                    state.check(draft.avpu, p, b, draft.skin.sortedBy(order::indexOf), draft.rhythm, draft.quality, draft.pupils)
                    draft.clear()
                }
            }
        }
    }
}

/** Monitoring: the vital signs again, when the next reassessment is due, and the way back to the primary assessment. */
private fun LazyListScope.monitor(
    state: KeyState, draft: VitalsDraft, onStage: (Stage) -> Unit, onEnd: () -> Unit, onPatients: () -> Unit, onAnother: () -> Unit,
) {
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
    if (state.incident != null) item(key = "end") {
        WideButton(stringResource(R.string.key_end_assessment), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onEnd)
    }
    if (state.incident != null) item(key = "another") {
        WideButton(stringResource(R.string.key_another), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onAnother)
    }
    if (state.patients.size > 1) item(key = "patients") {
        WideButton(stringResource(R.string.key_patients, state.patients.size), Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onPatients)
    }
    if (state.incident != null) item(key = "reset") {
        ArmedButton(R.string.key_reset, R.string.key_reset_armed, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) { state.reset(); onStage(Stage.SCENE) }
    }
}

/**
 * "End this assessment?": keep the note (it waits on the home screen as the last note, to copy
 * or share) or discard it (an armed row: tapped twice), or cancel. Either end clears the open
 * assessment and every reminder, so the next one starts fresh.
 */
@Composable
fun EndAssessmentDialog(state: KeyState, onEnded: () -> Unit, onDismiss: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(6_000); armed = false } }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.key_end_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        TextMMD(text = stringResource(R.string.key_end_text), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        WideButton(stringResource(R.string.key_end_keep)) { state.end(keep = true); onEnded() }
        Spacer(Modifier.height(8.dp))
        WideButton(stringResource(if (armed) R.string.key_end_discard_armed else R.string.key_end_discard)) {
            if (armed) { state.end(keep = false); onEnded() } else armed = true
        }
        Spacer(Modifier.height(8.dp))
        WideButton(stringResource(R.string.key_end_cancel), onClick = onDismiss)
    }
}

/** The note of the last assessment, ended with "Keep the note": read, copy or share; an armed row deletes it. */
@Composable
fun LastNoteScreen(state: KeyState, onQr: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val inc = state.kept
    val text = inc?.let { state.note.text(it) }
    if (inc == null) { LaunchedEffect(Unit) { onBack() }; return }
    Screen(
        title = stringResource(R.string.last_title),
        onBack = onBack,
        actions = { BarButton(Icons.Share, stringResource(R.string.key_share)) { shareText(context, context.getString(R.string.key_title), text!!) } },
    ) { modifier ->
        PagedColumn(modifier) {
            text!!.lines().forEachIndexed { i, l ->
                item(key = i) {
                    SelectionContainer(Modifier.textActions()) {
                        TextMMD(text = l, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                    }
                }
            }
            item(key = "tools") { NoteTools(text!!, onQr) }
            item(key = "delete") {
                var armed by remember { mutableStateOf(false) }
                LaunchedEffect(armed) { if (armed) { delay(6_000); armed = false } }
                Column(Modifier.padding(top = 20.dp, bottom = 16.dp)) {
                    HorizontalDividerMMD()
                    TextMMD(
                        text = stringResource(if (armed) R.string.last_delete_armed else R.string.last_delete),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { if (armed) { state.deleteKept(); onBack() } else armed = true }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                    HorizontalDividerMMD()
                }
            }
        }
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
 * The SOAP note, top to bottom, with Send and "Verbal report" in the bar. At its foot a row,
 * "End assessment", which asks whether to keep the note or discard it.
 */
@Composable
fun NoteScreen(state: KeyState, model: CompassModel, onReadOut: () -> Unit, onQr: () -> Unit, onBack: () -> Unit, onEnd: () -> Unit) {
    val context = LocalContext.current
    RecordWhere(state, model)
    val inc = state.incident
    val text = inc?.let { state.note.text(it) }
    Screen(
        title = stringResource(R.string.key_note),
        onBack = onBack,
        actions = {
            BarText(stringResource(R.string.key_read_out_bar), onReadOut)
            if (text != null) BarButton(Icons.Share, stringResource(R.string.key_share)) { shareText(context, context.getString(R.string.key_title), text) }
        },
    ) { modifier ->
        val empty = stringResource(R.string.key_note_empty)
        PagedColumn(modifier) {
            val lines = text?.lines() ?: listOf(empty)
            lines.forEachIndexed { i, l ->
                item(key = i) {
                    SelectionContainer(Modifier.textActions()) {
                        TextMMD(text = l, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                    }
                }
            }
            if (text != null) item(key = "tools") { NoteTools(text, onQr) }
            if (inc != null) item(key = "end") {
                Column(Modifier.padding(top = 20.dp, bottom = 16.dp)) {
                    HorizontalDividerMMD()
                    TextMMD(
                        text = stringResource(R.string.key_end_assessment),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onEnd).padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                    HorizontalDividerMMD()
                }
            }
        }
    }
}

/** A plain row under a note: tap to do it. */
@Composable
private fun ToolRow(label: String, onClick: () -> Unit) {
    TextMMD(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

/**
 * Under a note: show it as QR code(s) for a crew's phone to scan, or save it as a file. The
 * file is written where the person chooses in the phone's own file picker; the app writes to no
 * other place.
 */
@Composable
private fun NoteTools(text: String, onQr: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<Int?>(null) }
    val current by androidx.compose.runtime.rememberUpdatedState(text)
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(current.toByteArray()) } }.isSuccess
            status = if (ok) R.string.note_saved else R.string.note_save_failed
        }
    }
    Column(Modifier.padding(top = 12.dp)) {
        ToolRow(stringResource(R.string.note_qr), onQr)
        ToolRow(stringResource(R.string.note_save)) { status = null; save.launch(context.getString(R.string.note_file_name)) }
        status?.let { TextMMD(text = stringResource(it), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp)) }
    }
}

/**
 * The note as QR codes. A short note is one code; a long one is cut between lines into
 * numbered codes ("(1/3)") that are scanned in turn, because one code holding it all would be
 * too fine to read off a small panel.
 */
@Composable
fun QrScreen(title: String, text: String, onBack: () -> Unit) {
    val pieces = remember(text) { Patients.codes(text) }
    var n by rememberSaveable { mutableStateOf(0) }
    if (n > pieces.lastIndex) n = pieces.lastIndex
    Screen(title = title, onBack = onBack) { modifier ->
        Column(modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pieces.size > 1) TextMMD(text = stringResource(R.string.qr_n, n + 1, pieces.size), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
            QrCode(pieces[n], Modifier.fillMaxWidth().weight(1f))
            TextMMD(text = stringResource(if (pieces.size > 1) R.string.qr_hint_many else R.string.qr_hint), style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
            if (pieces.size > 1) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (n > 0) WideButton(stringResource(R.string.qr_prev), Modifier.weight(1f)) { n-- }
                if (n < pieces.lastIndex) WideButton(stringResource(R.string.qr_next), Modifier.weight(1f)) { n++ }
            }
        }
    }
}

/** The patients open, to pick one or begin another. */
@Composable
fun PatientsScreen(state: KeyState, onPick: (String) -> Unit, onAnother: () -> Unit, onBack: () -> Unit) {
    val shown = state.incident?.id
    Screen(title = stringResource(R.string.patients_title), onBack = onBack) { modifier ->
        PagedColumn(modifier) {
            for (p in state.patients) {
                item(key = p.id) {
                    Column(Modifier.fillMaxWidth().clickable { onPick(p.id) }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 12.dp)) {
                        TextMMD(text = state.label(p), style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (p.id == shown) FontWeight.Bold else FontWeight.Normal)
                        TextMMD(text = stringResource(R.string.patients_row, state.note.time(p.started), state.note.time(p.nextCheck(state.key))),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item(key = "another") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    WideButton(stringResource(R.string.key_another), Modifier.fillMaxWidth(), onAnother)
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
        PagedColumn(modifier) {
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

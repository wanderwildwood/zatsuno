package com.wanderwildwood.zatsuno.key

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.wanderwildwood.zatsuno.MainActivity
import com.wanderwildwood.zatsuno.R
import java.io.File

/** The key, read once from `assets/key/key.txt`. */
object Keys {
    @Volatile private var key: Key? = null

    fun get(context: Context): Key = key ?: context.assets.open("key/key.txt").bufferedReader().use { Key.parse(it.readText()) }.also { key = it }

    /** strings.xml by name. Every name it is asked for is kept through shrinking by res/raw/keep_key.xml. */
    fun words(context: Context): Words {
        val res = context.resources
        return Words { name, args ->
            @SuppressLint("DiscouragedApi")
            val id = res.getIdentifier(name, "string", context.packageName)
            if (id == 0) name else res.getString(id, *args)
        }
    }
}

/**
 * What the assessment state machine needs from the phone, so the rules can be tested without
 * one: a file for each open patient, the kept note and the undo slot, which patient is showing,
 * and the reminder (an alarm and a notification).
 */
interface AssessmentPlatform {
    /** The ids of the patients with a file, in no order. */
    fun openIds(): List<String>
    fun readOpen(id: String): String?
    fun writeOpen(id: String, text: String)
    fun deleteOpen(id: String)
    fun readKept(): String?
    fun writeKept(text: String)
    fun deleteKept()
    /** The patient showing: an id, [Assessments.BLANK] for a new one not yet begun, or null for none chosen. */
    fun readActive(): String?
    fun writeActive(id: String?)
    /** The one thing that can still be put back: a first line "K" or "O", a tab and the time, then the text. */
    fun readTrash(): String?
    fun writeTrash(text: String)
    fun deleteTrash()
    /** Sets the one reminder, for whichever open patient is due first (or takes it away if none is). */
    fun remind(patients: List<Incident>)
    /** Cancels the alarm and any reminder notification showing. */
    fun cancelReminder()
    /** Takes the reminder notification down (one patient ended; the others' alarm stays). */
    fun dismissNotification()
}

/**
 * The assessments' life. Any number of patients can be open at once, each in a file of its
 * own, saved after every change. One is showing. A patient is begun by a first answer and
 * ends by "End assessment" only: the note is kept as the last one, or discarded. Ending always
 * clears that patient's part of the reminder. Whatever is deleted or discarded (and a reset)
 * waits in one undo slot for [UNDO_MS].
 */
class Assessments(private val p: AssessmentPlatform, private val now: () -> Long = System::currentTimeMillis) {
    /** Every open patient, the oldest first. */
    fun patients(): List<Incident> =
        p.openIds().mapNotNull { id -> runCatching { p.readOpen(id)?.let(Incident::decode) }.getOrNull() }.sortedBy { it.started }

    /** The patient showing: the one chosen, else the newest. Null for a new one not yet begun. */
    fun open(): Incident? {
        val all = patients()
        return when (val a = p.readActive()) {
            BLANK -> null
            null -> all.lastOrNull()
            else -> all.firstOrNull { it.id == a } ?: all.lastOrNull()
        }
    }

    /** Shows [id]; null shows a new patient, begun by the first answer. */
    fun show(id: String?) = p.writeActive(id ?: BLANK)

    fun kept(): Incident? = runCatching { p.readKept()?.let(Incident::decode) }.getOrNull()

    fun save(incident: Incident) {
        p.writeOpen(incident.id, incident.encode())
        p.writeActive(incident.id)
        p.remind(patients())
    }

    /** Ends patient [id]: [keep] the note as the last one, or throw it away (it can be put back). Either way the reminder is worked out again without it. */
    fun end(id: String, keep: Boolean) {
        val text = p.readOpen(id)
        if (keep && text != null && Incident.decode(text) != null) p.writeKept(text)
        else if (text != null) trash(OPEN, text)
        p.deleteOpen(id)
        if (p.readActive() == id) p.writeActive(null)
        remindAgain()
    }

    /** Empties patient [id] for a fresh start: nothing is left of it but the undo. */
    fun reset(id: String) {
        val text = p.readOpen(id)
        if (text != null) trash(OPEN, text)
        p.deleteOpen(id)
        p.writeActive(BLANK)
        remindAgain()
    }

    fun deleteKept() {
        p.readKept()?.let { trash(KEPT, it) }
        p.deleteKept()
    }

    /** What [undo] would put back: [KEPT] or [OPEN], or null when nothing is waiting or it is too old. */
    fun undoable(): String? {
        val head = p.readTrash()?.substringBefore('\n') ?: return null
        val kind = head.substringBefore('\t')
        val at = head.substringAfter('\t', "").toLongOrNull() ?: return null
        if (now() - at > UNDO_MS || now() < at) { p.deleteTrash(); return null }
        return kind.takeIf { it == KEPT || it == OPEN }
    }

    /** Puts back what was last deleted, discarded or reset. Returns what it was, or null. */
    fun undo(): String? {
        val kind = undoable() ?: return null
        val body = p.readTrash()!!.substringAfter('\n', "")
        p.deleteTrash()
        when (kind) {
            KEPT -> p.writeKept(body)
            else -> {
                val inc = Incident.decode(body) ?: return null
                save(inc)
            }
        }
        return kind
    }

    private fun trash(kind: String, text: String) = p.writeTrash("$kind\t${now()}\n$text")

    private fun remindAgain() {
        val all = patients()
        if (all.isEmpty()) p.cancelReminder() else { p.dismissNotification(); p.remind(all) }
    }

    /**
     * Run whenever the app's process starts: files that cannot be read go, an old undo goes, no
     * patient means no alarm and no notification (whatever left them behind), and each open one
     * counts in the reminder again.
     */
    fun sweep() {
        for (id in p.openIds()) if (runCatching { p.readOpen(id)?.let(Incident::decode) }.getOrNull() == null) p.deleteOpen(id)
        undoable()
        remindAgain()
    }

    companion object {
        const val BLANK = ""
        const val KEPT = "K"
        const val OPEN = "O"
        /** How long a deleted note can be put back. */
        const val UNDO_MS = 10 * 60_000L
    }
}

/** The phone's side of [Assessments]: files in the app's own storage, which is never backed up and never sent. */
class AndroidAssessmentPlatform(private val context: Context) : AssessmentPlatform {
    private val dir get() = context.noBackupFilesDir
    private fun open(id: String) = File(dir, "patient-$id.txt")
    private fun kept() = File(dir, "last-note.txt")
    private fun active() = File(dir, "showing.txt")
    private fun trash() = File(dir, "undo.txt")

    private fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".new")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    private fun read(f: File) = runCatching { f.takeIf { it.isFile }?.readText() }.getOrNull()

    override fun openIds(): List<String> {
        // Before several patients there was one file, incident.txt: it becomes the first patient.
        File(dir, "incident.txt").takeIf { it.isFile }?.let { old ->
            val started = runCatching { Incident.decode(old.readText())?.id }.getOrNull()
            if (started != null) write(open(started), old.readText())
            old.delete()
        }
        return dir.listFiles { f -> f.name.startsWith("patient-") && f.name.endsWith(".txt") }
            ?.map { it.name.removePrefix("patient-").removeSuffix(".txt") }.orEmpty()
    }

    override fun readOpen(id: String) = read(open(id))
    override fun writeOpen(id: String, text: String) = write(open(id), text)
    override fun deleteOpen(id: String) { open(id).delete() }
    override fun readKept() = read(kept())
    override fun writeKept(text: String) = write(kept(), text)
    override fun deleteKept() { kept().delete() }
    override fun readActive() = read(active())
    override fun writeActive(id: String?) { if (id == null) active().delete() else write(active(), id) }
    override fun readTrash() = read(trash())
    override fun writeTrash(text: String) = write(trash(), text)
    override fun deleteTrash() { trash().delete() }
    override fun remind(patients: List<Incident>) = Recheck.schedule(context, patients)
    override fun cancelReminder() = Recheck.cancel(context)
    override fun dismissNotification() = Recheck.seen(context)
}

/** The app's one [Assessments], on the phone. */
object IncidentStore {
    fun of(context: Context) = Assessments(AndroidAssessmentPlatform(context.applicationContext))
    fun load(context: Context): Incident? = of(context).open()
    fun patients(context: Context): List<Incident> = of(context).patients()
    fun show(context: Context, id: String?) = of(context).show(id)
    fun save(context: Context, incident: Incident) = of(context).save(incident)
    fun end(context: Context, id: String, keep: Boolean) = of(context).end(id, keep)
    fun reset(context: Context, id: String) = of(context).reset(id)
    fun kept(context: Context): Incident? = of(context).kept()
    fun deleteKept(context: Context) = of(context).deleteKept()
    fun undoable(context: Context): String? = of(context).undoable()
    fun undo(context: Context): String? = of(context).undo()
    fun sweep(context: Context) = of(context).sweep()
}

/**
 * When the reminder next goes off, if at all. Once per due time: a reminder already given for
 * this due time is not given again every time the screen is tapped; and an assessment nobody
 * has touched for [STALE_MS] is left alone rather than nagged about for ever.
 */
object ReminderPlan {
    const val STALE_MS = 12 * 60 * 60_000L

    /** [due] is Incident.nextCheck; [firedFor] the due time last reminded about (0 for none). Null: no alarm. */
    fun alarmAt(due: Long, lastActivity: Long, now: Long, firedFor: Long): Long? = when {
        now - lastActivity > STALE_MS -> null
        due > now -> due
        firedFor >= due -> null
        else -> now
    }

    /** Of several open patients, the one whose reminder comes first, and when. */
    fun next(key: Key, patients: List<Incident>, now: Long, firedFor: Long): Pair<Incident, Long>? =
        patients.mapNotNull { i -> alarmAt(i.nextCheck(key), i.last, now, firedFor)?.let { i to it } }.minByOrNull { it.second }
}

/**
 * The reassessment reminder: one alarm, set for the next reassessment whenever the incident
 * changes. When it goes off the phone buzzes and a notification says a reassessment is due;
 * tapping it opens the key at Monitoring. The notification can be swiped away, is never
 * ongoing, takes itself away after an hour, and has an "End assessment" action.
 */
object Recheck {
    private const val CHANNEL = "recheck2"
    /** The first channel had no sound attributes of its own; its settings cannot be changed once made, so it is replaced. */
    private const val OLD_CHANNEL = "recheck"
    private const val NOTIFICATION = 1
    private const val PREFS = "recheck"
    private const val FIRED_FOR = "fired_for"
    const val EXTRA_MONITOR = "com.wanderwildwood.zatsuno.extra.MONITOR"
    /** Only Field Kit's own notification sends it: opens the question "End assessment?". */
    const val EXTRA_END = "com.wanderwildwood.zatsuno.extra.END"
    /** Which patient the reminder is about: their id (the time they were begun). */
    const val EXTRA_PATIENT = "com.wanderwildwood.zatsuno.extra.PATIENT"

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, RecheckReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun firedFor(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(FIRED_FOR, 0L)

    /** One alarm, for whichever open patient is due first. */
    fun schedule(context: Context, patients: List<Incident>, now: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = ReminderPlan.next(Keys.get(context), patients, now, firedFor(context))?.second
        if (at == null) { alarms.cancel(pending(context)); return }
        // Exact when the phone allows it; otherwise as near as Android will let it be.
        if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(FIRED_FOR).apply()
    }

    /** Dismisses the reminder once the reassessment is under way. */
    fun seen(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
    }

    /** The patient whose reassessment is due first, or null when nobody is open. */
    fun dueFirst(context: Context, patients: List<Incident>): Incident? {
        val key = Keys.get(context)
        return patients.minByOrNull { it.nextCheck(key) }
    }

    fun notify(context: Context, incident: Incident, several: Boolean = false, label: String = "") {
        val due = incident.nextCheck(Keys.get(context))
        // Once per due time. Starting the app's process to deliver this alarm sets an alarm again
        // (FieldKitApplication), which would otherwise buzz a second time a moment after the first.
        if (firedFor(context) >= due) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(FIRED_FOR, due).apply()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.recheck_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                // The buzz is made by [buzz], as an alarm, so a phone in the pocket and a background app both feel it once.
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        // With the screen off or locked the reminder comes up as a whole screen, which stays
        // until it is answered; with the phone in use it is a notification at the top.
        val screen = PendingIntent.getActivity(
            context, 2,
            Intent(context, ReminderActivity::class.java).putExtra(EXTRA_PATIENT, incident.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_MONITOR, true).putExtra(EXTRA_PATIENT, incident.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val end = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_END, true).putExtra(EXTRA_PATIENT, incident.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.recheck_title))
            .setContentText(if (several) context.getString(R.string.recheck_text_named, label) else context.getString(R.string.recheck_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(android.app.Notification.CATEGORY_ALARM)
            .setFullScreenIntent(screen, true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(60 * 60_000L)
            .addAction(android.app.Notification.Action.Builder(null, context.getString(R.string.recheck_end), end).build())
            .build()
        manager.notify(NOTIFICATION, n)
        buzz(context, 600)
    }

    /** One buzz: the end of the 15-second count, or a reassessment due. */
    fun buzz(context: Context, millis: Long = 400) {
        val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        // Said to be an alarm: Android drops a background app's vibration that says nothing
        // about what it is for, and the reminder comes from the background.
        @Suppress("DEPRECATION")
        vibrator.vibrate(
            VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE),
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
    }
}

/** After a restart or an update the process starts, which sets an open assessment's alarm again (FieldKitApplication); this is what starts it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { IncidentStore.sweep(context) }
    }
}

class RecheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only while an assessment is still open: ending the last one cancels the alarm, this makes sure.
        val all = IncidentStore.patients(context)
        val due = Recheck.dueFirst(context, all)
        if (due == null) { Recheck.cancel(context); return }
        Recheck.notify(context, due, all.size > 1, Patients.label(due, all, context))
        // The others are still to come.
        Recheck.schedule(context, all)
    }
}

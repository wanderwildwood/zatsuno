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
 * one: the two files, and the reminder (an alarm and a notification).
 */
interface AssessmentPlatform {
    fun readOpen(): String?
    fun writeOpen(text: String)
    fun deleteOpen()
    fun readKept(): String?
    fun writeKept(text: String)
    fun deleteKept()
    /** Sets the reminder for an open assessment (or takes it away if none is due). */
    fun remind(incident: Incident)
    /** Cancels the alarm and any reminder notification showing. */
    fun cancelReminder()
}

/**
 * The assessment's life: nothing -> open (a first answer starts it) -> ended, by "End
 * assessment" only. Ending always cancels the alarm and the notification. The open assessment
 * is saved after every change; the one that was ended with "keep" waits as the last note
 * until it is deleted or another is kept over it.
 */
class Assessments(private val p: AssessmentPlatform) {
    fun open(): Incident? = runCatching { p.readOpen()?.let(Incident::decode) }.getOrNull()

    fun kept(): Incident? = runCatching { p.readKept()?.let(Incident::decode) }.getOrNull()

    fun save(incident: Incident) {
        p.writeOpen(incident.encode())
        p.remind(incident)
    }

    /** Ends the open assessment: [keep] it as the last note, or throw it away. Either way the next one starts fresh. */
    fun end(keep: Boolean) {
        val text = p.readOpen()
        if (keep && text != null && Incident.decode(text) != null) p.writeKept(text)
        p.deleteOpen()
        p.cancelReminder()
    }

    fun deleteKept() = p.deleteKept()

    /**
     * Run whenever the app's process starts: no open assessment, no alarm and no notification
     * (whatever left them behind); an open one has its reminder set again.
     */
    fun sweep() {
        val o = open()
        if (o == null) { p.deleteOpen(); p.cancelReminder() } else p.remind(o)
    }
}

/** The phone's side of [Assessments]: one file each in the app's own storage, which is never backed up and never sent. */
class AndroidAssessmentPlatform(private val context: Context) : AssessmentPlatform {
    private fun open() = File(context.noBackupFilesDir, "incident.txt")
    private fun kept() = File(context.noBackupFilesDir, "last-note.txt")

    private fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".new")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    override fun readOpen() = runCatching { open().takeIf { it.isFile }?.readText() }.getOrNull()
    override fun writeOpen(text: String) = write(open(), text)
    override fun deleteOpen() { open().delete() }
    override fun readKept() = runCatching { kept().takeIf { it.isFile }?.readText() }.getOrNull()
    override fun writeKept(text: String) = write(kept(), text)
    override fun deleteKept() { kept().delete() }
    override fun remind(incident: Incident) = Recheck.schedule(context, incident)
    override fun cancelReminder() = Recheck.cancel(context)
}

/** The app's one [Assessments], on the phone. */
object IncidentStore {
    fun of(context: Context) = Assessments(AndroidAssessmentPlatform(context.applicationContext))
    fun load(context: Context): Incident? = of(context).open()
    fun save(context: Context, incident: Incident) = of(context).save(incident)
    fun end(context: Context, keep: Boolean) = of(context).end(keep)
    fun kept(context: Context): Incident? = of(context).kept()
    fun deleteKept(context: Context) = of(context).deleteKept()
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

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, RecheckReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun firedFor(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(FIRED_FOR, 0L)

    fun schedule(context: Context, incident: Incident, now: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = ReminderPlan.alarmAt(incident.nextCheck(Keys.get(context)), incident.last, now, firedFor(context))
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

    fun notify(context: Context, incident: Incident) {
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
            Intent(context, ReminderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_MONITOR, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val end = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_END, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.recheck_title))
            .setContentText(context.getString(R.string.recheck_text))
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
        // Only while an assessment is still open: ending it cancels the alarm, this makes sure.
        val open = IncidentStore.load(context)
        if (open != null) Recheck.notify(context, open) else Recheck.cancel(context)
    }
}

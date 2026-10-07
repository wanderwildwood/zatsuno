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
 * Where the open incident is kept: one file in the app's own storage, which is never backed up
 * and never sent. Written after every change; removed only by "Start over".
 */
object IncidentStore {
    private fun file(context: Context) = File(context.noBackupFilesDir, "incident.txt")

    fun load(context: Context): Incident? = runCatching { file(context).takeIf { it.isFile }?.readText()?.let(Incident::decode) }.getOrNull()

    fun save(context: Context, incident: Incident) {
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".new")
        tmp.writeText(incident.encode())
        tmp.renameTo(f)
        Recheck.schedule(context, incident)
    }

    fun clear(context: Context) {
        file(context).delete()
        Recheck.cancel(context)
    }
}

/**
 * The recheck reminder: one alarm, set for the next check whenever the incident changes. When
 * it goes off the phone buzzes and a notification says a recheck is due; tapping it opens the
 * key at Watch.
 */
object Recheck {
    private const val CHANNEL = "recheck"
    private const val NOTIFICATION = 1
    const val EXTRA_WATCH = "com.wanderwildwood.zatsuno.extra.WATCH"

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, RecheckReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context, incident: Incident) {
        val at = incident.nextCheck(Keys.get(context))
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Exact when the phone allows it; otherwise as near as Android will let it be.
        if (alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
    }

    /** Dismisses the reminder once the recheck is under way. */
    fun seen(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
    }

    fun notify(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.recheck_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            },
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_WATCH, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.recheck_title))
            .setContentText(context.getString(R.string.recheck_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION, n)
        buzz(context, 600)
    }

    /** One buzz: the end of the 15-second count, or a recheck due. */
    fun buzz(context: Context, millis: Long = 400) {
        val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

class RecheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only while an incident is still open: "Start over" cancels the alarm, this makes sure.
        if (IncidentStore.load(context) != null) Recheck.notify(context)
    }
}

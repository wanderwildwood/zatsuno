package com.wanderwildwood.zatsuno.key

import com.wanderwildwood.zatsuno.ui.DarkSelection
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.zatsuno.MainActivity
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.ui.WideButton
import com.wanderwildwood.zatsuno.ui.houseTypography
import com.wanderwildwood.zatsuno.ui.monochrome
import java.util.Date
import com.wanderwildwood.zatsuno.ui.ArmedScope

/**
 * The reassessment reminder as a whole screen, shown over the lock screen when it goes off
 * with the screen dark. It stays until the person answers: open the assessment, end it, or
 * leave it for later. What it says comes from the assessment's own file, not from whether the
 * phone still shows the notification (some phones clear that as the screen lights), so it
 * never closes itself.
 */
class ReminderActivity : ComponentActivity() {
    /** The patient this screen is about; a second reminder while it is up names the next one. */
    private val patient = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        patient.value = intent.getStringExtra(Recheck.EXTRA_PATIENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        patient.value = intent.getStringExtra(Recheck.EXTRA_PATIENT)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) { DarkSelection {
                ArmedScope {
                    CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                            Reminder(
                                patientId = patient.value,
                                onOpen = { id -> go(Recheck.EXTRA_MONITOR, id) },
                                onEnd = { id -> go(Recheck.EXTRA_END, id) },
                                onClose = ::finish,
                            )
                        }
                    }
                }
            } }
        }
    }

    private fun go(extra: String, patient: String?) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(extra, true).putExtra(Recheck.EXTRA_PATIENT, patient).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun Reminder(patientId: String?, onOpen: (String?) -> Unit, onEnd: (String?) -> Unit, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val all = androidx.compose.runtime.remember(patientId) { IncidentStore.patients(context) }
    val incident = androidx.compose.runtime.remember(patientId) { all.firstOrNull { it.id == patientId } ?: Keys.get(context).let { key -> all.minByOrNull { it.nextCheck(key) } } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        if (incident == null) {
            TextMMD(text = stringResource(R.string.recheck_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            TextMMD(text = stringResource(R.string.reminder_none), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            WideButton(stringResource(R.string.reminder_close), Modifier.fillMaxWidth(), onClose)
            return@Column
        }
        TextMMD(text = stringResource(R.string.recheck_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        // Numbered, never named: this screen is made to stand over the lock screen, and the phone
        // can lock while it is up, so anyone near could read a name.
        val who = Patients.numbered(incident, all, context)
        TextMMD(
            text = if (all.size > 1) stringResource(R.string.recheck_text_named, who) else stringResource(R.string.recheck_text),
            style = MaterialTheme.typography.bodyLarge,
        )
        // Others overdue as well each have a notification of their own; this screen names them
        // too (numbered), so answering it for one patient never hides the rest.
        val others = androidx.compose.runtime.remember(patientId) {
            val key = Keys.get(context)
            val now = System.currentTimeMillis()
            all.filter { it.id != incident.id && it.nextCheck(key) <= now && now - it.last <= ReminderPlan.STALE_MS }
                .sortedBy { it.nextCheck(key) }
        }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TextMMD(
                text = stringResource(R.string.reminder_also_due, others.joinToString(", ") { Patients.numbered(it, all, context) }),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(10.dp))
        val due = DateFormat.getTimeFormat(context).format(Date(incident.nextCheck(Keys.get(context))))
        TextMMD(text = stringResource(R.string.key_next_check, due), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(24.dp))
        WideButton(stringResource(R.string.reminder_open), Modifier.fillMaxWidth()) { onOpen(incident.id) }
        Spacer(Modifier.height(10.dp))
        WideButton(stringResource(R.string.recheck_end), Modifier.fillMaxWidth()) { onEnd(incident.id) }
        Spacer(Modifier.height(10.dp))
        WideButton(stringResource(R.string.reminder_later), Modifier.fillMaxWidth(), onClose)
    }
}

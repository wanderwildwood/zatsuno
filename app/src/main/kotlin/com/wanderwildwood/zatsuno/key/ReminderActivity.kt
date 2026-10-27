package com.wanderwildwood.zatsuno.key

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
import com.wanderwildwood.zatsuno.ui.monochrome
import java.util.Date

/**
 * The reassessment reminder as a whole screen, shown over the lock screen when it goes off
 * with the screen dark. It stays until the person answers: open the assessment, end it, or
 * leave it for later. What it says comes from the assessment's own file, not from whether the
 * phone still shows the notification (some phones clear that as the screen lights), so it
 * never closes itself.
 */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                        Reminder(
                            onOpen = { go(Recheck.EXTRA_MONITOR) },
                            onEnd = { go(Recheck.EXTRA_END) },
                            onClose = ::finish,
                        )
                    }
                }
            }
        }
    }

    private fun go(extra: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(extra, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun Reminder(onOpen: () -> Unit, onEnd: () -> Unit, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val incident = androidx.compose.runtime.remember { IncidentStore.load(context) }
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
        TextMMD(text = stringResource(R.string.recheck_text), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        val due = DateFormat.getTimeFormat(context).format(Date(incident.nextCheck(Keys.get(context))))
        TextMMD(text = stringResource(R.string.key_next_check, due), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(24.dp))
        WideButton(stringResource(R.string.reminder_open), Modifier.fillMaxWidth(), onOpen)
        Spacer(Modifier.height(10.dp))
        WideButton(stringResource(R.string.recheck_end), Modifier.fillMaxWidth(), onEnd)
        Spacer(Modifier.height(10.dp))
        WideButton(stringResource(R.string.reminder_later), Modifier.fillMaxWidth(), onClose)
    }
}

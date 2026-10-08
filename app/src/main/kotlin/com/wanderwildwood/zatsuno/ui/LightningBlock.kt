package com.wanderwildwood.zatsuno.ui

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.signal.Lightning
import java.util.Date

/**
 * Flash to thunder, on the "Lightning" page. Flash at the lightning, Thunder when it is heard:
 * the seconds between, how far that strike was, and the line that matters under it. The last
 * few counts stay in a list with their times, so a storm coming closer shows as the numbers
 * falling. Nothing on the screen moves while it counts; the panel is e-ink.
 */
@Composable
fun LightningBlock() {
    val context = LocalContext.current
    // Elapsed time rather than the clock, so a clock set while counting does not count.
    var flashAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var counts by rememberSaveable(saver = countsSaver()) { mutableStateOf(emptyList()) }
    val format = DateFormat.getTimeFormat(context)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp)
            .border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        TextMMD(text = stringResource(R.string.lightning_how), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            WideButton(stringResource(R.string.lightning_flash), Modifier.weight(1f)) { flashAt = SystemClock.elapsedRealtime() }
            WideButton(stringResource(R.string.lightning_thunder), Modifier.weight(1f)) {
                val from = flashAt ?: return@WideButton
                val seconds = Lightning.elapsed(from, SystemClock.elapsedRealtime())
                counts = Lightning.record(counts, Lightning.Count(System.currentTimeMillis(), seconds))
                flashAt = null
            }
        }
        val last = counts.lastOrNull()
        if (flashAt != null) {
            Spacer(Modifier.height(10.dp))
            TextMMD(text = stringResource(R.string.lightning_counting), style = MaterialTheme.typography.bodyMedium)
        } else if (last != null) {
            Spacer(Modifier.height(10.dp))
            TextMMD(
                text = stringResource(R.string.lightning_result, Lightning.seconds(last.seconds),
                    Lightning.distance(Lightning.miles(last.seconds)), Lightning.distance(Lightning.km(last.seconds))),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
        }
        if (counts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TextMMD(text = stringResource(R.string.lightning_in_range), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (counts.size > 1) {
                Spacer(Modifier.height(10.dp))
                for (c in counts) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        TextMMD(text = format.format(Date(c.at)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                        TextMMD(
                            text = stringResource(R.string.lightning_row, Lightning.seconds(c.seconds),
                                Lightning.distance(Lightning.miles(c.seconds)), Lightning.distance(Lightning.km(c.seconds))),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** The counts through a stop and restart, as "time:seconds" strings. */
private fun countsSaver() = listSaver<androidx.compose.runtime.MutableState<List<Lightning.Count>>, String>(
    save = { state -> state.value.map { "${it.at}:${it.seconds}" } },
    restore = { saved ->
        mutableStateOf(saved.mapNotNull { s ->
            val at = s.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            val sec = s.substringAfter(':').toDoubleOrNull() ?: return@mapNotNull null
            Lightning.Count(at, sec)
        })
    },
)

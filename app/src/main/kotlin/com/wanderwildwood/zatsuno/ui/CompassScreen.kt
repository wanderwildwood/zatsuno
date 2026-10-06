package com.wanderwildwood.zatsuno.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.compass.Bearing
import com.wanderwildwood.zatsuno.compass.CompassModel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The compass: a card that turns, the heading as a number, magnetic or true north, the
 * position under it, and Freeze to hold all of it still for reading or copying.
 *
 * The card and the reading follow kCompass (Ondřej Koloničný, OK1CDJ, GPL-3.0). True north is
 * only offered as a number once there is a position, since the declination depends on where
 * you are; until then the screen says so rather than showing magnetic labelled true.
 */
@Composable
fun CompassScreen(model: CompassModel, onBack: () -> Unit) {
    RunWhileShown(model)
    val context = LocalContext.current
    val live by model.live.collectAsState()
    val points = stringArrayResource(R.array.points)
    Screen(stringResource(R.string.home_compass), onBack) { modifier ->
        Column(modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val heading = live.heading
            Rose(heading, listOf(points[0], points[2], points[4], points[6]), Modifier.padding(top = 6.dp).size(176.dp))
            val headingText = when {
                !live.hasSensor -> null
                heading == null -> "—"
                else -> "${Bearing.whole(heading)}° ${points[Bearing.point(heading)]} " +
                    stringResource(if (live.trueNorth) R.string.heading_true else R.string.heading_magnetic)
            }
            if (headingText != null) {
                // A figure the instrument reads out, not type, so it is sized as one.
                TextMMD(text = headingText, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            } else {
                TextMMD(text = stringResource(R.string.compass_no_sensor), style = MaterialTheme.typography.bodyMedium)
            }
            val note = when {
                live.trueNorth && live.fix == null && live.magnetic != null -> stringResource(R.string.compass_true_needs_fix)
                live.frozen -> stringResource(R.string.compass_frozen)
                else -> stringResource(R.string.compass_flat)
            }
            TextMMD(text = note, style = MaterialTheme.typography.labelSmall)

            // True north is a row with its switch: the row takes the press.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { model.setTrueNorth(!live.trueNorth) }
                    .padding(vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    TextMMD(text = stringResource(R.string.compass_true), style = MaterialTheme.typography.bodyLarge)
                    live.fix?.let {
                        val d = Bearing.declinationText(it.declination, stringResource(R.string.dir_east), stringResource(R.string.dir_west))
                        TextMMD(text = stringResource(R.string.compass_declination, d), style = MaterialTheme.typography.labelSmall)
                    }
                }
                SwitchMMD(checked = live.trueNorth, onCheckedChange = null)
            }

            Spacer(Modifier.height(4.dp))
            val fix = live.fix
            if (fix != null) PositionLines(fix, big = false) else LocationPrompt(live) { model.refresh() }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WideButton(
                    stringResource(if (live.frozen) R.string.compass_resume else R.string.compass_freeze),
                    Modifier.weight(1f),
                ) { model.freeze() }
                if (fix != null) {
                    WideButton(stringResource(R.string.pos_share), Modifier.weight(1f)) { sharePosition(context, fix) }
                    WideButton(stringResource(R.string.pos_map), Modifier.weight(1f)) { openInMap(context, fix) }
                }
            }
        }
    }
}

/**
 * The card turns so its N points at north; the mark at the top is where the phone points.
 * Ink only: lines and letters, nothing filled but the small mark itself.
 */
@Composable
private fun Rose(heading: Float?, letters: List<String>, modifier: Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(cx, cy) - 14f
        val centre = Offset(cx, cy)
        drawCircle(Color.Black, radius = r, center = centre, style = Stroke(width = 3f))
        val mark = Path().apply {
            moveTo(cx, cy - r + 2f)
            lineTo(cx - 10f, cy - r - 13f)
            lineTo(cx + 10f, cy - r - 13f)
            close()
        }
        drawPath(mark, Color.Black)
        rotate(degrees = -(heading ?: 0f), pivot = centre) {
            for (deg in 0 until 360 step 15) {
                val a = Math.toRadians(deg.toDouble())
                val len = when {
                    deg % 90 == 0 -> 22f
                    deg % 45 == 0 -> 15f
                    else -> 9f
                }
                val w = if (deg % 90 == 0) 4f else 2f
                drawLine(
                    Color.Black,
                    Offset(cx + (r - len) * sin(a).toFloat(), cy - (r - len) * cos(a).toFloat()),
                    Offset(cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat()),
                    strokeWidth = w,
                )
            }
            // The needle: north half outlined heavy, south half a thin line.
            val tip = Offset(cx, cy - (r - 34f))
            val needle = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(cx - 11f, cy)
                lineTo(cx + 11f, cy)
                close()
            }
            drawPath(needle, Color.Black, style = Stroke(width = 4f))
            drawLine(Color.Black, centre, Offset(cx, cy + (r - 34f)), strokeWidth = 2f)
            val paint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 20f * density
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            for ((i, letter) in letters.withIndex()) {
                val a = Math.toRadians(i * 90.0)
                val lr = r - 40f * density / 2.6f - 14f
                val fm = paint.fontMetrics
                val x = cx + lr * sin(a).toFloat()
                val y = cy - lr * cos(a).toFloat() - (fm.ascent + fm.descent) / 2f
                drawContext.canvas.nativeCanvas.drawText(letter, x, y, paint)
            }
        }
    }
}

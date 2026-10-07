package com.wanderwildwood.zatsuno.card

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.ui.Screen
import com.wanderwildwood.zatsuno.ui.WideButton
import com.wanderwildwood.zatsuno.ui.monochrome

/**
 * Someone to call, handed over by another app: Contacts' "Add to emergency card". The name
 * and number are shown with Add and Cancel, and nothing changes until Add is pressed; then
 * they join the card's emergency contacts as if picked there with "Add from Contacts".
 *
 * Action `com.wanderwildwood.zatsuno.action.ADD_EMERGENCY_CONTACT`, with [EXTRA_NAME] and
 * [EXTRA_NUMBER] (a number is needed). Add returns RESULT_OK; anything else RESULT_CANCELED.
 */
class AddContactActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val contact = Card.given(intent.getStringExtra(EXTRA_NAME), intent.getStringExtra(EXTRA_NUMBER))
        if (contact == null) {
            finish()
            return
        }
        val already = CardStore.load(this).hasNumber(contact.number)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                BackHandler { finish() }
                Screen(title = stringResource(R.string.card_title), onBack = { finish() }) { modifier ->
                    Column(modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        TextMMD(
                            text = stringResource(if (already) R.string.card_given_already else R.string.card_given_ask),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextMMD(
                            text = listOf(contact.name, contact.number).filter { it.isNotEmpty() }.joinToString("  "),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(20.dp))
                        if (!already) {
                            WideButton(stringResource(R.string.card_given_add)) {
                                // Read again: the card may have changed while this was open.
                                val card = CardStore.load(this@AddContactActivity)
                                if (!card.hasNumber(contact.number)) {
                                    CardStore.save(this@AddContactActivity, card.copy(contacts = card.contacts + contact))
                                }
                                Toast.makeText(this@AddContactActivity, getString(R.string.card_given_done), Toast.LENGTH_SHORT).show()
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        WideButton(stringResource(if (already) R.string.about_close else R.string.card_given_cancel), Modifier.fillMaxWidth()) {
                            finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_NAME = "com.wanderwildwood.zatsuno.extra.NAME"
        const val EXTRA_NUMBER = "com.wanderwildwood.zatsuno.extra.NUMBER"
    }
}

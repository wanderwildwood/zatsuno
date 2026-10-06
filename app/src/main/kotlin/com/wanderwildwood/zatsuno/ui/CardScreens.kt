package com.wanderwildwood.zatsuno.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.card.Card
import com.wanderwildwood.zatsuno.card.CardStore
import com.wanderwildwood.zatsuno.card.Contact
import com.wanderwildwood.zatsuno.card.Field
import com.wanderwildwood.zatsuno.glance.CardOnLockScreen

/** The card as it reads to someone else: each filled line under its label; a contact dials. */
@Composable
fun CardLines(card: Card, bordered: Boolean) {
    val context = LocalContext.current
    val box = if (bordered) Modifier
        .padding(top = 12.dp, bottom = 6.dp)
        .border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(12.dp))
        .padding(14.dp) else Modifier
    Column(Modifier.fillMaxWidth().then(box)) {
        if (bordered) TextMMD(text = stringResource(R.string.card_title), style = MaterialTheme.typography.labelSmall)
        val contacts = card.contacts.iterator()
        for ((field, text) in card.lines()) {
            val contact = if (field == Field.CONTACTS && contacts.hasNext()) contacts.next() else null
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (contact != null && contact.number.isNotBlank()) Modifier.clickable { dial(context, contact.number) } else Modifier)
                    .padding(vertical = 4.dp),
            ) {
                TextMMD(
                    text = stringResource(CardOnLockScreen.label(field)),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(92.dp).padding(top = 2.dp),
                )
                TextMMD(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (field == Field.ALLERGIES || field == Field.CONDITIONS) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** The card to read, with Edit in the bar; or, while it is empty, what it is for and a start. */
@Composable
fun CardScreen(onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val card = remember { CardStore.load(context) }
    Screen(
        title = stringResource(R.string.card_title),
        onBack = onBack,
        actions = {
            if (!card.isEmpty) BarButton(Icons.Share, stringResource(R.string.cd_share)) {
                shareText(context, context.getString(R.string.card_title), cardText(context, card))
            }
        },
    ) { modifier ->
        LazyColumnMMD(modifier = modifier) {
            item(key = "body") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    if (card.isEmpty) {
                        TextMMD(text = stringResource(R.string.card_empty), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        CardLines(card, bordered = false)
                    }
                    Spacer(Modifier.height(16.dp))
                    WideButton(stringResource(if (card.isEmpty) R.string.card_fill else R.string.card_edit), onClick = onEdit)
                    Spacer(Modifier.height(12.dp))
                    TextMMD(
                        text = stringResource(if (card.onLockScreen) R.string.card_lock_on else R.string.card_lock_off),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** The card as text, for sending to someone or to Notes. */
fun cardText(context: Context, card: Card): String = buildString {
    appendLine(context.getString(R.string.card_title))
    for ((field, text) in card.lines()) appendLine("${context.getString(CardOnLockScreen.label(field))}: $text")
}.trimEnd()

/**
 * Filling the card in. Every line is optional. Contacts come from the phone's own contact
 * picker, so the Contacts app (enishi or any other) answers it; medicines can come from
 * Medicine (fukuyaku) when it is installed. What goes on the lock screen is chosen here, off
 * until switched on, with what that means said beside it.
 */
@Composable
fun CardEditScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val start = remember { CardStore.load(context) }
    var name by rememberSaveable { mutableStateOf(start.name) }
    var blood by rememberSaveable { mutableStateOf(start.blood) }
    var allergies by rememberSaveable { mutableStateOf(start.allergies) }
    var medications by rememberSaveable { mutableStateOf(start.medications) }
    var conditions by rememberSaveable { mutableStateOf(start.conditions) }
    var note by rememberSaveable { mutableStateOf(start.note) }
    var contacts by rememberSaveable { mutableStateOf(Card.encodeContacts(start.contacts)) }
    var lock by rememberSaveable { mutableStateOf(start.onLockScreen) }
    var shown by rememberSaveable { mutableStateOf(start.shown.map { it.name }) }

    fun current() = Card(
        name, blood, allergies, medications, conditions, Card.decodeContacts(contacts), note, lock,
        shown.mapNotNull { n -> Field.entries.firstOrNull { it.name == n } }.toSet(),
    )
    fun save() { CardStore.save(context, current()); onDone() }

    val pickContact = rememberLauncherForActivityResult(PickPhone()) { picked ->
        if (picked != null) contacts = Card.encodeContacts(Card.decodeContacts(contacts) + picked)
    }
    val medicine = remember { medicineIntent(context) }
    val fromMedicine = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringExtra(Intent.EXTRA_TEXT)
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) medications = text.trim()
    }

    Screen(
        title = stringResource(R.string.card_title),
        onBack = ::save,
    ) { modifier ->
        androidx.activity.compose.BackHandler { save() }
        LazyColumnMMD(modifier = modifier, scrollStep = 2) {
            item(key = "name") { Field(stringResource(R.string.card_name), name) { name = it } }
            item(key = "blood") { Field(stringResource(R.string.card_blood), blood) { blood = it } }
            item(key = "allergies") { Field(stringResource(R.string.card_allergies), allergies, lines = 2) { allergies = it } }
            item(key = "meds") {
                Column {
                    Field(stringResource(R.string.card_medications), medications, lines = 3) { medications = it }
                    if (medicine != null) {
                        WideButton(stringResource(R.string.card_from_medicine), Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                            runCatching { fromMedicine.launch(medicine) }
                        }
                    }
                }
            }
            item(key = "conditions") { Field(stringResource(R.string.card_conditions), conditions, lines = 2) { conditions = it } }
            item(key = "contacts") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    TextMMD(text = stringResource(R.string.card_contacts), style = MaterialTheme.typography.labelSmall)
                    val list = Card.decodeContacts(contacts)
                    list.forEachIndexed { i, c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            TextMMD(text = "${c.name}  ${c.number}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            BarButton(Icons.Close, stringResource(R.string.cd_remove)) {
                                contacts = Card.encodeContacts(list.filterIndexed { j, _ -> j != i })
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    WideButton(stringResource(R.string.card_add_contact)) {
                        runCatching { pickContact.launch(Unit) }
                            .onFailure { android.widget.Toast.makeText(context, context.getString(R.string.no_app), android.widget.Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            item(key = "note") { Field(stringResource(R.string.card_note), note, lines = 3) { note = it } }
            item(key = "lock") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    SwitchRow(stringResource(R.string.card_on_lock_screen), lock) { lock = it }
                    TextMMD(text = stringResource(R.string.card_lock_warning), style = MaterialTheme.typography.labelSmall)
                    if (lock) {
                        Spacer(Modifier.height(8.dp))
                        for (f in Field.entries) {
                            SwitchRow(stringResource(CardOnLockScreen.label(f)), f.name in shown) { on ->
                                shown = if (on) shown + f.name else shown - f.name
                            }
                        }
                        TextMMD(text = stringResource(R.string.card_lock_glance), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item(key = "done") {
                WideButton(stringResource(R.string.card_done), Modifier.padding(20.dp).fillMaxWidth()) { save() }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        TextMMD(text = label, style = MaterialTheme.typography.labelSmall)
        TextFieldMMD(
            value = value,
            onValueChange = onChange,
            singleLine = lines == 1,
            maxLines = lines,
            modifier = Modifier.fillMaxWidth().textActions(),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
    ) {
        TextMMD(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        SwitchMMD(checked = checked, onCheckedChange = null)
    }
}

/**
 * The phone's own picker over phone numbers. The answer comes with a one-time grant to read
 * that one number, so Field Kit needs no contacts permission.
 */
private class PickPhone : ActivityResultContract<Unit, Contact?>() {
    private lateinit var context: Context

    override fun createIntent(context: Context, input: Unit): Intent {
        this.context = context.applicationContext
        return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Contact? {
        val uri: Uri = intent?.data ?: return null
        if (resultCode != Activity.RESULT_OK) return null
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) Contact(c.getString(0).orEmpty(), c.getString(1).orEmpty()) else null }
        }.getOrNull()
    }
}

/** Medicine's way of handing over its list, if Medicine is on the phone; otherwise null. */
private fun medicineIntent(context: Context): Intent? {
    val intent = Intent(MEDICINE_ACTION).setPackage(MEDICINE_PACKAGE)
    @Suppress("DEPRECATION")
    return if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) intent else null
}

private const val MEDICINE_PACKAGE = "com.wanderwildwood.fukuyaku"
private const val MEDICINE_ACTION = "com.wanderwildwood.fukuyaku.action.SHARE_MEDICINES"

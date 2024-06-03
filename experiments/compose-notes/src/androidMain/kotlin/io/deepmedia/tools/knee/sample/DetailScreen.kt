package io.deepmedia.tools.knee.sample

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.*

@Composable
fun DetailScreen(noteManager: NoteManager, note: Note, modifier: Modifier = Modifier, navigate: (Destination) -> Unit) {
    BackHandler { navigate(Destination.List) }
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Delete") },
                icon = { Image(Icons.Rounded.Delete, "Delete") },
                onClick = {
                    noteManager.removeNote(note.id)
                    navigate(Destination.List)
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {

            Text(
                text = "Note",
                modifier = Modifier.padding(top = 96.dp, bottom = 16.dp).padding(horizontal = 24.dp),
                style = MaterialTheme.typography.h4.copy(fontFamily = FontFamily.Monospace)
            )

            val context = LocalContext.current
            val format = remember(context) { DateFormat.getMediumDateFormat(context) }
            val date = remember(note.date) { format.format(Date(note.date)) }
            Text(
                text = "${note.author}, $date",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.subtitle2
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = note.content,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
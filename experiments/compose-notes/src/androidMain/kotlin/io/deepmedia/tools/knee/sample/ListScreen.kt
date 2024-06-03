package io.deepmedia.tools.knee.sample

import android.text.format.DateFormat
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Date


// Use NoteManager.size and NoteManager.noteAt to collect a list
// (until we add proper list/array support!)
// private val NoteManager.notes get() = Array(size) { noteAt(it) }.toList()
// EDIT: list support added
private val NoteManager.notes get() = current


@Composable
fun ListScreen(noteManager: NoteManager, modifier: Modifier = Modifier, navigate: (Destination) -> Unit) {
    var notes by remember { mutableStateOf(noteManager.notes) }
    DisposableEffect(noteManager) {
        val callback = object : NoteManager.Callback {
            override fun onNoteAdded(note: Note) { notes = noteManager.notes }
            override fun onNoteRemoved(note: Note) { notes = noteManager.notes }
        }
        noteManager.registerCallback(callback)
        onDispose { noteManager.unregisterCallback(callback) }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navigate(Destination.Editor) },
                content = { Image(imageVector = Icons.Rounded.Add, contentDescription = "Add") }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    text = "Notes",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.h4,
                    modifier = Modifier.padding(top = 96.dp, bottom = 16.dp).padding(horizontal = 24.dp)
                )
            }
            items(notes.reversed()) { note ->
                NotePreview(note, Modifier.fillMaxWidth()
                    .clickable { navigate(Destination.Detail(note)) }
                    .padding(vertical = 8.dp, horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun NotePreview(note: Note, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getMediumDateFormat(context) }
    val date = remember(note.date) { format.format(Date(note.date)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${note.author}, $date",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.subtitle2
        )
        Text(
            text = note.content,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}
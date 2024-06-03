package io.deepmedia.tools.knee.sample

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.*

@Composable
fun EditorScreen(noteManager: NoteManager, modifier: Modifier = Modifier, navigate: (Destination) -> Unit) {
    BackHandler { navigate(Destination.List) }
    var author by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Save") },
                icon = { Image(Icons.Rounded.Check, "Save") },
                onClick = {
                    if (author.isBlank() || content.isBlank()) return@ExtendedFloatingActionButton
                    val note = Note(UUID.randomUUID().toString(), author, System.currentTimeMillis(), content)
                    noteManager.addNote(note)
                    navigate(Destination.List)
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.padding(top = 96.dp, bottom = 16.dp).padding(horizontal = 24.dp)
            ) {
                val style = MaterialTheme.typography.h4.copy(
                    fontFamily = FontFamily.Monospace
                )
                if (author.isEmpty()) {
                    Text("Who?", Modifier.alpha(0.5F), style = style)
                }
                BasicTextField(
                    value = author,
                    onValueChange = { author = it.replace("\n", "") },
                    textStyle = style,
                    maxLines = 1,
                )
            }
            Box(Modifier.padding(16.dp)) {
                if (content.isEmpty()) {
                    Text("Write content...", Modifier.alpha(0.5F))
                }
                BasicTextField(content, { content = it })
            }
        }
    }
}
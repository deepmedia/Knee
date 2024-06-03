package io.deepmedia.tools.knee.sample

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.lightColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier


class NotesActivity : androidx.activity.ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("compose_notes")
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colors = lightColors()) {
                Surface {
                    RootScreen(Modifier.fillMaxSize())
                }
            }
        }
    }
}

sealed interface Destination {
    object List : Destination
    class Detail(val note: Note) : Destination
    object Editor : Destination
}

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    val noteManager = remember { NoteManager() }
    /* DisposableEffect(Unit) { onDispose { noteManager.finalize() } } */

    var currentDestination by remember { mutableStateOf<Destination>(Destination.List) }
    when (val dest = currentDestination) {
        is Destination.List -> ListScreen(noteManager, modifier) { currentDestination = it }
        is Destination.Detail -> DetailScreen(noteManager, dest.note, modifier) { currentDestination = it }
        is Destination.Editor -> EditorScreen(noteManager, modifier) { currentDestination = it }
    }
}
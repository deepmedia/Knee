package io.deepmedia.tools.knee.sample

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.flowOf


class ExpectActualActivity : androidx.activity.ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("expect_actual")
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


@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(jvmToString())
        Text(targetName())
        Text(PlatformInfoA().targetName)
        Text(PlatformInfoB().targetName)
    }
}
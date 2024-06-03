package io.deepmedia.tools.knee.sample.mm.consumer

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.lightColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.deepmedia.tools.knee.mm.consumer.ConsumerEnum
import io.deepmedia.tools.knee.mm.consumer.getConsumerEnum
import io.deepmedia.tools.knee.mm.consumer.getProducerClassExportedByConsumer
import io.deepmedia.tools.knee.mm.consumer.getProducerEnumExportedByConsumer
import io.deepmedia.tools.knee.mm.consumer.getProducerInterfaceExportedByConsumer
import io.deepmedia.tools.knee.sample.mm.producer.ProducerFrontendEnum
import io.deepmedia.tools.knee.sample.mm.producer.getProducerEnum


class ConsumerActivity : androidx.activity.ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("multimodule_consumer")
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
        Text("Consumer enum:")
        Text(getConsumerEnum().toString())
        Spacer(Modifier.height(16.dp))

        Text("Producer enum (provided by its own module):")
        Text(getProducerEnum().toString())
        Spacer(Modifier.height(16.dp))

        Text("Producer enum (provided by consumer module):")
        Text(getProducerEnumExportedByConsumer().toString())
        Spacer(Modifier.height(16.dp))

        Text("Producer interface (provided by consumer module):")
        Text(getProducerInterfaceExportedByConsumer().toString())
        Spacer(Modifier.height(16.dp))

        Text("Producer class (provided by consumer module):")
        Text(getProducerClassExportedByConsumer().toString())
        Spacer(Modifier.height(16.dp))
    }
}
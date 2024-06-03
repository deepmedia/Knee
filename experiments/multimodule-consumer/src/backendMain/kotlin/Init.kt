package io.deepmedia.tools.knee.mm.consumer

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import io.deepmedia.tools.knee.sample.mm.producer.ProducerClass
import io.deepmedia.tools.knee.sample.mm.producer.ProducerEnum
import io.deepmedia.tools.knee.sample.mm.producer.ProducerInterface
import io.deepmedia.tools.knee.sample.mm.producer.initProducerKnee
import kotlin.random.Random

@CName(externName = "JNI_OnLoad")
@KneeInit
fun initKnee(jvm: JavaVirtualMachine) {
    jvm.useEnv { env ->
        initProducerKnee(env)
    }
}

@KneeEnum
enum class ConsumerEnum {
    Foo, Bar
}

@Knee
fun getConsumerEnum(): ConsumerEnum {
    return ConsumerEnum.Foo
}

@Knee
fun getProducerEnumExportedByConsumer(): ProducerEnum {
    return ProducerEnum.Bar
}

@Knee
fun getProducerClassExportedByConsumer(): ProducerClass {
    return ProducerClass()
}

@Knee
fun getProducerInterfaceExportedByConsumer(): ProducerInterface {
    return object : ProducerInterface {
        override val foo: Int
            get() = 20
    }
}
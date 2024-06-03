package io.deepmedia.tools.knee.sample.mm.producer

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*

@KneeInit
fun initProducerKnee(env: JniEnvironment) {

}

@KneeEnum(exported = true)
enum class ProducerEnum {
    Foo, Bar
}

@KneeClass(exported = true)
class ProducerClass {
    fun asd() = 2
}

@KneeInterface(exported = true)
interface ProducerInterface {
    val foo: Int
}

@Knee
fun getProducerEnum(): ProducerEnum {
    return ProducerEnum.Foo
}
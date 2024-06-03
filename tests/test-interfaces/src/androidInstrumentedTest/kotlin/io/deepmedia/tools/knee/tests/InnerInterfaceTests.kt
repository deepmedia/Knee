package io.deepmedia.tools.knee.tests

import org.junit.Test
import java.lang.System.identityHashCode

class InnerInterfaceTests {

    companion object {
        init {
            System.loadLibrary("test_interfaces")
        }
    }

    val jvm = object : Outer.Inner {
        override var value: Int = 24
    }

    @Test
    fun testProperty_retrieveNatively() {
        val kn = makeInner(0)
        // update on JVM, retreive natively
        jvm.value = 100
        kn.value = 100
        require(jvm.value == getInnerValue(kn))
        // update on KN, retreive natively
        setInnerValue(jvm, 200)
        setInnerValue(kn, 200)
        require(jvm.value == getInnerValue(kn))
    }

    @Test
    fun testProperty_updateNatively() {
        val kn = makeInner(0)
        // update natively, retreive on JVM
        jvm.value = 300
        setInnerValue(kn, 300)
        require(jvm.value == kn.value)
        require(jvm.value == 300)

        // update natively, retreive on KN
        jvm.value = 400
        setInnerValue(kn, 400)
        require(getInnerValue(jvm) == getInnerValue(kn))
        require(getInnerValue(kn) == 400)
    }
}

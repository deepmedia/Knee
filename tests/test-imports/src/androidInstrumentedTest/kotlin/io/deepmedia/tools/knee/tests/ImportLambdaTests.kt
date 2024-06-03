package io.deepmedia.tools.knee.tests

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.lang.System.identityHashCode
import kotlin.math.abs

class ImportLambdaTests {

    companion object {
        init {
            System.loadLibrary("test_imports")
        }
    }

    @Test
    fun testImportedInterface_simpleLambda_property() {
        val lambda: () -> Unit = { }
        currentSimpleLambda = lambda
        check(currentSimpleLambda == lambda)
    }

    @Test
    fun testImportedInterface_simpleLambda_jvmInvoke() {
        val native: () -> Unit = makeSimpleLambda()
        native.invoke()
    }

    @Test
    fun testImportedInterface_simpleLambda_nativeInvoke() {
        val jvm: () -> Unit = { }
        invokeSimpleLambda(jvm)
    }

    @Test
    fun testImportedInterface_complexLambda_property() {
        val lambda: (String, Long) -> String = { a, b -> a + b }
        currentComplexLambda = lambda
        check(currentComplexLambda == lambda)
    }

    @Test
    fun testImportedInterface_complexLambda_jvmInvoke() {
        val native: (String, Long) -> String = makeComplexLambda()
        val result = native.invoke("Hello", 10)
        check(result == "Hello10")
    }

    @Test
    fun testImportedInterface_complexLambda_nativeInvoke() {
        val lambda: (String, Long) -> String = { a, b -> a + b }
        val result = invokeComplexLambda(lambda, "Hello", 20)
        check(result == "Hello20")
    }

    @Test
    fun testImportedInterface_complexLambda2_property() {
        val lambda: (Int, UInt) -> String = { a, b -> (a + b.toInt()).toString() }
        currentComplexLambda2 = lambda
        check(currentComplexLambda2 == lambda)
    }

    @Test
    fun testImportedInterface_complexLambda2_jvmInvoke() {
        val native: (Int, UInt) -> String = makeComplexLambda2()
        val result = native.invoke(30, 10u)
        check(result == "40")
    }

    @Test
    fun testImportedInterface_complexLambda2_nativeInvoke() {
        val lambda: (Int, UInt) -> String = { a, b -> (a + b.toInt()).toString() }
        val result = invokeComplexLambda2(lambda, 25, 25u)
        check(result == "50")
    }

    @Test
    fun testImportedInterface_simpleSuspendLambda_property() {
        val lambda: suspend () -> Unit = { }
        currentSimpleSuspendLambda = lambda
        check(currentSimpleSuspendLambda == lambda)
    }

    @Test
    fun testImportedInterface_simpleSuspendLambda_jvmInvoke() = runBlocking {
        val native: suspend () -> Unit = makeSimpleSuspendLambda()
        native.invoke()
    }

    @Test
    fun testImportedInterface_simpleSuspendLambda_nativeInvoke() = runBlocking {
        val jvm: suspend () -> Unit = { }
        invokeSimpleSuspendLambda(jvm)
    }


    @Test
    fun testImportedInterface_complexSuspendLambda_property() {
        val lambda: suspend (String, Int) -> ULong = { a, b -> (a.length + b).toULong() }
        currentSuspendComplexLambda = lambda
        check(currentSuspendComplexLambda == lambda)
    }

    @Test
    fun testImportedInterface_complexSuspendLambda_jvmInvoke() = runBlocking {
        val native: suspend (String, Int) -> ULong = makeSuspendComplexLambda()
        val result = native.invoke("Hello", 10)
        check(result == 15.toULong())
    }

    @Test
    fun testImportedInterface_complexSuspendLambda_nativeInvoke() = runBlocking {
        val lambda: suspend (String, Int) -> ULong = { a, b -> (a.length + b).toULong() }
        val result = invokeSuspendComplexLambda(lambda, "Hi", 20)
        check(result == 22.toULong())
    }
}

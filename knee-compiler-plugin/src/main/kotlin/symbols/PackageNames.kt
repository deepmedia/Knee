package io.deepmedia.tools.knee.plugin.compiler.symbols

import org.jetbrains.kotlin.name.FqName

object PackageNames {
    val kotlin = FqName("kotlin")
    val kotlinCollections = FqName("kotlin.collections")
    val kotlinCoroutines = FqName("kotlin.coroutines")
    val cinterop = FqName("kotlinx.cinterop")
    val platformAndroid = FqName("platform.android")
    val annotations = FqName("io.deepmedia.tools.knee.annotations")
    val runtime = FqName("io.deepmedia.tools.knee.runtime")
    val runtimeCompiler = FqName("io.deepmedia.tools.knee.runtime.compiler")
    val runtimeTypes = FqName("io.deepmedia.tools.knee.runtime.types")
    val runtimeCollections = FqName("io.deepmedia.tools.knee.runtime.collections")
    val runtimeBuffer = FqName("io.deepmedia.tools.knee.runtime.buffer")
    val runtimeModule = FqName("io.deepmedia.tools.knee.runtime.module")
}


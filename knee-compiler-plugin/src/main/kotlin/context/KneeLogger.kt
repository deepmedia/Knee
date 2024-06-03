package io.deepmedia.tools.knee.plugin.compiler.context

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.name.Name

class KneeLogger(
    private val collector: MessageCollector,
    private val verboseLogs: Boolean,
    private val verboseRuntime: Boolean
) {

    fun logWarning(message: String) {
        if (verboseLogs) println(message)
        collector.report(CompilerMessageSeverity.WARNING, message)
    }

    fun logMessage(message: String) {
        if (verboseLogs) println(message)
    }

    private var printlnIr: IrSimpleFunctionSymbol? = null
    private val printlnCodegen = MemberName("kotlin", "println")

    fun injectLog(scope: IrStatementsBuilder<*>, message: String) {
        if (!verboseRuntime) return

        if (printlnIr == null) {
            val builtIns = (scope.parent as IrDeclaration).file.module.irBuiltins
            val function = builtIns.findFunctions(Name.identifier("println"), "kotlin", "export")
            printlnIr = function.single { it.owner.valueParameters.firstOrNull()?.type == builtIns.stringType }
        }

        with(scope) {
            +irCall(printlnIr!!).apply {
                putValueArgument(0, scope.irString("[KNEE_KN] $message"))
            }
        }
    }


    fun injectLog(scope: CodeBlock.Builder, message: String) {
        if (!verboseRuntime) return
        scope.addStatement("%M(%S)", printlnCodegen, "[KNEE_JVM] $message")
    }
}
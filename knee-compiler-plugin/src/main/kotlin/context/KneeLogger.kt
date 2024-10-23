package io.deepmedia.tools.knee.plugin.compiler.context

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.makeNullable
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

    private var printlnIrString: IrSimpleFunctionSymbol? = null
    private var printlnIrAny: IrSimpleFunctionSymbol? = null
    private val printlnCodegen = MemberName("kotlin.io", "println")

    fun injectLog(scope: IrStatementsBuilder<*>, message: String) {
        if (!verboseRuntime) return

        if (printlnIrString == null) {
            val builtIns = (scope.parent as IrDeclaration).file.module.irBuiltins
            val function = builtIns.findFunctions(Name.identifier("println"), "kotlin", "io")
            printlnIrString = function.single { it.owner.valueParameters.firstOrNull()?.type == builtIns.stringType }
        }

        with(scope) {
            +irCall(printlnIrString!!).apply {
                putValueArgument(0, scope.irString("[KNEE_KN] $message"))
            }
        }
    }

    fun injectLog(scope: IrStatementsBuilder<*>, objToPrint: IrValueDeclaration) {
        if (!verboseRuntime) return

        if (printlnIrAny == null) {
            val builtIns = (scope.parent as IrDeclaration).file.module.irBuiltins
            val function = builtIns.findFunctions(Name.identifier("println"), "kotlin", "io")
            printlnIrAny = function.single { it.owner.valueParameters.firstOrNull()?.type == builtIns.anyType.makeNullable() }
        }

        with(scope) {
            +irCall(printlnIrAny!!).apply {
                putValueArgument(0, irGet(objToPrint))
            }
        }
    }

    fun injectLog(scope: CodeBlock.Builder, message: String) {
        if (!verboseRuntime) return
        scope.addStatement("%M(%S)", printlnCodegen, "[KNEE_JVM] $message")
    }
}
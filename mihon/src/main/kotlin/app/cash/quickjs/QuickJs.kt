// Comic TV adaptation: JavaScript is an explicitly unsupported optional capability.
package app.cash.quickjs

import java.io.Closeable

class QuickJs private constructor() : Closeable {
    fun evaluate(script: String): Any? = unsupported()
    fun evaluate(script: String, fileName: String): Any? = unsupported()
    fun compile(sourceCode: String, fileName: String): ByteArray = unsupported()
    fun execute(bytecode: ByteArray): Any? = unsupported()
    override fun close() = Unit
    private fun unsupported(): Nothing = throw UnsupportedOperationException("This extension requires QuickJS; JavaScript execution is not supported in Comic TV yet")
    companion object { @JvmStatic fun create(): QuickJs = QuickJs() }
}

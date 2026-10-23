package com.pelican.comictv.sources

import java.lang.reflect.InvocationTargetException
import org.junit.Assert.*
import org.junit.Test

class ExtensionLoadDiagnosticTest {
    @Test fun unwrapsConstructorFailure() {
        val failure = InvocationTargetException(NoSuchMethodError("missing API"))
        assertEquals("NoSuchMethodError: missing API", extensionLoadDiagnostic(failure))
    }
    @Test fun boundsAndFlattensMessage() {
        val text = extensionLoadDiagnostic(IllegalStateException("a\nb" + "x".repeat(1000)))
        assertTrue(text.length <= 350)
        assertFalse(text.contains('\n'))
    }
    @Test fun retainsTypeWithoutMessage() {
        assertEquals("ClassNotFoundException", extensionLoadDiagnostic(ClassNotFoundException()))
    }
    @Test fun handlesCyclicCauses() {
        val first = Exception("first")
        val second = Exception("second", first)
        first.initCause(second)
        assertTrue(extensionLoadDiagnostic(first).length <= 350)
    }
}

package com.pelican.comictv.updates

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateProviderTest {
    @Test fun updateProviderExposesOnlyItsOwnDirectory() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.cacheDir,"updates").apply{mkdirs()}
        val inside=File(dir,"provider-${System.nanoTime()}.apk").apply{writeText("test")}
        val outside=File(context.cacheDir,"outside-${System.nanoTime()}.apk").apply{writeText("private")}
        try {
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",inside)
            assertEquals("test",context.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()})
            try {FileProvider.getUriForFile(context,"${context.packageName}.updates",outside);fail("Outside file exposed")}
            catch(_:IllegalArgumentException){}
        } finally {inside.delete();outside.delete()}
    }
}

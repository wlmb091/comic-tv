package com.pelican.comictv

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestProviderIdentityTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    @Test fun everyFileProviderHasItsOwnComponentClass() {
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val nodes = manifest.getElementsByTagName("provider")
        val names = (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
        assertEquals("Provider components must not repeat", names.distinct(), names)
        assertEquals(3, names.size)
    }
}

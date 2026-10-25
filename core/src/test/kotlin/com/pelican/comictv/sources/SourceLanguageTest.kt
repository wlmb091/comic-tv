package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class SourceLanguageTest {
    @Test fun languageAndNameFiltersCombineWithoutGuessingMultilingualSupport() {
        val sources = listOf(SourceHandle("a",1,"Brasil","pt-BR",""), SourceHandle("b",2,"English","en",""), SourceHandle("c",3,"Multi","all",""))
        assertEquals(listOf("all","en","pt-br"), sourceLanguages(sources))
        assertEquals(listOf(sources[0]), filterSources(sources,"","pt-BR"))
        assertTrue(filterSources(sources,"English","pt-br").isEmpty())
        assertEquals(sources,filterSources(sources,"",""))
        assertTrue(sourceLanguageLabel("pt-BR").contains("portugu",true))
    }
}

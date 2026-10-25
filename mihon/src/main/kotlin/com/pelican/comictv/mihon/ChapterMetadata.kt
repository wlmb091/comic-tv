package com.pelican.comictv.mihon

import com.pelican.comictv.sources.OnlineChapter
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.*

internal fun SChapter.toOnlineChapter(): OnlineChapter {
    val metadata=memo.toString()
    require(metadata.length <= 65536) {"Os metadados do capítulo excedem o limite."}
    return OnlineChapter(url,name,chapter_number,metadata,scanlator,date_upload)
}
internal fun OnlineChapter.toSourceChapter(): SChapter = SChapter.create().also { chapter ->
    chapter.url=key;chapter.name=title;chapter.chapter_number=number
    chapter.scanlator=scanlator;chapter.date_upload=uploadedAt
    memo?.let { metadata ->
        require(metadata.length <= 65536) {"Os metadados do capítulo excedem o limite."}
        chapter.memo=Json.parseToJsonElement(metadata).jsonObject
    }
}

internal suspend fun readChapterPages(chapter: OnlineChapter, refresh: (suspend () -> List<OnlineChapter>)?,
    fetch: suspend (SChapter) -> List<eu.kanade.tachiyomi.source.model.Page>): List<eu.kanade.tachiyomi.source.model.Page> {
    return try { fetch(chapter.toSourceChapter()) }
    catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException || refresh == null || !e.message.orEmpty().contains("Refresh Chapter List",true)) throw e
        val updated=refresh().firstOrNull {it.key == chapter.key}
            ?: throw java.io.IOException("O capítulo mudou na fonte. Atualize a lista e escolha novamente.")
        return fetch(updated.toSourceChapter())
    }
}

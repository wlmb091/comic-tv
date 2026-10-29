package com.pelican.comictv.details

import com.pelican.comictv.library.ComicEntity
import com.pelican.comictv.sources.*

internal data class DetailChapter(val id: String, val title: String, val chapter: OnlineChapter? = null,
    val progress: ComicEntity? = null)

internal fun chapterReadStatus(comic: ComicEntity?): String = when {
    comic == null || comic.lastRead <= 0 -> "Não lido"
    comic.pageCount > 0 && comic.currentPage >= comic.pageCount - 1 -> "Lido"
    else -> "Em andamento"
}

/** Source/title/chapter identity, never a chapter number or a display title match. */
internal fun detailChapters(context: OnlineEditions, order: ChapterOrder, comics: List<ComicEntity>): List<DetailChapter> {
    val byId = comics.associateBy { it.id }
    return chapterSequence(context.chapters, context.current.key, order).chapters.map { chapter ->
        val id = OnlineIdentity.id(context.source, context.title, chapter)
        DetailChapter(id, chapter.title, chapter, byId[id])
    }
}

internal fun chapterResumeId(rows: List<DetailChapter>, currentId: String): String? =
    rows.filter { (it.progress?.lastRead ?: 0) > 0 }.maxByOrNull { it.progress!!.lastRead }?.id
        ?: rows.firstOrNull { it.id == currentId }?.id ?: rows.firstOrNull()?.id

/** A selection is restorable only after Details has actually yielded to the reader and resumed. */
internal data class ChapterFocusReturn(val pendingId: String? = null, val departed: Boolean = false,
    val restoreId: String? = null) {
    fun readerOpening(id: String) = ChapterFocusReturn(pendingId = id)
    fun paused() = if (pendingId != null) copy(departed = true) else this
    fun resumed() = if (departed) ChapterFocusReturn(restoreId = pendingId) else this
    fun consumed() = copy(restoreId = null)
}

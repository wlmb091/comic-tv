package com.pelican.comictv.reader

fun chapterEndShortcutVisible(state: ReaderState, hasChapterNavigation: Boolean, pageReady: Boolean,
    lastVisiblePage: Int = state.page): Boolean = hasChapterNavigation && pageReady &&
    state.pageCount > 0 && state.page in 0 until state.pageCount &&
    lastVisiblePage in state.page until state.pageCount && lastVisiblePage == state.pageCount - 1 &&
    state.mode == ReaderMode.Reading && state.zoom == 1f

/** This only requests focus. Chapter navigation still requires explicit button selection. */
fun focusChapterEndShortcut(state: ReaderState, action: ReaderAction, visible: Boolean): Boolean =
    visible && state.mode == ReaderMode.Reading && state.zoom == 1f &&
        action == if (state.rtl) ReaderAction.Left else ReaderAction.Right

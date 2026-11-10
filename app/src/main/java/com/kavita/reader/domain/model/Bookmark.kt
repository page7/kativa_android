package com.kavita.reader.domain.model

/**
 * One saved page, flattened for the "My bookmarks" list.
 *
 * `page` is Kavita's **0-based** image index (`BookmarkDto.page`): the web reader posts its
 * 0-based `pageNum` when bookmarking, and `Reader/image?page=` / `Image/bookmark?pageNum=` take
 * the same index. Display adds one; the reader's initial page index uses it as-is. Keeping the
 * raw value here means the conversion happens in exactly one place per consumer.
 */
data class Bookmark(
    val id: Int,
    val page: Int,
    val chapterId: Int,
    val volumeId: Int,
    val seriesId: Int,
    val seriesName: String,
    val chapterTitle: String?,
    val imageOffset: Int = 0,
    val xPath: String? = null,
) {
    /** 0-based page index, clamped so a malformed row cannot produce a negative index. */
    val pageIndex: Int get() = page.coerceAtLeast(0)

    /** 1-based page number for display (`BookmarkDto.page` is 0-based). */
    val displayPage: Int get() = pageIndex + 1
}

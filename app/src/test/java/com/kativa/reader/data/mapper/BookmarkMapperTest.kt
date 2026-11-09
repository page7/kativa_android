package com.kativa.reader.data.mapper

import com.kativa.reader.data.remote.dto.BookmarkDto
import com.kativa.reader.data.remote.dto.SeriesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bookmark page numbers are a favourite place for off-by-one bugs.
 *
 * Kavita's web reader posts its **0-based** `pageNum` when bookmarking, and both
 * `Reader/image?page=` and `Image/bookmark?pageNum=` address the same index. The reader UI must
 * therefore treat `BookmarkDto.page` as an index, and add one only for display.
 */
class BookmarkMapperTest {

    @Test
    fun `bookmark page is a zero based index`() {
        val bookmark = ContentMapper.bookmark(BookmarkDto(id = 1, page = 0, chapterId = 9))

        assertEquals(0, bookmark.pageIndex)
        assertEquals(1, bookmark.displayPage)
    }

    @Test
    fun `display page is one past the index`() {
        val bookmark = ContentMapper.bookmark(BookmarkDto(id = 2, page = 7, chapterId = 9))

        assertEquals(7, bookmark.pageIndex)
        assertEquals(8, bookmark.displayPage)
    }

    @Test
    fun `negative page cannot produce a negative index`() {
        val bookmark = ContentMapper.bookmark(BookmarkDto(id = 3, page = -4, chapterId = 9))

        assertEquals(0, bookmark.pageIndex)
        assertEquals(1, bookmark.displayPage)
    }

    /**
     * `POST Reader/all-bookmarks` is the only endpoint that embeds the series, and the bookmark
     * list has nothing else to name a card with.
     */
    @Test
    fun `series name prefers the localized title and falls back to name`() {
        val localized = ContentMapper.bookmark(
            BookmarkDto(seriesId = 5, series = SeriesDto(id = 5, name = "Berserk", localizedName = "烙印勇士")),
        )
        assertEquals("烙印勇士", localized.seriesName)

        val plain = ContentMapper.bookmark(
            BookmarkDto(seriesId = 5, series = SeriesDto(id = 5, name = "Berserk", localizedName = " ")),
        )
        assertEquals("Berserk", plain.seriesName)
    }

    @Test
    fun `missing series and blank chapter title degrade to empty values`() {
        val bookmark = ContentMapper.bookmark(BookmarkDto(seriesId = 5, chapterTitle = "  "))

        assertEquals("", bookmark.seriesName)
        assertNull(bookmark.chapterTitle)
    }
}

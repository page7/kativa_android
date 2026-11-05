package com.kativa.reader.data.mapper

import com.kativa.reader.data.remote.dto.ChapterDto
import com.kativa.reader.data.remote.dto.ProgressDto
import com.kativa.reader.data.remote.dto.VolumeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single most dangerous conversion in the app: Kavita's 1-based `pageNum` vs the reader's
 * 0-based page index. Every branch of [ContentMapper.resumePageNum] is pinned here
 * (AGENTS.md §4.6 items 1–2).
 */
class ContentMapperTest {

    private fun progress(pageNum: Int) = ProgressDto(
        chapterId = 1,
        pageNum = pageNum,
        seriesId = 2,
        volumeId = 3,
        libraryId = 4,
    )

    @Test
    fun `no progress starts at page 1`() {
        assertEquals(1, ContentMapper.resumePageNum(progress(0), totalPages = 100))
    }

    @Test
    fun `mid chapter resumes on the same page`() {
        assertEquals(37, ContentMapper.resumePageNum(progress(37), totalPages = 100))
    }

    @Test
    fun `last page is not skipped`() {
        // pageNum == pages means "last page shown, not finished" — resume there.
        assertEquals(100, ContentMapper.resumePageNum(progress(100), totalPages = 100))
    }

    @Test
    fun `finished chapter restarts from page 1`() {
        // Kavita stores pages + 1 once a chapter is completed.
        assertEquals(1, ContentMapper.resumePageNum(progress(101), totalPages = 100))
    }

    @Test
    fun `stale progress beyond the chapter restarts`() {
        assertEquals(1, ContentMapper.resumePageNum(progress(500), totalPages = 100))
    }

    @Test
    fun `single page chapter handles completion`() {
        assertEquals(1, ContentMapper.resumePageNum(progress(0), totalPages = 1))
        assertEquals(1, ContentMapper.resumePageNum(progress(1), totalPages = 1))
        assertEquals(1, ContentMapper.resumePageNum(progress(2), totalPages = 1))
    }

    @Test
    fun `zero page chapter never divides by zero`() {
        assertEquals(1, ContentMapper.resumePageNum(progress(10), totalPages = 0))
    }

    @Test
    fun `chapters are ordered by sortOrder not by number string`() {
        val volume = VolumeDto(
            id = 1,
            number = 1,
            pages = 30,
            chapters = listOf(
                chapter(id = 3, number = "10", sortOrder = 10f),
                chapter(id = 1, number = "2", sortOrder = 2f),
                chapter(id = 2, number = "1", sortOrder = 1f),
            ),
        )
        val mapped = ContentMapper.volume(volume)
        assertEquals(listOf(2, 1, 3), mapped.chapters.map { it.id })
    }

    @Test
    fun `chapter read state follows pagesRead`() {
        val unread = ContentMapper.chapter(chapter(id = 1, number = "1", sortOrder = 1f, pages = 20, pagesRead = 0))
        val partial = ContentMapper.chapter(chapter(id = 2, number = "2", sortOrder = 2f, pages = 20, pagesRead = 19))
        val read = ContentMapper.chapter(chapter(id = 3, number = "3", sortOrder = 3f, pages = 20, pagesRead = 20))

        assertFalse(unread.isRead)
        assertFalse(partial.isRead)
        assertTrue(read.isRead)
    }

    @Test
    fun `volume progress is clamped`() {
        val volume = ContentMapper.volume(
            VolumeDto(id = 1, number = 1, pages = 100, pagesRead = 150, chapters = emptyList()),
        )
        assertEquals(1f, volume.progress, 0.0001f)
    }

    @Test
    fun `chapter title falls back to chapter number`() {
        val c = ContentMapper.chapter(
            ChapterDto(id = 1, number = "7", sortOrder = 7f, pages = 10, title = "", titleName = ""),
        )
        assertEquals("Ch. 7", c.displayTitle)
    }

    private fun chapter(
        id: Int,
        number: String,
        sortOrder: Float,
        pages: Int = 10,
        pagesRead: Int = 0,
    ) = ChapterDto(
        id = id,
        number = number,
        sortOrder = sortOrder,
        pages = pages,
        pagesRead = pagesRead,
    )
}

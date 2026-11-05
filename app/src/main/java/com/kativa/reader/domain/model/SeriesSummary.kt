package com.kativa.reader.domain.model

/** Lightweight series projection used by grids and cards. */
data class SeriesSummary(
    val id: Int,
    val name: String,
    val libraryId: Int,
    val libraryName: String,
    val format: ContentFormat,
    val pages: Int,
    val pagesRead: Int,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
) {
    val progress: Float
        get() = if (pages <= 0) 0f else (pagesRead.toFloat() / pages).coerceIn(0f, 1f)

    val hasProgress: Boolean get() = pagesRead > 0
}

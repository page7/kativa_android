package com.kavita.reader.domain.model

/** Kawita `MangaFormat` values (0 Image / 1 Archive / 2 Unknown / 3 Epub / 4 Pdf). */
enum class ContentFormat {
    Image,
    Archive,
    Unknown,
    Epub,
    Pdf,
    ;

    /** Which reader implementation this format needs. */
    val reader: ReaderKind
        get() = when (this) {
            Epub -> ReaderKind.Epub
            Pdf -> ReaderKind.Pdf
            else -> ReaderKind.Manga
        }

    companion object {
        fun from(value: Int): ContentFormat = when (value) {
            0 -> Image
            1 -> Archive
            3 -> Epub
            4 -> Pdf
            else -> Unknown
        }
    }
}

enum class ReaderKind { Manga, Epub, Pdf }

/** `LayoutMode` — 1 Single, 2 Double, 3 DoubleReversed. */
enum class PageLayout(val apiValue: Int) {
    Single(1),
    Double(2),
    DoubleReversed(3),
    ;

    companion object {
        fun from(value: Int): PageLayout = entries.firstOrNull { it.apiValue == value } ?: Single
    }
}

/** `ReaderMode` — 0 LeftRight, 1 UpDown, 2 Webtoon. */
enum class ReaderMode(val apiValue: Int) {
    LeftRight(0),
    UpDown(1),
    Webtoon(2),
    ;

    companion object {
        fun from(value: Int): ReaderMode = entries.firstOrNull { it.apiValue == value } ?: LeftRight
    }
}

/** `ReadingDirection` — 0 LTR, 1 RTL. */
enum class ReadingDirection(val apiValue: Int) {
    LeftToRight(0),
    RightToLeft(1),
    ;

    companion object {
        fun from(value: Int): ReadingDirection = entries.firstOrNull { it.apiValue == value } ?: LeftToRight
    }
}

/** `ScalingOption` — 0 FitToHeight, 1 FitToWidth, 2 Original, 3 Automatic. */
enum class ScalingOption(val apiValue: Int) {
    FitToHeight(0),
    FitToWidth(1),
    Original(2),
    Automatic(3),
    ;

    companion object {
        fun from(value: Int): ScalingOption = entries.firstOrNull { it.apiValue == value } ?: Automatic
    }
}

/** `PageSplitOption` — 0 SplitLTR, 1 SplitRTL, 2 NoSplit, 3 FitSplit. */
enum class PageSplit(val apiValue: Int) {
    SplitLeftToRight(0),
    SplitRightToLeft(1),
    NoSplit(2),
    FitSplit(3),
    ;

    companion object {
        fun from(value: Int): PageSplit = entries.firstOrNull { it.apiValue == value } ?: NoSplit
    }
}

/** `PublicationStatus` — 0 OnGoing, 1 Hiatus, 2 Completed, 3 Cancelled, 4 Ended. */
enum class PublicationStatus {
    OnGoing,
    Hiatus,
    Completed,
    Cancelled,
    Ended,
    Unknown,
    ;

    companion object {
        fun from(value: Int): PublicationStatus = when (value) {
            0 -> OnGoing
            1 -> Hiatus
            2 -> Completed
            3 -> Cancelled
            4 -> Ended
            else -> Unknown
        }
    }
}

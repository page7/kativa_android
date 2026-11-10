package com.kavita.reader.ui.reader

import com.kavita.reader.core.settings.StoredReaderSettings
import com.kavita.reader.domain.model.PageLayout
import com.kavita.reader.domain.model.ReaderMode
import com.kavita.reader.domain.model.ReadingDirection
import com.kavita.reader.domain.model.ScalingOption

/**
 * The single translation point between the persisted [StoredReaderSettings] (numeric, no Compose)
 * and the renderer's [ReaderSettings].
 *
 * Two rules live here so they can be unit-tested without Android:
 *  - [brightness] is **never** persisted or restored: it is an in-session, environment-dependent
 *    value ("亮度（仅本次阅读）" is what the sheet promises).
 *  - an unknown/missing stored number falls back to the reader's own default instead of throwing.
 */
object ReaderSettingsPersistence {

    /**
     * Collapses the stored value into a render-time one. [current] supplies the values that are not
     * persisted (brightness), so toggling an enum never resets the user's dimming mid-session.
     */
    fun toRenderModel(
        stored: StoredReaderSettings,
        current: ReaderSettings = ReaderSettings.Default,
    ): ReaderSettings = ReaderSettings(
        readingDirection = ReadingDirection.from(stored.readingDirection),
        readerMode = ReaderMode.from(stored.readerMode),
        layoutMode = PageLayout.from(stored.layoutMode),
        scalingOption = ScalingOption.from(stored.scalingOption),
        coverOffset = stored.coverOffset,
        backgroundColor = stored.backgroundColor,
        brightness = current.brightness,
        showPageNumber = stored.showPageNumber,
        volumeKeyPaging = stored.volumeKeyPaging,
        forceLandscape = stored.forceLandscape,
    )

    /** The persistable projection of a live settings object. Brightness is dropped by design. */
    fun toStored(settings: ReaderSettings): StoredReaderSettings = StoredReaderSettings(
        readingDirection = settings.readingDirection.apiValue,
        readerMode = settings.readerMode.apiValue,
        layoutMode = settings.layoutMode.apiValue,
        scalingOption = settings.scalingOption.apiValue,
        coverOffset = settings.coverOffset,
        backgroundColor = settings.backgroundColor,
        showPageNumber = settings.showPageNumber,
        volumeKeyPaging = settings.volumeKeyPaging,
        forceLandscape = settings.forceLandscape,
    )
}

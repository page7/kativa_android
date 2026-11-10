package com.kavita.reader.ui.reader

import androidx.compose.ui.layout.ContentScale
import com.kavita.reader.domain.model.ScalingOption

/**
 * Maps the `ScalingOption` reading-profile value onto a Compose [ContentScale].
 *
 * This used to be a dead control: the sheet stored `scalingOption`, the ViewModel wrote it, and
 * nothing ever read it — every page was hard-coded to `Fit`. Mapping it in one place keeps the
 * single-page and double-page branches from drifting apart, and makes the translation unit-testable
 * without inflating a composable.
 */
object ReaderScaling {

    /**
     * - `FitToHeight` → [ContentScale.Fit]. Fits the whole page inside the viewport, which is what
     *   "fit height" means for a portrait page in a portrait window and never crops artwork.
     * - `FitToWidth` → [ContentScale.FillWidth]. Fills the width and allows vertical overflow,
     *   the usual choice for tall strips.
     * - `Original` → [ContentScale.None]. No resampling; the page is drawn at its intrinsic size
     *   and can extend beyond the viewport (users pan/pinch or scroll).
     * - `Automatic` is the server's "let the client decide" value; treat it as `Fit`.
     */
    fun contentScale(option: ScalingOption): ContentScale = when (option) {
        ScalingOption.FitToHeight -> ContentScale.Fit
        ScalingOption.FitToWidth -> ContentScale.FillWidth
        ScalingOption.Original -> ContentScale.None
        ScalingOption.Automatic -> ContentScale.Fit
    }
}

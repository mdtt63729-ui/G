package com.gitofy.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource

/**
 * PRD §20 — Configurable Gito home icon.
 *
 * The Home screen's "Gito" quick-action icon used to be a single hardcoded
 * [ImageVector] passed directly into the card composable. This sealed type
 * lets that icon be swapped for a VectorDrawable resource, a raster
 * PNG/WebP drawable resource, or a bundled custom [ImageBitmap] — without
 * touching the call site's layout code. The current app icon becomes the
 * [Vector] default; nothing about how it renders today changes.
 *
 * Deliberately does NOT support loading an arbitrary remote/user-supplied
 * image URL (PRD §20 explicitly excludes that as unnecessary).
 */
sealed interface GitoIconAsset {
    /** A Compose [ImageVector], e.g. a Material icon or a hand-authored vector. */
    data class Vector(val imageVector: ImageVector) : GitoIconAsset

    /** A drawable resource — covers both `vector` XML drawables and PNG/WebP assets. */
    data class Drawable(@DrawableRes val resId: Int) : GitoIconAsset

    /** A pre-decoded bitmap, e.g. bundled custom artwork loaded once and cached by the caller. */
    data class Bitmap(val bitmap: ImageBitmap) : GitoIconAsset
}

/** Renders any [GitoIconAsset] uniformly, tinted the same way [Icon] would be. */
@Composable
fun GitoConfigurableIcon(
    asset: GitoIconAsset,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    when (asset) {
        is GitoIconAsset.Vector -> Icon(
            imageVector = asset.imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
        is GitoIconAsset.Drawable -> Icon(
            painter = painterResource(id = asset.resId),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
        is GitoIconAsset.Bitmap -> Image(
            bitmap = asset.bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint)
        )
    }
}

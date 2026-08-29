package com.gitofy.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * GITOFY Material 3 Shapes — PRD §8.
 * Reference: soft rounded geometry, cards 20-24dp, buttons 18-22dp.
 * No sharp rectangular cards.
 */
val GITOFYShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

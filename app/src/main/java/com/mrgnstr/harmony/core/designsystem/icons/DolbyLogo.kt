package com.mrgnstr.harmony.core.designsystem.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The Dolby double-D from Simple Icons (https://simpleicons.org, CC0), viewBox 24 x 24; the mark itself spans y 3.564 .. 20.436.
// Dolby and the double-D symbol are trademarks of Dolby Laboratories; it only marks Dolby formats here.
private const val DolbyPath =
    "M0 3.564v16.872h2.488c4.648 0 8.438-3.788 8.438-8.436s-3.79-8.436-8.438-8.436H0zm21.512 0c-4.648 0-8.438 3.788-8.438 8.436s3.79 8.436 8.438 8.436H24V3.564h-2.488z"
private const val MarkTop = 3.564f
private const val MarkHeight = 16.872f
private const val MarkWidth = 24f

/** The Dolby double-D mark, [size] tall (a glyph, not a font: fonts get replaced on some devices). */
@Composable
fun DolbyLogo(modifier: Modifier = Modifier, size: Dp = 18.dp, tint: Color = LocalContentColor.current) {
    val path = remember { PathParser().parsePathString(DolbyPath).toPath() }
    Canvas(modifier.size(size * (MarkWidth / MarkHeight), size)) {
        val scale = this.size.height / MarkHeight
        drawContext.canvas.withSave {
            drawContext.canvas.translate(0f, -MarkTop * scale)
            drawContext.canvas.scale(scale, scale)
            drawPath(path, tint)
        }
    }
}

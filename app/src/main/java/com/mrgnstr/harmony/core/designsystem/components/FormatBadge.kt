package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.icons.DolbyLogo
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.FormatIcon

/** The glyph a format tag wears (album header tags, Now Playing's pill). */
@Composable
fun FormatGlyph(icon: FormatIcon, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val color = tint.takeIf { it != Color.Unspecified } ?: androidx.compose.material3.LocalContentColor.current
    when (icon) {
        FormatIcon.Dolby -> DolbyLogo(modifier, size = 12.dp, tint = color)
        FormatIcon.Surround -> SymbolIcon(Symbols.SurroundSound, null, modifier, size = 18.dp, tint = color)
        FormatIcon.Lossless -> SymbolIcon(Symbols.Hd, null, modifier, size = 18.dp, tint = color)
        FormatIcon.Lossy -> SymbolIcon(Symbols.MusicNote, null, modifier, size = 18.dp, tint = color)
    }
}

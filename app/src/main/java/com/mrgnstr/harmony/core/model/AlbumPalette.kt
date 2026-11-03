package com.mrgnstr.harmony.core.model

/** Three artwork-derived ARGB colours, computed once per album. [hasArt] false means "looked, found nothing". */
data class AlbumPalette(
    val albumId: Long,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val hasArt: Boolean,
)

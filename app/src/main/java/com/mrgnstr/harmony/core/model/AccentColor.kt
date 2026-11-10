package com.mrgnstr.harmony.core.model

/**
 * The app's accent: the wallpaper's colours (Material You, API 31+; a fixed purple below), or a preset that replaces
 * them everywhere a page does not bring its own colour. [seed] is what the colour engine builds the scheme from.
 * The launcher icons come in the same presets (`tools/app_icons/gen_m3.py` ACCENTS: keep the two lists the same).
 */
enum class AccentColor(val label: String, val seed: Int?) {
    Wallpaper("Wallpaper", null),
    Red("Red", 0xFFE5484D.toInt()),
    Orange("Orange", 0xFFF0803C.toInt()),
    Yellow("Yellow", 0xFFE0B000.toInt()),
    Green("Green", 0xFF3FA34D.toInt()),
    Teal("Teal", 0xFF1E9E94.toInt()),
    Blue("Blue", 0xFF3B7DE9.toInt()),
    Purple("Purple", 0xFF7A5AF8.toInt()),
    Pink("Pink", 0xFFE0559A.toInt()),
}

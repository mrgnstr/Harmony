package com.mrgnstr.harmony.core.model

/** Which theme the app uses: follow the system, or force light or dark (older Android versions have no system dark mode). */
enum class ThemeMode(val label: String) { System("Automatic"), Light("Light"), Dark("Dark") }

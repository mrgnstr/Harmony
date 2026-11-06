#!/usr/bin/env python3
"""
Material Symbols (Rounded) for the app, as outlines rather than a font: some systems (HyperOS on the 11T) replace the fonts
apps draw text with, which turned every icon into a missing-glyph box. Writes

  app/src/main/res/raw/symbols.bin                  glyph outlines of the icons in ICONS
  .../core/designsystem/icons/Symbols.kt             name -> codepoint

Each glyph is stored at six instances of the variable font: FILL 0/1 x wght 100/400/700 (GRAD 0, opsz 24). The instances are
point-compatible (a variable font only moves points), so the app interpolates fill and weight itself on every API level.

symbols.bin (little endian): "SYM1", u16 count, then per glyph: u16 codepoint, u16 points, u16 contours,
u16[contours] last point of each contour, u8[points] on-curve flags, then 6 x points x (i16 x, i16 y) in the order
(fill 0: wght 100, 400, 700), (fill 1: wght 100, 400, 700). Units: 960 per em, y up from the baseline.

Source: icons/MaterialSymbolsRounded.ttf + .codepoints (github.com/google/material-design-icons, variablefont/).
Run: python3 -I tools/gen_symbols.py   (needs fontTools)
"""
import os
import struct
import sys

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

# Symbol name in Kotlin -> Material Symbols name. Add a line and re-run to use a new icon.
ICONS = {
    "Home": "home",
    "Library": "library_music",
    "Search": "search",
    "Play": "play_arrow",
    "Pause": "pause",
    "SkipNext": "skip_next",
    "SkipPrevious": "skip_previous",
    "Shuffle": "shuffle",
    "Repeat": "repeat",
    "RepeatOne": "repeat_one",
    "MusicNote": "music_note",
    "Album": "album",
    "Person": "person",
    "Artist": "artist",
    "QueueMusic": "queue_music",
    "Settings": "settings",
    "MoreVert": "more_vert",
    "Favorite": "favorite",
    "Star": "star",
    "Close": "close",
    "ArrowBack": "arrow_back",
    "Check": "check",
    "Add": "add",
    "Sort": "sort",
    "Lyrics": "lyrics",
    "ExpandMore": "keyboard_arrow_down",
    "ChevronRight": "chevron_right",
    "History": "history",
    "Palette": "palette",
    "Tune": "tune",
    "Folder": "folder",
    "PlaylistAdd": "playlist_add",
    "Delete": "delete",
    "Edit": "edit",
    "Image": "image",
    "Info": "info",
    "VolumeUp": "volume_up",
    "VolumeMute": "volume_mute",
    "GraphicEq": "graphic_eq",
    "Genres": "category",
    "PlayNext": "queue_play_next",
    "DragHandle": "drag_handle",
    "Refresh": "refresh",
    "DarkMode": "dark_mode",
    "LightMode": "light_mode",
    "GridView": "grid_view",
    "ListView": "list",
    "Hd": "hd",
    "SurroundSound": "surround_sound",
    "Cloud": "cloud",
    "CloudDone": "cloud_done",
    "CloudOff": "cloud_off",
    "CloudSync": "cloud_sync",
    "Dns": "dns",
    "Storage": "storage",
    "Download": "download",
    "DownloadDone": "download_done",
    "Downloading": "downloading",
    "OfflinePin": "offline_pin",
    "Share": "share",
    "Link": "link",
    "Public": "public",
    "Lock": "lock",
    "Radio": "radio",
    "Bookmark": "bookmark",
    "BookmarkAdd": "bookmark_add",
    "Speaker": "speaker",
    "Devices": "devices",
    "AutoAwesome": "auto_awesome",
    "Bolt": "bolt",
    "Translate": "translate",
    "Key": "key",
    "AdminPanelSettings": "admin_panel_settings",
    "FilterList": "filter_list",
    "Equalizer": "equalizer",
    "Speed": "speed",
    "CheckCircle": "check_circle",
    "Error": "error",
    "Wifi": "wifi",
}

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "icons", "MaterialSymbolsRounded.ttf")
CODEPOINTS = os.path.join(ROOT, "icons", "MaterialSymbolsRounded.codepoints")
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw", "symbols.bin")
KT = os.path.join(ROOT, "app", "src", "main", "java", "com", "mrgnstr", "harmony", "core", "designsystem", "icons", "Symbols.kt")


def main() -> None:
    points = {}
    with open(CODEPOINTS) as f:
        for line in f:
            name, cp = line.split()
            points[name] = int(cp, 16)
    missing = [n for n in ICONS.values() if n not in points]
    if missing:
        sys.exit(f"not in the font: {missing}")

    font = TTFont(SRC)
    cmap = font.getBestCmap()
    instances = [
        instancer.instantiateVariableFont(TTFont(SRC), {"FILL": fill, "wght": wght, "GRAD": 0, "opsz": 24})
        for fill in (0, 1) for wght in (100, 400, 700)
    ]
    out = bytearray(b"SYM1") + struct.pack("<H", len(ICONS))
    for name in ICONS.values():
        cp = points[name]
        glyphs = [inst["glyf"][cmap[cp]] for inst in instances]
        base = glyphs[0]
        if base.isComposite():
            sys.exit(f"{name}: composite glyph, not supported")
        n = len(base.coordinates)
        for g in glyphs[1:]:
            if len(g.coordinates) != n or list(g.endPtsOfContours) != list(base.endPtsOfContours):
                sys.exit(f"{name}: instances are not point-compatible")
        out += struct.pack("<HHH", cp, n, len(base.endPtsOfContours))
        out += struct.pack(f"<{len(base.endPtsOfContours)}H", *base.endPtsOfContours)
        out += bytes(f & 1 for f in base.flags)
        for g in glyphs:
            for x, y in g.coordinates:
                out += struct.pack("<hh", round(x), round(y))
    os.makedirs(os.path.dirname(RAW), exist_ok=True)
    with open(RAW, "wb") as f:
        f.write(out)

    os.makedirs(os.path.dirname(KT), exist_ok=True)
    with open(KT, "w") as f:
        f.write("// Generated by tools/gen_symbols.py, do not edit.\n")
        f.write("package com.mrgnstr.harmony.core.designsystem.icons\n\n")
        f.write("/** Material Symbols Rounded codepoints; draw with [SymbolIcon]. */\n")
        f.write("object Symbols {\n")
        for kt, name in ICONS.items():
            f.write(f"    val {kt} = Symbol('\\u{points[name]:04X}')\n")
        f.write("}\n")
    print(f"{len(ICONS)} icons; symbols.bin {len(out) // 1024} KB")


if __name__ == "__main__":
    main()

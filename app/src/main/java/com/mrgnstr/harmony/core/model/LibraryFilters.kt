package com.mrgnstr.harmony.core.model

/** Filters the MediaStore index only; files and user data are never deleted. */
data class LibraryFilters(val minimumDurationMs: Long = 30_000L, val excludedFolders: Set<String> = emptySet()) {
    fun excludes(path: String?): Boolean = path != null && excludedFolders.any { folder ->
        path.startsWith(folder.trimEnd('/') + "/")
    }
}

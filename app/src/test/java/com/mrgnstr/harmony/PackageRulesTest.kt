package com.mrgnstr.harmony

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the single module splittable: enforces the dependency direction between packages. */
class PackageRulesTest {
    private val root = File("src/main/java/com/mrgnstr/harmony")
    private val base = "com.mrgnstr.harmony"

    private fun sources() = root.walkTopDown().filter { it.extension == "kt" }.toList()

    private fun importsOf(file: File) =
        file.readLines().filter { it.startsWith("import $base.") }.map { it.removePrefix("import ") }

    /** First path segment(s) below the root: "feature.home", "core.model", "app". */
    private fun unit(rel: String): String {
        val parts = rel.split('.', '/')
        return if (parts[0] == "app") "app" else parts.take(2).joinToString(".")
    }

    @Test
    fun coreNeverImportsFeatureOrApp() {
        val bad = sources().filter { it.relativeTo(root).path.startsWith("core") }
            .flatMap { f -> importsOf(f).filter { it.removePrefix("$base.").let { r -> r.startsWith("feature") || r.startsWith("app") } }.map { "${f.name} -> $it" } }
        assertTrue("core must not depend on feature/app: $bad", bad.isEmpty())
    }

    @Test
    fun featuresNeverImportOtherFeatures() {
        val bad = sources().filter { it.relativeTo(root).path.startsWith("feature") }.flatMap { f ->
            val self = unit(f.relativeTo(root).path.replace(File.separatorChar, '/'))
            importsOf(f).filter { imp ->
                val r = imp.removePrefix("$base.")
                r.startsWith("feature") && unit(r) != self || r.startsWith("app")
            }.map { "${f.name} -> $it" }
        }
        assertTrue("features must not depend on other features/app: $bad", bad.isEmpty())
    }
}

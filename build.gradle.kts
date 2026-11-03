plugins {
    alias(libs.plugins.android.application) apply false
    // Declared (not applied) so the 2.4 compiler overrides AGP's bundled Kotlin.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

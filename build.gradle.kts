// ============================================================
// build.gradle.kts (Project-level)
// ============================================================
// Declara qué plugins EXISTEN en el proyecto.
// "apply false" = los hace disponibles pero no los activa acá.
// Cada módulo (app/) decide cuáles activar.
// ============================================================

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
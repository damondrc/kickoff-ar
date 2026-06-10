// ============================================================
// settings.gradle.kts
// ============================================================
// Configura de dónde Gradle descarga plugins y dependencias,
// y qué módulos tiene el proyecto.
// ============================================================

pluginManagement {
    // De dónde se descargan los PLUGINS de Gradle
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS = si un módulo intenta agregar su propio
    // repositorio, Gradle tira error. Esto fuerza a que TODOS los repos
    // estén centralizados acá (buena práctica).
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    // De dónde se descargan las DEPENDENCIAS (librerías)
    repositories {
        google()
        mavenCentral()
    }
}

// Nombre del proyecto (aparece en Android Studio)
rootProject.name = "Fútbol Argentino Widgets"

// Módulos incluidos (por ahora solo "app")
include(":app")

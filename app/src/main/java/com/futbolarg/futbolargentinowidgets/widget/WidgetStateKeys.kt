package com.futbolarg.futbolargentinowidgets.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// ============================================================
// WidgetStateKeys.kt
// ============================================================
// Claves del ESTADO GLANCE de cada instancia del widget.
//
// Glance le da a cada widget su propio archivo de Preferences
// (separado de nuestro DataStore de configuración). Cuando
// WidgetUpdater escribe estas claves con updateAppWidgetState
// y llama a update(), Glance RECOMPONE el contenido con los
// valores nuevos.
//
// LECCIÓN APRENDIDA (bug de la primera versión): el código que
// está antes de provideContent en provideGlance corre UNA sola
// vez, al crearse el widget. Si los datos se leen ahí, el
// widget queda congelado con esos valores. La forma correcta
// es leer TODO desde currentState() adentro de la composición.
// ============================================================

object WidgetStateKeys {

    // ¿Esta instancia ya tiene equipo configurado?
    val CONFIGURED = booleanPreferencesKey("configured")

    // Nombre del equipo seguido (para el estado "sin datos")
    val TEAM_NAME = stringPreferencesKey("team_name")

    // Línea central ya formateada: "RIV vs BOC" o "RIV 2 - 1 BOC"
    val LINE_MAIN = stringPreferencesKey("line_main")

    // Línea de estado ya formateada: "dom 26 jul · 18:30",
    // "● Jugando" o "Final"
    val LINE_STATUS = stringPreferencesKey("line_status")

    // ¿Está en juego? (la línea de estado se pinta verde)
    val IS_LIVE = booleanPreferencesKey("is_live")

    // Rutas locales de los escudos ya descargados ("" si no hay)
    val HOME_LOGO_PATH = stringPreferencesKey("home_logo_path")
    val AWAY_LOGO_PATH = stringPreferencesKey("away_logo_path")

    // Partidos SIGUIENTES al principal, ya formateados
    // ("vs CAT · dom 26 jul · 16:00"). Solo los muestra el
    // widget cuando el usuario lo agranda (tamaño expandido).
    val UPCOMING_1 = stringPreferencesKey("upcoming_1")
    val UPCOMING_2 = stringPreferencesKey("upcoming_2")
}

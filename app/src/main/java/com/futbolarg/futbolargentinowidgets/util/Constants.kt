package com.futbolarg.futbolargentinowidgets.util

// ============================================================
// Constants.kt
// ============================================================
// Constantes globales de la app.
//
// FUENTE DE DATOS: API pública de ESPN (site.api.espn.com).
// - No requiere clave ni registro.
// - Devuelve fixture, estado y resultado de la Liga Profesional.
// - "arg.1" es el slug de la Liga Profesional Argentina.
// ============================================================

object Constants {

    // ========================
    // Fuente de datos
    // ========================
    // false → la app consulta ESPN directamente (4 requests/sync)
    // true  → la app consulta TU Cloudflare Worker (1 request/sync,
    //         con caché de borde compartida entre todos los usuarios)
    //
    // Para activar el proxy: despliega server/worker.js en
    // Cloudflare, pega tu URL en PROXY_BASE_URL y pon esto en true.
    const val USE_PROXY = true

    // URL del Worker desplegado (siempre con "/" final)
    const val PROXY_BASE_URL = "https://kickoff-ar.damondrc99.workers.dev/"

    // ========================
    // ESPN API (fuente directa)
    // ========================
    const val ESPN_BASE_URL = "https://site.api.espn.com/apis/site/v2/sports/soccer/"

    // Slug de la Liga Profesional Argentina en ESPN.
    // Se usa para el listado de EQUIPOS (los 30 de Primera).
    const val LEAGUE_SLUG = "arg.1"

    // Competiciones que se sincronizan (slug → nombre a mostrar).
    // Así el widget responde "¿cuándo juega Boca?" sin importar
    // si el próximo partido es de liga, copa o torneo continental.
    // El orden importa solo para logs; los partidos se mezclan
    // en Room y se ordenan por fecha.
    val LEAGUES: Map<String, String> = mapOf(
        "arg.1" to "Liga Profesional",
        "arg.copa" to "Copa Argentina",
        "conmebol.libertadores" to "Libertadores",
        "conmebol.sudamericana" to "Sudamericana"
    )

    // Ventana de sincronización del fixture:
    // miramos unos días hacia atrás (último resultado) y varios
    // hacia adelante (la liga tiene recesos largos entre torneos).
    const val SYNC_DAYS_BACK = 7L
    const val SYNC_DAYS_FORWARD = 90L

    // ========================
    // WorkManager
    // ========================
    // Nombre ÚNICO del worker de sync puntual (se reprograma solo)
    const val SYNC_WORK_NAME = "match_sync_work"
    // Nombre del sync diario de seguridad (capta cambios de fixture)
    const val DAILY_SYNC_WORK_NAME = "daily_match_sync_work"
    // Nombre de la liga doméstica (el widget omite la competición
    // solo para esta, por minimalismo)
    val DOMESTIC_LEAGUE_NAME: String = LEAGUES.getValue("arg.1")

    // ---- Polling durante el partido (adaptativo) ----
    // Fase normal: cada 30 min
    const val LIVE_POLL_MINUTES = 30L
    // Fase final: pasado este tiempo desde el kickoff...
    const val LIVE_ENDGAME_AFTER_MINUTES = 75L
    // ...consultamos cada 10 min para detectar el final más cerca
    // del pitazo real (el TTL del Worker baja a 5 min en vivo)
    const val LIVE_ENDGAME_POLL_MINUTES = 10L

    // Worker de notificación previa al partido
    const val PRE_MATCH_WORK_NAME = "pre_match_notification_work"
    // Cuántos minutos antes del kickoff avisamos
    const val PRE_MATCH_NOTIFY_MINUTES = 30L

    // ========================
    // DataStore
    // ========================
    const val PREFERENCES_NAME = "futbol_widgets_prefs"
    const val SETTINGS_NAME = "app_settings"

    // ========================
    // Notificaciones
    // ========================
    const val NOTIFICATION_CHANNEL_ID = "match_notifications"
}

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
    // ESPN API
    // ========================
    const val ESPN_BASE_URL = "https://site.api.espn.com/apis/site/v2/sports/soccer/"

    // Slug de la Liga Profesional Argentina en ESPN
    const val LEAGUE_SLUG = "arg.1"

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
    // Cada cuánto re-chequeamos mientras hay un partido en juego
    const val LIVE_POLL_MINUTES = 30L

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

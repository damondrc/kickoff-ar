package com.futbolarg.futbolargentinowidgets.domain.model

// ============================================================
// Match.kt
// ============================================================
// Modelo de dominio principal: representa un partido.
// Es el objeto que usan las pantallas y los widgets.
// No tiene anotaciones de Room ni de Gson — es "puro".
// ============================================================

data class Match(
    val id: Long,

    // Equipo local
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamAbbr: String,     // "RIV" — lo muestra el widget
    val homeTeamLogo: String,

    // Equipo visitante
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamAbbr: String,
    val awayTeamLogo: String,

    // Inicio del partido en milisegundos epoch (UTC).
    // Lo usamos para ordenar, comparar con "ahora" y formatear
    // en hora local del teléfono.
    val kickoffMillis: Long,

    // Competición ("Liga Profesional", "Copa Argentina", etc.)
    val leagueName: String,

    // Fase del torneo ("Torneo Clausura", "Round of 32"...)
    val phase: String = "",

    val status: MatchStatus,
    val homeScore: Int?,          // null si no empezó
    val awayScore: Int?           // null si no empezó
) {
    // ¿Este equipo juega de local en este partido?
    fun isHome(teamId: Int): Boolean = homeTeamId == teamId

    // Nombre del rival desde la perspectiva de teamId
    fun rivalName(teamId: Int): String =
        if (isHome(teamId)) awayTeamName else homeTeamName
}

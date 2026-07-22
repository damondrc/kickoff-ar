package com.futbolarg.futbolargentinowidgets.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// ============================================================
// MatchEntity.kt
// ============================================================
// Tabla "matches" de Room. Cada propiedad = una columna.
//
// CAMBIOS vs. versión anterior (versión 2 del esquema):
// - kickoffMillis: epoch UTC en milisegundos. Permite ordenar y
//   comparar con System.currentTimeMillis() directamente en SQL.
// - homeTeamAbbr / awayTeamAbbr: abreviaturas ("RIV") que
//   muestra el widget.
// - "status" guarda el NOMBRE de nuestro enum ("SCHEDULED"),
//   no códigos de la API.
// ============================================================

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey
    val id: Long,

    // Equipo local
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamAbbr: String,
    val homeTeamLogo: String,

    // Equipo visitante
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamAbbr: String,
    val awayTeamLogo: String,

    // Inicio del partido (epoch millis, UTC)
    val kickoffMillis: Long,

    // Competición a la que pertenece ("Liga Profesional",
    // "Copa Argentina", "Libertadores", "Sudamericana")
    val leagueName: String,

    // Fase dentro de la competición: "Torneo Clausura",
    // "Round of 32"... Es lo que ESPN sí publica. El NÚMERO de
    // fecha de liga no existe en la API y se decidió NO
    // derivarlo: la heurística resultaba imprecisa y es
    // preferible agrupar por el día real de juego.
    val phase: String = "",

    // Nombre del enum MatchStatus (ej: "SCHEDULED", "FINISHED")
    val status: String,

    // Marcador (null si no empezó)
    val homeScore: Int?,
    val awayScore: Int?,

    // Cuándo se actualizó este registro (para limpieza/diagnóstico)
    val lastUpdated: Long = System.currentTimeMillis()
)

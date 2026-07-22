package com.futbolarg.futbolargentinowidgets.data.remote.dto

import com.google.gson.annotations.SerializedName

// ============================================================
// EspnScoreboardDto.kt
// ============================================================
// DTOs para el endpoint "scoreboard" de la API pública de ESPN:
//   GET soccer/arg.1/scoreboard?dates=YYYYMMDD-YYYYMMDD&limit=1000
//
// Estructura (solo los campos que usamos):
// {
//   "events": [{
//     "id": "401873392",
//     "date": "2026-05-24T18:30Z",          ← UTC
//     "competitions": [{
//       "status": { "type": { "name": "STATUS_FULL_TIME",
//                              "state": "post",
//                              "completed": true } },
//       "competitors": [{
//         "homeAway": "home",
//         "score": "2",                      ← String en este endpoint
//         "team": { "id": "16", "displayName": "River Plate",
//                   "abbreviation": "RIV", "logo": "https://..." }
//       }, { ...visitante... }]
//     }]
//   }]
// }
//
// IMPORTANTE: el "shortName" de ESPN es "BEL @ RIV" (visitante
// primero). NUNCA lo usamos para deducir local/visitante:
// usamos el campo homeAway de cada competitor.
// ============================================================

data class EspnScoreboardResponse(
    @SerializedName("events")
    val events: List<EspnEventDto> = emptyList()
)

data class EspnEventDto(
    // Nullables a propósito: Gson puede dejar null campos
    // ausentes aunque Kotlin los declare no-nulos (no valida
    // en runtime). El mapper descarta eventos incompletos.
    @SerializedName("id")
    val id: String?,

    // Fecha/hora del partido en UTC, formato "2026-05-24T18:30Z"
    @SerializedName("date")
    val date: String?,

    // Fase del torneo a la que pertenece el evento
    @SerializedName("season")
    val season: EspnEventSeasonDto? = null,

    // Un evento tiene una sola "competition" (el partido en sí)
    @SerializedName("competitions")
    val competitions: List<EspnCompetitionDto> = emptyList()
)

data class EspnEventSeasonDto(
    // "torneo-clausura", "round-of-32", "apertura---final"...
    @SerializedName("slug")
    val slug: String?
)

data class EspnCompetitionDto(
    // "Copa Argentina, Round of 32" en copas; genérico en liga
    @SerializedName("altGameNote")
    val altGameNote: String? = null,

    @SerializedName("status")
    val status: EspnStatusDto?,

    @SerializedName("competitors")
    val competitors: List<EspnCompetitorDto> = emptyList()
)

data class EspnStatusDto(
    @SerializedName("type")
    val type: EspnStatusTypeDto?
)

data class EspnStatusTypeDto(
    // Ej: "STATUS_SCHEDULED", "STATUS_FIRST_HALF", "STATUS_FULL_TIME"
    @SerializedName("name")
    val name: String?,

    // "pre" (no empezó), "in" (en juego), "post" (terminó)
    @SerializedName("state")
    val state: String?,

    @SerializedName("completed")
    val completed: Boolean?
)

data class EspnCompetitorDto(
    // "home" o "away"
    @SerializedName("homeAway")
    val homeAway: String?,

    // En el scoreboard el score viene como String ("2").
    // Es "0" incluso antes de empezar, por eso al mapear solo lo
    // usamos si el partido está en juego o terminado.
    @SerializedName("score")
    val score: String?,

    @SerializedName("team")
    val team: EspnTeamDto?
)

data class EspnTeamDto(
    @SerializedName("id")
    val id: String?,

    @SerializedName("displayName")
    val displayName: String?,

    @SerializedName("abbreviation")
    val abbreviation: String?,

    // URL del escudo (en scoreboard viene como "logo" simple)
    @SerializedName("logo")
    val logo: String?
)

package com.futbolarg.futbolargentinowidgets.data.remote.dto

import com.google.gson.annotations.SerializedName

// ============================================================
// EspnTeamsDto.kt
// ============================================================
// DTOs para el endpoint de equipos de la liga:
//   GET soccer/arg.1/teams?limit=50
//
// Estructura:
// { "sports": [{ "leagues": [{ "teams": [{ "team": {...} }] }] }] }
//
// Lo usamos en la pantalla de configuración del widget para
// listar los 30 equipos de la Liga Profesional con sus escudos.
// ============================================================

data class EspnTeamsResponse(
    @SerializedName("sports")
    val sports: List<EspnSportDto> = emptyList()
) {
    // Atajo: aplana la estructura anidada y devuelve los equipos
    fun allTeams(): List<EspnTeamListItemDto> =
        sports.firstOrNull()
            ?.leagues?.firstOrNull()
            ?.teams?.mapNotNull { it.team }
            ?: emptyList()
}

data class EspnSportDto(
    @SerializedName("leagues")
    val leagues: List<EspnLeagueDto> = emptyList()
)

data class EspnLeagueDto(
    @SerializedName("teams")
    val teams: List<EspnTeamWrapperDto> = emptyList()
)

data class EspnTeamWrapperDto(
    @SerializedName("team")
    val team: EspnTeamListItemDto?
)

data class EspnTeamListItemDto(
    @SerializedName("id")
    val id: String?,

    @SerializedName("displayName")
    val displayName: String?,

    @SerializedName("abbreviation")
    val abbreviation: String?,

    // En este endpoint los escudos vienen como lista "logos"
    @SerializedName("logos")
    val logos: List<EspnLogoDto> = emptyList()
) {
    // Primer logo disponible (el "default" de ESPN)
    fun logoUrl(): String = logos.firstOrNull()?.href ?: ""
}

data class EspnLogoDto(
    @SerializedName("href")
    val href: String?
) {
    val hrefOrEmpty: String get() = href ?: ""
}

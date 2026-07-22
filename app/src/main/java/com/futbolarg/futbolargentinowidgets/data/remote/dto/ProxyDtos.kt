package com.futbolarg.futbolargentinowidgets.data.remote.dto

import com.google.gson.annotations.SerializedName

// ============================================================
// ProxyDtos.kt
// ============================================================
// DTOs para NUESTRO servidor (server/worker.js en Cloudflare).
//
// Compara con EspnScoreboardDto: aquí no hay anidamiento ni
// campos de sobra, porque el Worker ya hizo la transformación.
// El JSON es plano y usa exactamente los nombres que la app
// necesita — esa es la ventaja de controlar el servidor.
//
// Nota: "status" llega ya como nombre de nuestro enum
// ("SCHEDULED", "FINISHED"...) porque el Worker replica la
// lógica de MatchStatus.fromEspn. La app solo hace fromName().
// ============================================================

data class ProxyFixturesResponse(
    @SerializedName("updatedAt")
    val updatedAt: String?,

    @SerializedName("matches")
    val matches: List<ProxyMatchDto> = emptyList()
)

data class ProxyMatchDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("league") val league: String?,
    // Fase del torneo: "Torneo Clausura", "Round of 32"...
    @SerializedName("phase") val phase: String? = null,
    @SerializedName("kickoffMillis") val kickoffMillis: Long?,
    @SerializedName("status") val status: String?,

    @SerializedName("homeId") val homeId: Int?,
    @SerializedName("homeName") val homeName: String?,
    @SerializedName("homeAbbr") val homeAbbr: String?,
    @SerializedName("homeLogo") val homeLogo: String?,

    @SerializedName("awayId") val awayId: Int?,
    @SerializedName("awayName") val awayName: String?,
    @SerializedName("awayAbbr") val awayAbbr: String?,
    @SerializedName("awayLogo") val awayLogo: String?,

    @SerializedName("homeScore") val homeScore: Int?,
    @SerializedName("awayScore") val awayScore: Int?
)

data class ProxyTeamDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("abbr") val abbr: String?,
    @SerializedName("logo") val logo: String?,
    @SerializedName("color") val color: String?
)

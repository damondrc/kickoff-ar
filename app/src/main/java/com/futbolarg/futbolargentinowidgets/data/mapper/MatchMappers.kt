package com.futbolarg.futbolargentinowidgets.data.mapper

import com.futbolarg.futbolargentinowidgets.data.local.entity.MatchEntity
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnEventDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnTeamListItemDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.ProxyMatchDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.ProxyTeamDto
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.domain.model.MatchStatus
import com.futbolarg.futbolargentinowidgets.domain.model.Team
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ============================================================
// MatchMappers.kt
// ============================================================
// Conversión entre las 3 representaciones de un partido:
//
//   ESPN (EspnEventDto)
//        ↓ toEntity()
//   Room (MatchEntity)
//        ↓ toDomainModel()
//   UI/Widget (Match)
// ============================================================

// ------------------------------------------------------------
// Parseo de fechas de ESPN
// ------------------------------------------------------------
// ESPN devuelve "2026-05-24T18:30Z" (UTC, a veces sin segundos).
// Instant.parse exige segundos, así que probamos ambos formatos.
// ------------------------------------------------------------
private val ESPN_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

fun parseEspnDate(date: String): Long? {
    return try {
        // Formato completo con segundos: "2026-05-24T18:30:00Z"
        Instant.parse(date).toEpochMilli()
    } catch (e: Exception) {
        try {
            // Formato corto sin segundos: "2026-05-24T18:30Z"
            LocalDateTime.parse(date, ESPN_DATE_FORMAT)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        } catch (e2: Exception) {
            null
        }
    }
}

// ============================================================
// EspnEventDto → MatchEntity
// ============================================================
// Devuelve null si el evento viene incompleto (lo descartamos
// en lugar de crashear o guardar basura).
//
// leagueName: el nombre de la competición NO viene en cada
// evento del scoreboard, así que lo pasa el repositorio según
// qué liga estaba consultando.
// ============================================================
fun EspnEventDto.toEntity(leagueName: String): MatchEntity? {
    val competition = competitions.firstOrNull() ?: return null

    // Local y visitante según el campo homeAway (NUNCA por orden
    // ni por shortName, que ESPN escribe "visitante @ local")
    val home = competition.competitors.find { it.homeAway == "home" } ?: return null
    val away = competition.competitors.find { it.homeAway == "away" } ?: return null
    val homeTeam = home.team ?: return null
    val awayTeam = away.team ?: return null

    val matchId = id?.toLongOrNull() ?: return null
    val kickoff = date?.let { parseEspnDate(it) } ?: return null

    val statusType = competition.status?.type
    val status = MatchStatus.fromEspn(statusType?.state, statusType?.name)

    // ESPN manda score "0" incluso antes de empezar.
    // Solo lo tomamos como marcador real si el partido está
    // en juego o terminado.
    val scoresValid = status.isLive || status.isFinished

    return MatchEntity(
        id = matchId,
        homeTeamId = homeTeam.id?.toIntOrNull() ?: return null,
        homeTeamName = homeTeam.displayName ?: "",
        homeTeamAbbr = homeTeam.abbreviation ?: "",
        homeTeamLogo = homeTeam.logo ?: "",
        awayTeamId = awayTeam.id?.toIntOrNull() ?: return null,
        awayTeamName = awayTeam.displayName ?: "",
        awayTeamAbbr = awayTeam.abbreviation ?: "",
        awayTeamLogo = awayTeam.logo ?: "",
        kickoffMillis = kickoff,
        leagueName = leagueName,
        status = status.name,
        homeScore = if (scoresValid) home.score?.toIntOrNull() else null,
        awayScore = if (scoresValid) away.score?.toIntOrNull() else null
    )
}

// Lista de eventos → lista de entities (descarta los inválidos)
fun List<EspnEventDto>.toEntityList(leagueName: String): List<MatchEntity> {
    return mapNotNull { it.toEntity(leagueName) }
}

// ============================================================
// MatchEntity → Match (modelo de dominio)
// ============================================================
fun MatchEntity.toDomainModel(): Match {
    return Match(
        id = id,
        homeTeamId = homeTeamId,
        homeTeamName = homeTeamName,
        homeTeamAbbr = homeTeamAbbr,
        homeTeamLogo = homeTeamLogo,
        awayTeamId = awayTeamId,
        awayTeamName = awayTeamName,
        awayTeamAbbr = awayTeamAbbr,
        awayTeamLogo = awayTeamLogo,
        kickoffMillis = kickoffMillis,
        leagueName = leagueName,
        status = MatchStatus.fromName(status),
        homeScore = homeScore,
        awayScore = awayScore
    )
}

fun List<MatchEntity>.toDomainModelList(): List<Match> {
    return map { it.toDomainModel() }
}

// ============================================================
// EspnTeamListItemDto → Team
// ============================================================
fun EspnTeamListItemDto.toDomainModel(): Team? {
    return Team(
        id = id?.toIntOrNull() ?: return null,
        name = displayName ?: return null,
        abbreviation = abbreviation ?: "",
        logoUrl = logoUrl()
    )
}

// ============================================================
// DTOs del PROXY (nuestro Cloudflare Worker) → Entity/Team
// ============================================================
// Mucho más simples que los de ESPN: el Worker ya hizo la
// transformación pesada (fechas a epoch, estados a nombres de
// nuestro enum, local/visitante resueltos). Aquí solo validamos.
// ============================================================

fun ProxyMatchDto.toEntity(): MatchEntity? {
    return MatchEntity(
        id = id ?: return null,
        homeTeamId = homeId ?: return null,
        homeTeamName = homeName ?: "",
        homeTeamAbbr = homeAbbr ?: "",
        homeTeamLogo = homeLogo ?: "",
        awayTeamId = awayId ?: return null,
        awayTeamName = awayName ?: "",
        awayTeamAbbr = awayAbbr ?: "",
        awayTeamLogo = awayLogo ?: "",
        kickoffMillis = kickoffMillis ?: return null,
        leagueName = league ?: "",
        // fromName valida: si llega algo raro, queda UNKNOWN
        status = MatchStatus.fromName(status ?: "").name,
        homeScore = homeScore,
        awayScore = awayScore
    )
}

fun List<ProxyMatchDto>.toEntityListFromProxy(): List<MatchEntity> {
    return mapNotNull { it.toEntity() }
}

fun ProxyTeamDto.toDomainModel(): Team? {
    return Team(
        id = id ?: return null,
        name = name ?: return null,
        abbreviation = abbr ?: "",
        logoUrl = logo ?: ""
    )
}

package com.futbolarg.futbolargentinowidgets.data.remote.api

import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnScoreboardResponse
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnTeamsResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ============================================================
// EspnApiService.kt
// ============================================================
// Interfaz de Retrofit para la API pública de ESPN.
//
// BASE URL: https://site.api.espn.com/apis/site/v2/sports/soccer/
// SIN AUTENTICACIÓN: es una API pública, no requiere clave.
//
// {league} es el slug de la liga: "arg.1" = Liga Profesional.
// Lo dejamos como @Path para poder sumar otras competiciones
// en el futuro (ej: copa argentina) sin tocar esta interfaz.
// ============================================================

interface EspnApiService {

    // ----------------------------------------------------------
    // getScoreboard
    // ----------------------------------------------------------
    // Trae TODOS los partidos de la liga en un rango de fechas,
    // con estado y resultado incluidos.
    //
    // @param league Slug de la liga ("arg.1")
    // @param dates  Rango "YYYYMMDD-YYYYMMDD" (fechas en UTC)
    // @param limit  Máximo de eventos (1000 cubre meses enteros)
    // ----------------------------------------------------------
    @GET("{league}/scoreboard")
    suspend fun getScoreboard(
        @Path("league") league: String,
        @Query("dates") dates: String,
        @Query("limit") limit: Int = 1000
    ): EspnScoreboardResponse

    // ----------------------------------------------------------
    // getTeams
    // ----------------------------------------------------------
    // Lista los equipos de la liga (id, nombre, abreviatura,
    // escudo). Se usa en la pantalla de configuración del widget.
    // ----------------------------------------------------------
    @GET("{league}/teams")
    suspend fun getTeams(
        @Path("league") league: String,
        @Query("limit") limit: Int = 50
    ): EspnTeamsResponse
}

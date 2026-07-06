package com.futbolarg.futbolargentinowidgets.data.remote.api

import com.futbolarg.futbolargentinowidgets.data.remote.dto.ProxyFixturesResponse
import com.futbolarg.futbolargentinowidgets.data.remote.dto.ProxyTeamDto
import retrofit2.http.GET

// ============================================================
// KickoffApiService.kt
// ============================================================
// Interfaz de Retrofit para NUESTRO servidor (el Cloudflare
// Worker de server/worker.js).
//
// Compara con EspnApiService: sin parámetros de fecha ni de
// liga — el Worker decide la ventana y las competiciones.
// Eso también significa que podemos cambiar esas decisiones
// en el servidor sin actualizar la app.
//
// BASE URL: Constants.PROXY_BASE_URL (tu URL de workers.dev)
// ============================================================

interface KickoffApiService {

    // Todos los partidos de las 4 competiciones, ya transformados
    @GET("fixtures")
    suspend fun getFixtures(): ProxyFixturesResponse

    // Los 30 equipos de la Liga Profesional
    @GET("teams")
    suspend fun getTeams(): List<ProxyTeamDto>
}

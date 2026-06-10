package com.futbolarg.futbolargentinowidgets.data.repository

import android.util.Log
import com.futbolarg.futbolargentinowidgets.data.local.dao.MatchDao
import com.futbolarg.futbolargentinowidgets.data.mapper.toDomainModel
import com.futbolarg.futbolargentinowidgets.data.mapper.toDomainModelList
import com.futbolarg.futbolargentinowidgets.data.mapper.toEntityList
import com.futbolarg.futbolargentinowidgets.data.remote.api.EspnApiService
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.domain.model.Team
import com.futbolarg.futbolargentinowidgets.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// MatchRepository.kt
// ============================================================
// Punto central de acceso a datos de partidos.
//
// Estrategia "offline-first":
// 1. Widget y pantallas leen SIEMPRE de Room (rápido, sin red)
// 2. syncFixtures() pide el fixture a ESPN y actualiza Room
// 3. La sincronización la dispara MatchSyncWorker SOLO cuando
//    hace falta (alrededor de los partidos), nunca "a cada rato"
//
// Un solo request de scoreboard trae TODA la liga en el rango
// de fechas: sirve para todos los widgets a la vez, sin importar
// cuántos equipos distintos siga el usuario.
// ============================================================

@Singleton
class MatchRepository @Inject constructor(
    private val apiService: EspnApiService,
    private val matchDao: MatchDao
) {

    companion object {
        private const val TAG = "MatchRepository"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

        // Tras el final, el widget muestra el resultado durante
        // esta ventana antes de pasar a "próximo partido"
        private val RESULT_WINDOW_MILLIS = TimeUnit.HOURS.toMillis(12)

        // Duración estimada máxima de un partido (para detectar
        // "debería haber terminado" aunque no hayamos sincronizado)
        private val MATCH_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(130)
    }

    // ========================================================
    // SINCRONIZAR (ESPN → Room)
    // ========================================================

    // Pide a ESPN todos los partidos de la liga en la ventana
    // [hoy - SYNC_DAYS_BACK, hoy + SYNC_DAYS_FORWARD] y los
    // guarda en Room. Devuelve true si salió bien.
    suspend fun syncFixtures(): Boolean {
        return try {
            val today = LocalDate.now(ZoneOffset.UTC)
            val from = today.minusDays(Constants.SYNC_DAYS_BACK).format(DATE_FORMAT)
            val to = today.plusDays(Constants.SYNC_DAYS_FORWARD).format(DATE_FORMAT)

            Log.d(TAG, "Sincronizando fixture $from-$to...")
            val response = apiService.getScoreboard(
                league = Constants.LEAGUE_SLUG,
                dates = "$from-$to"
            )

            val entities = response.events.toEntityList()
            matchDao.insertMatches(entities)

            // Limpieza: borra terminados de hace más de 30 días
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            matchDao.deleteOldFinishedMatches(cutoff)

            Log.d(TAG, "Sync OK: ${entities.size} partidos guardados")
            true
        } catch (e: Exception) {
            // Sin internet o API caída: no crasheamos, Room
            // conserva los últimos datos conocidos.
            Log.e(TAG, "Error sincronizando fixture: ${e.message}", e)
            false
        }
    }

    // ========================================================
    // LECTURAS (Room)
    // ========================================================

    // El partido que el widget debe mostrar para un equipo:
    //
    //   1. Partido EN JUEGO ahora           → mostrar "Jugando"
    //   2. Terminó hace < 12 h              → mostrar resultado
    //   3. Próximo programado               → mostrar fecha/hora
    //   4. Solo hay un resultado viejo      → mostrar resultado
    //   5. Nada                             → null (sin datos)
    suspend fun getWidgetMatch(teamId: Int): Match? {
        val now = System.currentTimeMillis()

        val currentOrNext = matchDao.getCurrentOrNextMatch(teamId, now)?.toDomainModel()
        val lastFinished = matchDao.getLastFinishedMatch(teamId)?.toDomainModel()

        // 1. En juego
        if (currentOrNext != null && currentOrNext.status.isLive) {
            return currentOrNext
        }

        // 2. Resultado reciente (terminó hace menos de 12 h)
        if (lastFinished != null) {
            val endedAround = lastFinished.kickoffMillis + MATCH_DURATION_MILLIS
            if (now - endedAround in 0..RESULT_WINDOW_MILLIS) {
                return lastFinished
            }
        }

        // 3. Próximo programado / 4. último resultado / 5. nada
        return currentOrNext ?: lastFinished
    }

    // Partidos futuros de toda la liga (pantalla principal)
    fun getUpcomingMatches(): Flow<List<Match>> {
        return matchDao.getUpcomingMatches(System.currentTimeMillis())
            .map { it.toDomainModelList() }
    }

    // Próximo kickoff programado de un equipo (programador de sync)
    suspend fun getNextKickoff(teamId: Int): Long? {
        return matchDao.getNextKickoff(teamId, System.currentTimeMillis())
    }

    // ¿Alguno de estos equipos está jugando ahora?
    suspend fun hasLiveMatch(teamIds: List<Int>): Boolean {
        if (teamIds.isEmpty()) return false
        return matchDao.countLiveMatches(teamIds) > 0
    }

    // Partidos de los equipos seguidos desde ayer en adelante.
    // El worker los compara antes/después del sync para detectar
    // transiciones de estado y disparar notificaciones.
    suspend fun getTrackedMatches(teamIds: List<Int>): List<Match> {
        if (teamIds.isEmpty()) return emptyList()
        val from = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        return matchDao.getMatchesForTeams(teamIds, from).toDomainModelList()
    }

    // ========================================================
    // EQUIPOS (directo de la API, sin cache)
    // ========================================================

    // Lista de equipos de la liga para la pantalla de
    // configuración. Requiere internet (solo se usa al
    // agregar/configurar un widget).
    suspend fun getTeams(): List<Team> {
        val response = apiService.getTeams(league = Constants.LEAGUE_SLUG)
        return response.allTeams()
            .mapNotNull { it.toDomainModel() }
            .sortedBy { it.name }
    }
}

package com.futbolarg.futbolargentinowidgets.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.futbolarg.futbolargentinowidgets.data.local.entity.MatchEntity
import kotlinx.coroutines.flow.Flow

// ============================================================
// MatchDao.kt
// ============================================================
// Operaciones sobre la tabla "matches".
//
// La lógica clave del widget vive en dos queries:
//
// 1) getCurrentOrNextMatch: el partido "relevante" de un equipo.
//    Es el partido EN JUEGO si lo hay; si no, el próximo
//    programado; si no, null.
//
// 2) getLastFinishedMatch: el último partido terminado, para
//    mostrar el resultado final hasta que se conozca/acerque
//    el siguiente.
//
// Nota: status guarda nombres de nuestro enum MatchStatus.
// Los estados "en vivo" son: FIRST_HALF, HALFTIME, SECOND_HALF, LIVE.
// ============================================================

@Dao
interface MatchDao {

    // Partido relevante: en juego ahora, o el próximo programado.
    // El CASE prioriza partidos en vivo sobre programados, y entre
    // programados gana el de fecha más cercana.
    @Query(
        """
        SELECT * FROM matches
        WHERE (homeTeamId = :teamId OR awayTeamId = :teamId)
          AND (
                status IN ('FIRST_HALF','HALFTIME','SECOND_HALF','LIVE')
                OR (status = 'SCHEDULED' AND kickoffMillis >= :now - 3600000)
              )
        ORDER BY
          CASE WHEN status IN ('FIRST_HALF','HALFTIME','SECOND_HALF','LIVE')
               THEN 0 ELSE 1 END,
          kickoffMillis ASC
        LIMIT 1
        """
    )
    suspend fun getCurrentOrNextMatch(teamId: Int, now: Long): MatchEntity?

    // Último partido terminado del equipo (para mostrar el resultado)
    @Query(
        """
        SELECT * FROM matches
        WHERE (homeTeamId = :teamId OR awayTeamId = :teamId)
          AND status = 'FINISHED'
        ORDER BY kickoffMillis DESC
        LIMIT 1
        """
    )
    suspend fun getLastFinishedMatch(teamId: Int): MatchEntity?

    // Todos los partidos futuros de la liga (pantalla principal).
    // Flow = la UI se actualiza sola cuando cambia la tabla.
    @Query(
        """
        SELECT * FROM matches
        WHERE kickoffMillis >= :now - 7200000
        ORDER BY kickoffMillis ASC
        """
    )
    fun getUpcomingMatches(now: Long): Flow<List<MatchEntity>>

    // Próximo kickoff de un partido programado de un equipo
    // (lo usa el programador de sincronizaciones)
    @Query(
        """
        SELECT MIN(kickoffMillis) FROM matches
        WHERE (homeTeamId = :teamId OR awayTeamId = :teamId)
          AND status = 'SCHEDULED'
          AND kickoffMillis > :now
        """
    )
    suspend fun getNextKickoff(teamId: Int, now: Long): Long?

    // Kickoff del partido EN VIVO más antiguo de estos equipos
    // (para calcular cuánto lleva jugándose y adaptar el polling)
    @Query(
        """
        SELECT MIN(kickoffMillis) FROM matches
        WHERE (homeTeamId IN (:teamIds) OR awayTeamId IN (:teamIds))
          AND status IN ('FIRST_HALF','HALFTIME','SECOND_HALF','LIVE')
        """
    )
    suspend fun getLiveKickoff(teamIds: List<Int>): Long?

    // ¿Hay algún partido en vivo de alguno de estos equipos?
    @Query(
        """
        SELECT COUNT(*) FROM matches
        WHERE (homeTeamId IN (:teamIds) OR awayTeamId IN (:teamIds))
          AND status IN ('FIRST_HALF','HALFTIME','SECOND_HALF','LIVE')
        """
    )
    suspend fun countLiveMatches(teamIds: List<Int>): Int

    // Partidos recientes/próximos de un grupo de equipos
    // (los workers lo usan para detectar cambios de estado:
    // programado → en juego, programado → postergado, etc.)
    @Query(
        """
        SELECT * FROM matches
        WHERE (homeTeamId IN (:teamIds) OR awayTeamId IN (:teamIds))
          AND kickoffMillis > :from
        """
    )
    suspend fun getMatchesForTeams(teamIds: List<Int>, from: Long): List<MatchEntity>

    // Insertar/actualizar partidos (REPLACE = pisa por id)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<MatchEntity>)

    // Limpieza: borra partidos terminados muy viejos
    @Query("DELETE FROM matches WHERE status = 'FINISHED' AND kickoffMillis < :olderThan")
    suspend fun deleteOldFinishedMatches(olderThan: Long)
}

package com.futbolarg.futbolargentinowidgets.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.futbolarg.futbolargentinowidgets.data.preferences.AppSettings
import com.futbolarg.futbolargentinowidgets.data.preferences.WidgetPreferences
import com.futbolarg.futbolargentinowidgets.data.repository.MatchRepository
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.domain.model.MatchStatus
import com.futbolarg.futbolargentinowidgets.notifications.MatchNotifier
import com.futbolarg.futbolargentinowidgets.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

// ============================================================
// MatchSyncWorker.kt
// ============================================================
// El worker que ejecuta cada sincronización:
//
//   1. Pide el fixture actualizado a ESPN y lo guarda en Room
//   2. Redibuja TODOS los widgets (leen de Room)
//   3. Le pide al SyncScheduler que programe el próximo sync
//      (en el kickoff siguiente, o en 30 min si hay partido
//      en juego)
//
// @HiltWorker permite inyectar dependencias en un Worker.
// Requiere que FutbolWidgetsApp configure HiltWorkerFactory.
// ============================================================

@HiltWorker
class MatchSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MatchRepository,
    private val scheduler: SyncScheduler,
    private val widgetUpdater: WidgetUpdater,
    private val widgetPreferences: WidgetPreferences,
    private val appSettings: AppSettings,
    private val notifier: MatchNotifier
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MatchSyncWorker"
        private const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Iniciando sincronización...")

        // Foto del estado ANTES del sync (para detectar cambios)
        val teamIds = widgetPreferences.getAllFollowedTeamIds()
        val before = repository.getTrackedMatches(teamIds)
            .associateBy({ it.id }, { it.status })

        val success = repository.syncFixtures()

        // Comparar y notificar transiciones (si el usuario lo activó)
        if (success) {
            notifyTransitions(before, repository.getTrackedMatches(teamIds))
        }

        // Aunque el sync falle, redibujamos los widgets con lo
        // que haya en Room (mejor datos viejos que un widget roto).
        // WidgetUpdater escribe el estado Glance de cada instancia
        // y dispara la recomposición.
        widgetUpdater.updateAllWidgets()

        // Reprogramar el próximo sync según el nuevo estado
        scheduler.scheduleNext()

        return if (success) {
            Result.success()
        } else if (runAttemptCount < MAX_RETRIES) {
            // Reintento con backoff exponencial (default de WorkManager)
            Result.retry()
        } else {
            Result.failure()
        }
    }

    // ========================================================
    // Detección de transiciones de estado entre sync y sync:
    //   PROGRAMADO → EN JUEGO  → "¡Arrancó!"
    //   EN JUEGO   → TERMINADO → "Final" (con el resultado)
    //
    // Solo partidos de equipos seguidos por algún widget, y solo
    // si el ajuste correspondiente está activado. Comparar
    // estados (en vez de "notificar siempre al sincronizar")
    // evita avisos duplicados: una transición ocurre UNA vez.
    // ========================================================
    private suspend fun notifyTransitions(
        before: Map<Long, MatchStatus>,
        after: List<Match>
    ) {
        val notifyKickoff = appSettings.isNotifyKickoffEnabled()
        val notifyFinished = appSettings.isNotifyFinishedEnabled()
        if (!notifyKickoff && !notifyFinished) return

        after.distinctBy { it.id }.forEach { match ->
            val previous = before[match.id]

            when {
                notifyKickoff &&
                    previous == MatchStatus.SCHEDULED &&
                    match.status.isLive -> {
                    Log.d(TAG, "Transición a EN JUEGO: ${match.id}")
                    notifier.notifyKickoff(match)
                }

                // previous != null exige que conociéramos el partido
                // antes: evita avisar "final" de partidos viejos que
                // entran a la DB ya terminados en el primer sync
                notifyFinished &&
                    previous != null &&
                    !previous.isFinished &&
                    match.status.isFinished -> {
                    Log.d(TAG, "Transición a TERMINADO: ${match.id}")
                    notifier.notifyFinished(match)
                }
            }
        }
    }
}

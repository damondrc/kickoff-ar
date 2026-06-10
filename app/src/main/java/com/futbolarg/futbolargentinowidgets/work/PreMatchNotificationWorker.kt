package com.futbolarg.futbolargentinowidgets.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.futbolarg.futbolargentinowidgets.data.preferences.AppSettings
import com.futbolarg.futbolargentinowidgets.data.preferences.WidgetPreferences
import com.futbolarg.futbolargentinowidgets.data.repository.MatchRepository
import com.futbolarg.futbolargentinowidgets.domain.model.MatchStatus
import com.futbolarg.futbolargentinowidgets.notifications.MatchNotifier
import com.futbolarg.futbolargentinowidgets.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

// ============================================================
// PreMatchNotificationWorker.kt
// ============================================================
// Worker liviano (sin red) que dispara el aviso "ya casi
// arranca". SyncScheduler lo programa para ~30 min antes del
// próximo kickoff de los equipos seguidos.
//
// En lugar de recibir un partido fijo por parámetro, RE-CONSULTA
// Room al momento de dispararse: así, si el partido se movió de
// horario o se canceló entre medio, no avisa de más. También
// re-chequea el ajuste del usuario (pudo desactivarlo después
// de programado).
// ============================================================

@HiltWorker
class PreMatchNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: MatchRepository,
    private val widgetPreferences: WidgetPreferences,
    private val appSettings: AppSettings,
    private val notifier: MatchNotifier
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PreMatchNotifWorker"
    }

    override suspend fun doWork(): Result {
        // ¿Sigue activado el aviso previo?
        if (!appSettings.isNotifyBeforeStartEnabled()) {
            Log.d(TAG, "Aviso previo desactivado: nada que hacer")
            return Result.success()
        }

        val teamIds = widgetPreferences.getAllFollowedTeamIds()
        val now = System.currentTimeMillis()
        val window = TimeUnit.MINUTES.toMillis(Constants.PRE_MATCH_NOTIFY_MINUTES + 15)

        // Partidos programados que arrancan dentro de la ventana
        // (~45 min): son los que motivaron esta alarma
        repository.getTrackedMatches(teamIds)
            .filter { it.status == MatchStatus.SCHEDULED }
            .filter { it.kickoffMillis - now in 0..window }
            .distinctBy { it.id }
            .forEach { match ->
                Log.d(TAG, "Aviso previo: ${match.homeTeamAbbr} vs ${match.awayTeamAbbr}")
                notifier.notifyPreMatch(match)
            }

        return Result.success()
    }
}

package com.futbolarg.futbolargentinowidgets.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.futbolarg.futbolargentinowidgets.data.preferences.AppSettings
import com.futbolarg.futbolargentinowidgets.data.preferences.WidgetPreferences
import com.futbolarg.futbolargentinowidgets.data.repository.MatchRepository
import com.futbolarg.futbolargentinowidgets.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// SyncScheduler.kt
// ============================================================
// El "cerebro" del modelo de actualización de la app.
//
// FILOSOFÍA (requisito del proyecto): el widget NO actualiza
// a cada rato. Solo sincroniza:
//
//   ANTES   → nada. El widget ya sabe fecha y hora del partido.
//   INICIO  → un sync justo después del kickoff (pasa a "Jugando")
//   DURANTE → un sync cada 30 min (por si terminó)
//   FINAL   → muestra el resultado y programa el PRÓXIMO sync
//             recién para el siguiente partido
//
// Además hay un sync diario de seguridad (flex, barato) que
// capta cambios de fixture (reprogramaciones, nuevos torneos).
//
// Todo con WorkManager: sobrevive reinicios del teléfono y
// respeta la batería. No usamos AlarmManager exacto porque
// un margen de 1-2 minutos es irrelevante para este caso y
// así evitamos el permiso especial SCHEDULE_EXACT_ALARM.
// ============================================================

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MatchRepository,
    private val widgetPreferences: WidgetPreferences,
    private val appSettings: AppSettings
) {

    companion object {
        private const val TAG = "SyncScheduler"
        // Margen tras el kickoff para que ESPN ya marque "in"
        private val KICKOFF_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(2)
    }

    private val workManager = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // ========================================================
    // Sync inmediato (al agregar/configurar un widget)
    // ========================================================
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<MatchSyncWorker>()
            .setConstraints(networkConstraint)
            .build()

        // REPLACE: si había un sync programado para más adelante,
        // este lo pisa; al terminar, el worker reprograma el próximo.
        workManager.enqueueUniqueWork(
            Constants.SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    // ========================================================
    // Programar el PRÓXIMO sync según el estado de los partidos
    // (la llama el worker al final de cada sincronización)
    // ========================================================
    suspend fun scheduleNext() {
        val teamIds = widgetPreferences.getAllFollowedTeamIds()
        if (teamIds.isEmpty()) {
            Log.d(TAG, "Sin widgets configurados: no se programa sync")
            workManager.cancelUniqueWork(Constants.SYNC_WORK_NAME)
            return
        }

        val delayMillis: Long = if (repository.hasLiveMatch(teamIds)) {
            // Hay partido en juego → re-chequear en 30 min
            TimeUnit.MINUTES.toMillis(Constants.LIVE_POLL_MINUTES)
        } else {
            // Buscar el kickoff más cercano entre los equipos seguidos
            val nextKickoff = teamIds
                .mapNotNull { repository.getNextKickoff(it) }
                .minOrNull()

            if (nextKickoff == null) {
                // No hay próximos partidos conocidos (receso largo).
                // El sync diario de seguridad se encarga.
                Log.d(TAG, "Sin próximos partidos: queda solo el sync diario")
                return
            }

            // Si el usuario quiere aviso previo, programarlo
            // ~30 min antes de ese mismo kickoff
            schedulePreMatchNotification(nextKickoff)

            (nextKickoff + KICKOFF_MARGIN_MILLIS - System.currentTimeMillis())
                .coerceAtLeast(0)
        }

        val request = OneTimeWorkRequestBuilder<MatchSyncWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        // APPEND_OR_REPLACE (y no REPLACE): esta función la llama
        // el propio MatchSyncWorker al terminar. REPLACE cancelaría
        // al worker EN EJECUCIÓN (mismo nombre único); APPEND lo
        // encadena después sin matar al actual.
        workManager.enqueueUniqueWork(
            Constants.SYNC_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )

        Log.d(TAG, "Próximo sync en ${delayMillis / 60000} min")
    }

    // ========================================================
    // Aviso "ya casi arranca": worker liviano sin red, programado
    // PRE_MATCH_NOTIFY_MINUTES antes del kickoff. El worker
    // re-verifica el ajuste y el fixture al dispararse, así que
    // programarlo de más no genera avisos falsos.
    // ========================================================
    private suspend fun schedulePreMatchNotification(kickoffMillis: Long) {
        if (!appSettings.isNotifyBeforeStartEnabled()) {
            workManager.cancelUniqueWork(Constants.PRE_MATCH_WORK_NAME)
            return
        }

        val fireAt = kickoffMillis -
            TimeUnit.MINUTES.toMillis(Constants.PRE_MATCH_NOTIFY_MINUTES)
        val delay = (fireAt - System.currentTimeMillis()).coerceAtLeast(0)

        val request = OneTimeWorkRequestBuilder<PreMatchNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            Constants.PRE_MATCH_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        Log.d(TAG, "Aviso previo programado en ${delay / 60000} min")
    }

    // ========================================================
    // Sync diario de seguridad (capta fixtures nuevos y cambios
    // de horario; consume un request por día, nada más)
    // ========================================================
    fun ensureDailySync() {
        val request = PeriodicWorkRequestBuilder<MatchSyncWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(networkConstraint)
            .build()

        // KEEP: si ya existe, no lo duplica ni lo reinicia
        workManager.enqueueUniquePeriodicWork(
            Constants.DAILY_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

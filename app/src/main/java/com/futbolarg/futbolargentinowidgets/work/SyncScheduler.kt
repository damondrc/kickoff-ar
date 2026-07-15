package com.futbolarg.futbolargentinowidgets.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
// SyncScheduler.kt (v2 — con AlarmManager)
// ============================================================
// El "cerebro" del modelo de actualización.
//
// HISTORIA DEL REDISEÑO: la v1 programaba la sincronización del
// kickoff como trabajo diferido de WorkManager (setInitialDelay).
// Falla en el mundo real: una app de widget no se abre en días,
// Android la degrada de "standby bucket" y difiere sus trabajos
// horas o indefinidamente → el widget quedaba muerto hasta abrir
// la app. Los trabajos diferidos son para "algún momento
// conveniente"; un kickoff es una HORA EXACTA.
//
// Ahora: AlarmManager (setExactAndAllowWhileIdle atraviesa Doze)
// despierta la app a la hora del partido → SyncAlarmReceiver →
// WorkManager ejecuta el sync con su restricción de red.
//
//   ANTES   → alarma al kickoff (y otra 30 min antes si el
//             usuario quiere el aviso previo)
//   DURANTE → alarma cada 30 min (cada 10 en el tramo final)
//   FINAL   → se muestra el resultado y la alarma siguiente
//             queda para el próximo partido
//   SIEMPRE → sync diario de respaldo (WorkManager periódico,
//             que sí sobrevive standby y reinicios) + BootReceiver
//             que restablece alarmas tras reiniciar el teléfono.
//
// Nota Doze: en reposo profundo, las alarmas "allow while idle"
// se limitan a ~1 cada 15 min por app; el polling de 10 min del
// tramo final puede estirarse a 15 con pantalla apagada. Precio
// aceptable del bajo consumo.
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
        private val KICKOFF_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(2)
        private const val REQUEST_SYNC = 100
        private const val REQUEST_PRE_MATCH = 101
    }

    private val workManager = WorkManager.getInstance(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // ========================================================
    // Sync INMEDIATO (lo disparan: la alarma del kickoff, el
    // BootReceiver, la config de un widget y abrir la app)
    // ========================================================
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<MatchSyncWorker>()
            .setConstraints(networkConstraint)
            .build()

        workManager.enqueueUniqueWork(
            Constants.SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    // Encolar el aviso previo YA (lo dispara su alarma)
    fun runPreMatchNow() {
        workManager.enqueueUniqueWork(
            Constants.PRE_MATCH_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PreMatchNotificationWorker>().build()
        )
    }

    // ========================================================
    // Programar la PRÓXIMA alarma según el estado de los
    // partidos (la llama el worker al final de cada sync)
    // ========================================================
    suspend fun scheduleNext() {
        val teamIds = widgetPreferences.getAllFollowedTeamIds()
        if (teamIds.isEmpty()) {
            Log.d(TAG, "Sin widgets: se cancelan las alarmas")
            alarmManager.cancel(syncPendingIntent())
            alarmManager.cancel(preMatchPendingIntent())
            return
        }

        val now = System.currentTimeMillis()
        val liveKickoff = repository.getLiveKickoff(teamIds)

        val triggerAt: Long = if (liveKickoff != null) {
            // Partido en juego → re-chequeo con cadencia adaptativa
            val elapsedMinutes = (now - liveKickoff) / 60000
            val pollMinutes = if (elapsedMinutes >= Constants.LIVE_ENDGAME_AFTER_MINUTES) {
                Constants.LIVE_ENDGAME_POLL_MINUTES
            } else {
                Constants.LIVE_POLL_MINUTES
            }
            Log.d(TAG, "En vivo (min $elapsedMinutes): próximo chequeo en $pollMinutes min")
            now + TimeUnit.MINUTES.toMillis(pollMinutes)
        } else {
            val nextKickoff = teamIds
                .mapNotNull { repository.getNextKickoff(it) }
                .minOrNull()

            if (nextKickoff == null) {
                Log.d(TAG, "Sin próximos partidos: queda el sync diario")
                return
            }

            schedulePreMatchAlarm(nextKickoff)
            nextKickoff + KICKOFF_MARGIN_MILLIS
        }

        setAlarm(triggerAt, syncPendingIntent())
        Log.d(TAG, "Alarma de sync en ${(triggerAt - now) / 60000} min")
    }

    // Alarma del aviso "ya casi arranca" (solo si está activado)
    private suspend fun schedulePreMatchAlarm(kickoffMillis: Long) {
        if (!appSettings.isNotifyBeforeStartEnabled()) {
            alarmManager.cancel(preMatchPendingIntent())
            return
        }

        val fireAt = kickoffMillis -
            TimeUnit.MINUTES.toMillis(Constants.PRE_MATCH_NOTIFY_MINUTES)
        if (fireAt > System.currentTimeMillis()) {
            setAlarm(fireAt, preMatchPendingIntent())
            Log.d(TAG, "Alarma de aviso previo programada")
        }
    }

    // ========================================================
    // Sync diario de respaldo. Se queda en WorkManager a
    // propósito: es tolerante a retrasos y ÉL SÍ sobrevive
    // standby buckets y reinicios — la red de seguridad si una
    // alarma se pierde.
    // ========================================================
    fun ensureDailySync() {
        val request = PeriodicWorkRequestBuilder<MatchSyncWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(networkConstraint)
            .build()

        workManager.enqueueUniquePeriodicWork(
            Constants.DAILY_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // ========================================================
    // Utilidades de alarmas
    // ========================================================

    // Exacta si el usuario/sistema lo permite (Android 12+ lo
    // restringe); si no, inexacta "while idle": puede derivar
    // unos minutos, pero JAMÁS queda muerta como el trabajo
    // diferido de la v1.
    private fun setAlarm(triggerAt: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pi
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pi
            )
        }
    }

    private fun syncPendingIntent(): PendingIntent =
        pendingIntent(SyncAlarmReceiver.ACTION_SYNC, REQUEST_SYNC)

    private fun preMatchPendingIntent(): PendingIntent =
        pendingIntent(SyncAlarmReceiver.ACTION_PRE_MATCH, REQUEST_PRE_MATCH)

    // Mismo requestCode + misma action = reprogramar PISA la
    // alarma anterior (nunca se acumulan duplicadas)
    private fun pendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, SyncAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

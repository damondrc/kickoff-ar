package com.futbolarg.futbolargentinowidgets.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// ============================================================
// SyncAlarmReceiver.kt
// ============================================================
// Receptor de las ALARMAS del sistema (AlarmManager).
//
// ¿Por qué existe? (la lección del bug más grave de la app)
// WorkManager con setInitialDelay es "diferible": si la app
// pasa días sin abrirse — el caso normal de un widget — Android
// la degrada de standby bucket y ejecuta sus trabajos horas
// tarde o nunca. El widget quedaba muerto hasta abrir la app.
//
// AlarmManager, en cambio, existe para despertar la app a una
// hora concreta (setExactAndAllowWhileIdle atraviesa Doze).
// La alarma NO hace el trabajo: solo encola el worker, que
// corre con su restricción de red vía WorkManager. Cada
// herramienta en su rol:
//
//   AlarmManager (cuándo) → este receiver → WorkManager (qué)
// ============================================================

@AndroidEntryPoint
class SyncAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SyncAlarmReceiver"
        const val ACTION_SYNC = "com.futbolarg.futbolargentinowidgets.SYNC_ALARM"
        const val ACTION_PRE_MATCH = "com.futbolarg.futbolargentinowidgets.PRE_MATCH_ALARM"
    }

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarma recibida: ${intent.action}")
        when (intent.action) {
            // Hora del kickoff (o del re-chequeo en vivo):
            // encolar la sincronización ya mismo
            ACTION_SYNC -> syncScheduler.syncNow()

            // ~30 min antes del partido: encolar el aviso previo
            ACTION_PRE_MATCH -> syncScheduler.runPreMatchNow()
        }
    }
}

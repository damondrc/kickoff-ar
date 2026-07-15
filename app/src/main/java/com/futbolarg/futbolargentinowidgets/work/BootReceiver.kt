package com.futbolarg.futbolargentinowidgets.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// ============================================================
// BootReceiver.kt
// ============================================================
// Las alarmas de AlarmManager NO sobreviven a un reinicio del
// teléfono (a diferencia de WorkManager, que sí persiste).
// Este receiver escucha BOOT_COMPLETED y dispara un sync, cuyo
// worker al terminar reprograma las alarmas del próximo partido.
// Sin esto, un reinicio dejaría el widget sin ciclo de
// actualización hasta el sync diario.
// ============================================================

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Reinicio detectado: restableciendo alarmas")
            syncScheduler.syncNow()
        }
    }
}

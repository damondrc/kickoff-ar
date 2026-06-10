package com.futbolarg.futbolargentinowidgets

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// ============================================================
// FutbolWidgetsApp.kt
// ============================================================
// @HiltAndroidApp: punto de arranque de Hilt.
//
// Configuration.Provider: le entrega a WorkManager la
// HiltWorkerFactory, que es lo que permite que MatchSyncWorker
// reciba dependencias inyectadas (@HiltWorker).
//
// IMPORTANTE: para que esto funcione hay que DESACTIVAR el
// inicializador automático de WorkManager en el Manifest
// (ver el bloque <provider> con tools:node="remove").
// ============================================================

@HiltAndroidApp
class FutbolWidgetsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

package com.futbolarg.futbolargentinowidgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.futbolarg.futbolargentinowidgets.data.preferences.AppSettings
import com.futbolarg.futbolargentinowidgets.data.repository.MatchRepository
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.work.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================
// MainViewModel.kt
// ============================================================
// Expone a la pantalla principal:
// - los próximos partidos de la liga (Flow de Room, se
//   actualiza solo con cada sync)
// - los tres ajustes de notificaciones (Flows de DataStore)
// ============================================================

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: MatchRepository,
    private val appSettings: AppSettings,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    init {
        // AUTO-REPARACIÓN al abrir la app:
        // Si Samsung/Doze durmió la app y el ciclo de workers se
        // perdió (widget atascado, datos viejos), abrir la app lo
        // restablece: re-asegura el sync diario y dispara una
        // sincronización que además redibuja todos los widgets.
        // Costo: un request por apertura (que la caché del Worker
        // absorbe casi siempre).
        syncScheduler.ensureDailySync()
        syncScheduler.syncNow()
    }

    val upcomingMatches: StateFlow<List<Match>> =
        repository.getUpcomingMatches()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // ---------- Ajustes de notificaciones ----------

    val notifyBeforeStart: StateFlow<Boolean> = settingFlow(appSettings.notifyBeforeStart)
    val notifyKickoff: StateFlow<Boolean> = settingFlow(appSettings.notifyKickoff)
    val notifyFinished: StateFlow<Boolean> = settingFlow(appSettings.notifyFinished)

    // Al cambiar el aviso previo, reprogramamos el ciclo de sync:
    // así el aviso del próximo partido se agenda (o cancela) al
    // instante, sin esperar al siguiente sync
    fun setNotifyBeforeStart(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.setSetting(AppSettings.NOTIFY_BEFORE_START, enabled)
            syncScheduler.scheduleNext()
        }
    }

    fun setNotifyKickoff(enabled: Boolean) =
        setSetting(AppSettings.NOTIFY_KICKOFF, enabled)

    fun setNotifyFinished(enabled: Boolean) =
        setSetting(AppSettings.NOTIFY_FINISHED, enabled)

    private fun settingFlow(flow: Flow<Boolean>): StateFlow<Boolean> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun setSetting(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        enabled: Boolean
    ) {
        viewModelScope.launch { appSettings.setSetting(key, enabled) }
    }
}

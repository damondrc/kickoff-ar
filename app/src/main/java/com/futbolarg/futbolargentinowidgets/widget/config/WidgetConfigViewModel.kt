package com.futbolarg.futbolargentinowidgets.widget.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.futbolarg.futbolargentinowidgets.data.preferences.AppSettings
import com.futbolarg.futbolargentinowidgets.data.preferences.WidgetPreferences
import com.futbolarg.futbolargentinowidgets.data.repository.MatchRepository
import com.futbolarg.futbolargentinowidgets.domain.model.Team
import com.futbolarg.futbolargentinowidgets.widget.WidgetUpdater
import com.futbolarg.futbolargentinowidgets.work.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================
// WidgetConfigViewModel.kt
// ============================================================
// Lógica de la pantalla de configuración del widget:
// 1. Carga la lista de equipos de la liga desde ESPN
// 2. Al elegir uno: lo guarda para ESTA instancia de widget,
//    asegura el sync diario y dispara un sync inmediato
// ============================================================

@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val repository: MatchRepository,
    private val widgetPreferences: WidgetPreferences,
    private val syncScheduler: SyncScheduler,
    private val widgetUpdater: WidgetUpdater,
    private val appSettings: AppSettings
) : ViewModel() {

    // Estados posibles de la pantalla
    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val teams: List<Team>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTeams()
    }

    fun loadTeams() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Ready(repository.getTeams())
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    "No se pudo cargar la lista de equipos.\n" +
                        "Revisa tu conexión e intenta de nuevo."
                )
            }
        }
    }

    // Guarda la elección, escribe el estado Glance del widget
    // (queda en "Sincronizando…" o con datos si Room ya los
    // tiene) y dispara la primera sincronización. onSaved se
    // llama cuando todo quedó persistido: la Activity solo
    // devuelve RESULT_OK y cierra.
    fun selectTeam(widgetId: Int, team: Team, onSaved: () -> Unit) {
        viewModelScope.launch {
            widgetPreferences.saveTeamForWidget(widgetId, team)
            // El último equipo elegido tiñe el tema de la app
            appSettings.setLastTeamColor(team.colorHex)
            widgetUpdater.updateWidget(widgetId)
            syncScheduler.ensureDailySync()
            syncScheduler.syncNow()
            onSaved()
        }
    }
}

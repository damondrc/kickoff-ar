package com.futbolarg.futbolargentinowidgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.futbolarg.futbolargentinowidgets.data.preferences.AppSettings
import com.futbolarg.futbolargentinowidgets.data.preferences.WidgetPreferences
import com.futbolarg.futbolargentinowidgets.data.repository.MatchRepository
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.domain.model.Team
import com.futbolarg.futbolargentinowidgets.work.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================
// MainViewModel.kt
// ============================================================
// Estado de la pantalla principal (v1.1):
//
// PARTIDOS:
//   - visibleTeams: equipos seguidos por widgets, menos los que
//     el usuario ocultó (mantener presionado → quitar)
//   - misEquipos: partidos de esos equipos (para las tarjetas
//     desplegables)
//   - fixture + leagueFilter: el fixture filtrable
//   - fixtureView: LISTA o CALENDARIO
//
// AJUSTES: los tres switches de notificaciones y la
// restauración de equipos ocultos (sección Personalización).
// ============================================================

// Cómo se visualiza el fixture
enum class FixtureView { LIST, CALENDAR }

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: MatchRepository,
    private val appSettings: AppSettings,
    private val syncScheduler: SyncScheduler,
    private val widgetPreferences: WidgetPreferences
) : ViewModel() {

    // ---------- Pestaña Partidos ----------

    private val allMatches: StateFlow<List<Match>> =
        repository.getUpcomingMatches()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Equipos seguidos (reactivo) menos los ocultados por el usuario
    val visibleTeams: StateFlow<List<Team>> =
        combine(
            widgetPreferences.followedTeamsFlow,
            appSettings.hiddenTeamIds
        ) { teams, hidden ->
            teams.filterNot { it.id in hidden }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cuántos equipos hay ocultos (para el botón "restaurar")
    val hiddenTeamCount: StateFlow<Int> =
        combine(
            appSettings.hiddenTeamIds,
            widgetPreferences.followedTeamsFlow
        ) { hidden, teams ->
            teams.count { it.id in hidden }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Partidos de los equipos seguidos (las tarjetas desplegables
    // filtran por equipo sobre esta lista)
    val misEquipos: StateFlow<List<Match>> =
        combine(allMatches, widgetPreferences.followedTeamsFlow) { matches, teams ->
            val ids = teams.map { it.id }.toSet()
            matches.filter { it.homeTeamId in ids || it.awayTeamId in ids }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun hideTeam(teamId: Int) {
        viewModelScope.launch { appSettings.hideTeam(teamId) }
    }

    fun restoreHiddenTeams() {
        viewModelScope.launch { appSettings.restoreHiddenTeams() }
    }

    // Filtro por competición (null = todas)
    private val _leagueFilter = MutableStateFlow<String?>(null)
    val leagueFilter: StateFlow<String?> = _leagueFilter.asStateFlow()

    fun setLeagueFilter(league: String?) {
        _leagueFilter.value = league
    }

    // Vista del fixture: lista agrupada o calendario
    private val _fixtureView = MutableStateFlow(FixtureView.LIST)
    val fixtureView: StateFlow<FixtureView> = _fixtureView.asStateFlow()

    fun setFixtureView(view: FixtureView) {
        _fixtureView.value = view
    }

    // Fixture completo, con el filtro de competición aplicado
    val fixture: StateFlow<List<Match>> =
        combine(allMatches, _leagueFilter) { matches, filter ->
            if (filter == null) matches
            else matches.filter { it.leagueName == filter }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------- Pestaña Ajustes (notificaciones) ----------

    val notifyBeforeStart: StateFlow<Boolean> = settingFlow(appSettings.notifyBeforeStart)
    val notifyKickoff: StateFlow<Boolean> = settingFlow(appSettings.notifyKickoff)
    val notifyFinished: StateFlow<Boolean> = settingFlow(appSettings.notifyFinished)

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

    // ------------------------------------------------------------
    // init AL FINAL de la clase (lección del NPE de la v1: nunca
    // disparar trabajo antes de declarar el estado que toca)
    // ------------------------------------------------------------
    init {
        // AUTO-REPARACIÓN al abrir la app
        syncScheduler.ensureDailySync()
        syncScheduler.syncNow()
    }
}

package com.futbolarg.futbolargentinowidgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.futbolarg.futbolargentinowidgets.data.preferences.AppSettings
import com.futbolarg.futbolargentinowidgets.data.preferences.WidgetPreferences
import com.futbolarg.futbolargentinowidgets.data.repository.MatchRepository
import com.futbolarg.futbolargentinowidgets.domain.model.Match
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
// Estado de la pantalla principal (dos pestañas):
//
// PARTIDOS:
//   - misEquipos: próximos partidos de los equipos que siguen
//     tus widgets (la sección destacada de arriba)
//   - fixture: todos los partidos, filtrables por competición
//   - leagueFilter: el chip seleccionado (null = todas)
//
// AJUSTES:
//   - los tres switches de notificaciones
//
// Patrón: la UI no filtra nada; observa StateFlows ya
// combinados. combine() re-emite cuando cambia CUALQUIERA de
// sus fuentes (partidos nuevos por un sync, o un chip tocado).
// ============================================================

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

    // IDs de los equipos seguidos por algún widget
    private val _followedTeamIds = MutableStateFlow<Set<Int>>(emptySet())
    val followedTeamIds: StateFlow<Set<Int>> = _followedTeamIds.asStateFlow()

    // Filtro por competición (null = todas)
    private val _leagueFilter = MutableStateFlow<String?>(null)
    val leagueFilter: StateFlow<String?> = _leagueFilter.asStateFlow()

    fun setLeagueFilter(league: String?) {
        _leagueFilter.value = league
    }

    // Próximos partidos SOLO de los equipos seguidos (sección
    // "Mis equipos"). No le aplica el filtro de competición:
    // si sigues a Boca quieres ver su próximo partido sea del
    // torneo que sea.
    val misEquipos: StateFlow<List<Match>> =
        combine(allMatches, _followedTeamIds) { matches, followed ->
            matches.filter { it.homeTeamId in followed || it.awayTeamId in followed }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fixture completo, con el filtro de competición aplicado
    val fixture: StateFlow<List<Match>> =
        combine(allMatches, _leagueFilter) { matches, filter ->
            if (filter == null) matches
            else matches.filter { it.leagueName == filter }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun loadFollowedTeams() {
        viewModelScope.launch {
            _followedTeamIds.value =
                widgetPreferences.getAllFollowedTeamIds().toSet()
        }
    }

    // ------------------------------------------------------------
    // IMPORTANTE: este init va AL FINAL de la clase a propósito.
    //
    // Kotlin ejecuta propiedades e init en orden de declaración.
    // En la versión anterior el init estaba ARRIBA y lanzaba
    // loadFollowedTeams() ANTES de que _followedTeamIds existiera
    // → NullPointerException al abrir la app. Regla: un init que
    // dispara trabajo solo puede ir después del estado que toca.
    // ------------------------------------------------------------
    init {
        // AUTO-REPARACIÓN al abrir la app: re-asegura el ciclo de
        // workers y sincroniza (redibuja widgets atascados)
        syncScheduler.ensureDailySync()
        syncScheduler.syncNow()
        loadFollowedTeams()
    }

    // ---------- Pestaña Ajustes ----------

    val notifyBeforeStart: StateFlow<Boolean> = settingFlow(appSettings.notifyBeforeStart)
    val notifyKickoff: StateFlow<Boolean> = settingFlow(appSettings.notifyKickoff)
    val notifyFinished: StateFlow<Boolean> = settingFlow(appSettings.notifyFinished)

    // Al cambiar el aviso previo, reprogramamos el ciclo de sync
    // para que el aviso del próximo partido se agende (o cancele)
    // al instante
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

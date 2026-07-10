package com.futbolarg.futbolargentinowidgets.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.futbolarg.futbolargentinowidgets.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// AppSettings.kt
// ============================================================
// Configuración GLOBAL de la app (a diferencia de
// WidgetPreferences, que es por instancia de widget).
//
// Por ahora: los tres interruptores de notificaciones.
// Los Flow alimentan los switches de la pantalla de ajustes;
// los suspend los usan los workers en el momento de notificar
// (así un cambio de ajuste aplica al instante, sin reprogramar).
// ============================================================

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.SETTINGS_NAME
)

@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Avisar ~30 min antes del partido
        val NOTIFY_BEFORE_START = booleanPreferencesKey("notify_before_start")
        // Avisar cuando el partido arranca
        val NOTIFY_KICKOFF = booleanPreferencesKey("notify_kickoff")
        // Avisar cuando termina (con el resultado final)
        val NOTIFY_FINISHED = booleanPreferencesKey("notify_finished")

        // ¿El widget usa el color oficial del equipo como fondo?
        val USE_TEAM_COLOR_WIDGET = booleanPreferencesKey("use_team_color_widget")

        // Color (hex sin "#") del ÚLTIMO equipo elegido: tiñe el
        // tema de la app
        val LAST_TEAM_COLOR = stringPreferencesKey("last_team_color")

        // Equipos ocultados de la sección "Mis equipos" (ids como
        // String porque DataStore no tiene Set<Int>)
        val HIDDEN_TEAM_IDS = stringSetPreferencesKey("hidden_team_ids")
    }

    // ---------- Flows reactivos (para la UI) ----------

    val notifyBeforeStart: Flow<Boolean> =
        context.settingsDataStore.data.map { it[NOTIFY_BEFORE_START] ?: false }

    val notifyKickoff: Flow<Boolean> =
        context.settingsDataStore.data.map { it[NOTIFY_KICKOFF] ?: false }

    val notifyFinished: Flow<Boolean> =
        context.settingsDataStore.data.map { it[NOTIFY_FINISHED] ?: false }

    // ---------- Lecturas puntuales (para los workers) ----------

    suspend fun isNotifyBeforeStartEnabled(): Boolean =
        context.settingsDataStore.data.first()[NOTIFY_BEFORE_START] ?: false

    suspend fun isNotifyKickoffEnabled(): Boolean =
        context.settingsDataStore.data.first()[NOTIFY_KICKOFF] ?: false

    suspend fun isNotifyFinishedEnabled(): Boolean =
        context.settingsDataStore.data.first()[NOTIFY_FINISHED] ?: false

    // ---------- Personalización ----------

    val useTeamColorWidget: Flow<Boolean> =
        context.settingsDataStore.data.map { it[USE_TEAM_COLOR_WIDGET] ?: false }

    suspend fun isUseTeamColorWidgetEnabled(): Boolean =
        context.settingsDataStore.data.first()[USE_TEAM_COLOR_WIDGET] ?: false

    val lastTeamColor: Flow<String> =
        context.settingsDataStore.data.map { it[LAST_TEAM_COLOR] ?: "" }

    suspend fun setLastTeamColor(colorHex: String) {
        context.settingsDataStore.edit { it[LAST_TEAM_COLOR] = colorHex }
    }

    // ---------- Equipos ocultos en "Mis equipos" ----------

    val hiddenTeamIds: Flow<Set<Int>> =
        context.settingsDataStore.data.map { prefs ->
            (prefs[HIDDEN_TEAM_IDS] ?: emptySet())
                .mapNotNull { it.toIntOrNull() }
                .toSet()
        }

    suspend fun hideTeam(teamId: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[HIDDEN_TEAM_IDS] =
                (prefs[HIDDEN_TEAM_IDS] ?: emptySet()) + teamId.toString()
        }
    }

    suspend fun restoreHiddenTeams() {
        context.settingsDataStore.edit { it.remove(HIDDEN_TEAM_IDS) }
    }

    // ---------- Escritura ----------

    suspend fun setSetting(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }
}

package com.futbolarg.futbolargentinowidgets.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.futbolarg.futbolargentinowidgets.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
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

        // Equipos QUITADOS de "Mis equipos". Cada entrada es
        // "idEquipo:momentoEnQueSeQuitó" (DataStore no tiene
        // Set<Int> ni mapas).
        //
        // SEMÁNTICA (importante): quitar es definitivo — el
        // equipo deja de estar en "Mis equipos" y se comporta
        // como cualquier otro de la liga. Lo que caduca a las 2
        // semanas es la VENTANA PARA DESHACER: durante ese
        // tiempo aparece en Ajustes con un botón "Restaurar";
        // después desaparece de esa lista, pero sigue fuera de
        // "Mis equipos". Para volver a seguirlo basta con
        // configurar un widget con ese equipo.
        //
        // Nota: en la v1.1 se eliminó el tinte por color de equipo
        // (widget y tema): los colores oficiales de los clubes
        // rompían la legibilidad con demasiada frecuencia. La
        // paleta ahora es uniforme.
        val HIDDEN_TEAM_IDS = stringSetPreferencesKey("hidden_team_ids")

        // Cuánto tiempo se puede deshacer el quitado
        private val UNDO_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(14)
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

    // ---------- Equipos quitados de "Mis equipos" ----------

    // TODOS los quitados, sin importar hace cuánto: la exclusión
    // de "Mis equipos" es permanente
    val hiddenTeamIds: Flow<Set<Int>> =
        context.settingsDataStore.data.map { prefs ->
            parseHidden(prefs[HIDDEN_TEAM_IDS]).keys
        }

    // Solo los quitados hace menos de 2 semanas: son los que
    // Ajustes ofrece restaurar. Pasada la ventana, el equipo
    // deja de listarse (pero sigue fuera de "Mis equipos")
    val restorableTeamIds: Flow<Set<Int>> =
        context.settingsDataStore.data.map { prefs ->
            val now = System.currentTimeMillis()
            parseHidden(prefs[HIDDEN_TEAM_IDS])
                .filterValues { now - it < UNDO_WINDOW_MILLIS }
                .keys
        }

    suspend fun hideTeam(teamId: Int) {
        context.settingsDataStore.edit { prefs ->
            val actuales = parseHidden(prefs[HIDDEN_TEAM_IDS]).toMutableMap()
            actuales[teamId] = System.currentTimeMillis()
            prefs[HIDDEN_TEAM_IDS] = actuales.map { (id, ts) -> "$id:$ts" }.toSet()
        }
    }

    // Deshacer: el equipo vuelve a "Mis equipos". También se
    // llama al configurar un widget con ese equipo — pedirlo
    // explícitamente es señal de que se lo quiere seguir de nuevo
    suspend fun restoreTeam(teamId: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[HIDDEN_TEAM_IDS] = parseHidden(prefs[HIDDEN_TEAM_IDS])
                .filterKeys { it != teamId }
                .map { (id, ts) -> "$id:$ts" }
                .toSet()
        }
    }

    suspend fun restoreHiddenTeams() {
        context.settingsDataStore.edit { it.remove(HIDDEN_TEAM_IDS) }
    }

    // "16:1784000000000" → 16 to 1784000000000, descartando
    // entradas corruptas. Formato viejo (solo el id): se le
    // asigna 0 = quitado hace mucho, sin ventana de deshacer.
    private fun parseHidden(raw: Set<String>?): Map<Int, Long> {
        return (raw ?: emptySet())
            .mapNotNull { entry ->
                val parts = entry.split(":")
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                id to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
            }
            .toMap()
    }

    // ---------- Escritura ----------

    suspend fun setSetting(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }
}

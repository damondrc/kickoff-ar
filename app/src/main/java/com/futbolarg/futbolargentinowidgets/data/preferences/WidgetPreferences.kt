package com.futbolarg.futbolargentinowidgets.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.futbolarg.futbolargentinowidgets.domain.model.Team
import com.futbolarg.futbolargentinowidgets.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// WidgetPreferences.kt
// ============================================================
// Guarda QUÉ EQUIPO sigue CADA instancia de widget.
//
// El usuario puede tener varios widgets en la pantalla de
// inicio, cada uno con un equipo distinto. Android identifica
// cada instancia con un appWidgetId (Int), así que usamos
// claves dinámicas por id:
//
//   team_id_42   = 16
//   team_name_42 = "River Plate"
//   team_abbr_42 = "RIV"
//   team_logo_42 = "https://..."
//
// Cuando el widget se elimina, borramos sus claves.
// ============================================================

// DataStore a nivel de Context (singleton por proceso)
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.PREFERENCES_NAME
)

@Singleton
class WidgetPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun teamIdKey(widgetId: Int) = intPreferencesKey("team_id_$widgetId")
    private fun teamNameKey(widgetId: Int) = stringPreferencesKey("team_name_$widgetId")
    private fun teamAbbrKey(widgetId: Int) = stringPreferencesKey("team_abbr_$widgetId")
    private fun teamLogoKey(widgetId: Int) = stringPreferencesKey("team_logo_$widgetId")

    // Guardar el equipo elegido para una instancia de widget
    suspend fun saveTeamForWidget(widgetId: Int, team: Team) {
        context.dataStore.edit { prefs ->
            prefs[teamIdKey(widgetId)] = team.id
            prefs[teamNameKey(widgetId)] = team.name
            prefs[teamAbbrKey(widgetId)] = team.abbreviation
            prefs[teamLogoKey(widgetId)] = team.logoUrl
        }
    }

    // Leer el equipo de una instancia (null si no está configurada)
    suspend fun getTeamForWidget(widgetId: Int): Team? {
        val prefs = context.dataStore.data.first()
        val id = prefs[teamIdKey(widgetId)] ?: return null
        return Team(
            id = id,
            name = prefs[teamNameKey(widgetId)] ?: "",
            abbreviation = prefs[teamAbbrKey(widgetId)] ?: "",
            logoUrl = prefs[teamLogoKey(widgetId)] ?: ""
        )
    }

    // IDs de todos los equipos seguidos por algún widget
    // (lo usa el programador de sync para saber qué partidos importan)
    suspend fun getAllFollowedTeamIds(): List<Int> {
        val prefs = context.dataStore.data.first()
        return prefs.asMap()
            .filterKeys { it.name.startsWith("team_id_") }
            .values
            .filterIsInstance<Int>()
            .distinct()
    }

    // Limpiar las claves de una instancia eliminada
    suspend fun deleteWidget(widgetId: Int) {
        context.dataStore.edit { prefs ->
            prefs.remove(teamIdKey(widgetId))
            prefs.remove(teamNameKey(widgetId))
            prefs.remove(teamAbbrKey(widgetId))
            prefs.remove(teamLogoKey(widgetId))
        }
    }
}

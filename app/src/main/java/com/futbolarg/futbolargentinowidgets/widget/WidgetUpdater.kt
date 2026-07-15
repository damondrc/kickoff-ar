package com.futbolarg.futbolargentinowidgets.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import coil.ImageLoader
import coil.request.ImageRequest
import com.futbolarg.futbolargentinowidgets.data.preferences.WidgetPreferences
import com.futbolarg.futbolargentinowidgets.data.repository.MatchRepository
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.util.Constants
import com.futbolarg.futbolargentinowidgets.util.DateFormatting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// WidgetUpdater.kt
// ============================================================
// El ÚNICO lugar que decide qué muestra cada widget.
//
// Para cada instancia:
//   1. Lee qué equipo sigue (WidgetPreferences, por appWidgetId)
//   2. Le pide a MatchRepository el partido relevante (Room)
//   3. Descarga/cachea los escudos como PNG en filesDir
//   4. Escribe textos ya formateados en el ESTADO GLANCE
//   5. Llama a update() → Glance recompone el widget
//
// El widget en sí (NextMatchWidget) queda "tonto": solo lee
// currentState() y dibuja. Así la recomposición siempre refleja
// los datos nuevos (ver lección en WidgetStateKeys).
//
// Lo usan: MatchSyncWorker (tras cada sync, todas las
// instancias) y WidgetConfigViewModel (la instancia recién
// configurada).
// ============================================================

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MatchRepository,
    private val widgetPreferences: WidgetPreferences
) {

    companion object {
        private const val TAG = "WidgetUpdater"
    }

    // Actualizar TODAS las instancias del widget
    suspend fun updateAllWidgets() {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(NextMatchWidget::class.java)
        Log.d(TAG, "Actualizando ${glanceIds.size} widget(s)")
        glanceIds.forEach { glanceId ->
            updateWidget(manager.getAppWidgetId(glanceId), glanceId)
        }
    }

    // Actualizar una instancia puntual (recién configurada).
    // try/catch defensivo: en algunos launchers el widget todavía
    // no está vinculado cuando corre la configuración; si pasa,
    // no rompemos el flujo — el syncNow() posterior lo actualiza
    // igual vía updateAllWidgets().
    suspend fun updateWidget(appWidgetId: Int) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            updateWidget(appWidgetId, glanceId)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo actualizar widget $appWidgetId: ${e.message}")
        }
    }

    // ========================================================
    // Núcleo: calcular y escribir el estado de UNA instancia
    // ========================================================
    private suspend fun updateWidget(appWidgetId: Int, glanceId: GlanceId) {
        val team = widgetPreferences.getTeamForWidget(appWidgetId)
        val match = team?.let { repository.getWidgetMatch(it.id) }

        Log.d(TAG, "Widget $appWidgetId → equipo=${team?.name}, partido=${match?.id}")

        // Escudos (solo descarga la primera vez, después lee disco)
        val homeLogoPath = match?.let { cacheLogo(it.homeTeamId, it.homeTeamLogo) } ?: ""
        val awayLogoPath = match?.let { cacheLogo(it.awayTeamId, it.awayTeamLogo) } ?: ""

        // Partidos siguientes al principal (para el tamaño expandido):
        // pedimos 3 y descartamos el que ya se muestra como principal
        val upcoming = if (team != null) {
            repository.getNextScheduledMatches(team.id, limit = 3)
                .filter { it.id != match?.id }
                .take(2)
                .map { buildUpcomingLine(it, team.id) }
        } else {
            emptyList()
        }

        updateAppWidgetState(context, glanceId) { prefs ->
            if (team == null) {
                prefs[WidgetStateKeys.CONFIGURED] = false
                return@updateAppWidgetState
            }

            prefs[WidgetStateKeys.CONFIGURED] = true
            prefs[WidgetStateKeys.TEAM_NAME] = team.name

            if (match == null) {
                // Configurado pero sin datos todavía
                prefs[WidgetStateKeys.LINE_MAIN] = ""
                prefs[WidgetStateKeys.LINE_STATUS] = ""
                prefs[WidgetStateKeys.IS_LIVE] = false
                prefs[WidgetStateKeys.HOME_LOGO_PATH] = ""
                prefs[WidgetStateKeys.AWAY_LOGO_PATH] = ""
            } else {
                prefs[WidgetStateKeys.LINE_MAIN] = buildMainLine(match)
                prefs[WidgetStateKeys.LINE_STATUS] = buildStatusLine(match)
                prefs[WidgetStateKeys.IS_LIVE] = match.status.isLive
                prefs[WidgetStateKeys.HOME_LOGO_PATH] = homeLogoPath
                prefs[WidgetStateKeys.AWAY_LOGO_PATH] = awayLogoPath
            }

            // Siguientes partidos (solo visibles en tamaño expandido)
            prefs[WidgetStateKeys.UPCOMING_1] = upcoming.getOrElse(0) { "" }
            prefs[WidgetStateKeys.UPCOMING_2] = upcoming.getOrElse(1) { "" }
        }

        // Disparar la recomposición con el estado nuevo
        NextMatchWidget().update(context, glanceId)
    }

    // ========================================================
    // Textos del widget (formateados acá, no en la composición)
    // ========================================================

    private fun buildMainLine(match: Match): String {
        return if (match.status.isLive || match.status.isFinished) {
            "${match.homeTeamAbbr} ${match.homeScore ?: 0} - " +
                "${match.awayScore ?: 0} ${match.awayTeamAbbr}"
        } else {
            "${match.homeTeamAbbr} vs ${match.awayTeamAbbr}"
        }
    }

    // Línea de "próximo partido" para el widget expandido, desde
    // la perspectiva del equipo seguido: "vs CAT · dom 26 jul · 16:00"
    private fun buildUpcomingLine(match: Match, teamId: Int): String {
        val rivalAbbr = if (match.isHome(teamId)) match.awayTeamAbbr else match.homeTeamAbbr
        return "vs $rivalAbbr · ${DateFormatting.formatKickoff(match.kickoffMillis)}"
    }

    private fun buildStatusLine(match: Match): String {
        val base = when {
            match.status.isLive -> "● Jugando"
            match.status.isFinished -> "Final"
            else -> DateFormatting.formatKickoff(match.kickoffMillis)
        }

        // Si el partido NO es de la liga (copa o torneo continental),
        // agregamos la competición: "sáb 12 jul · 16:15 · Copa Argentina".
        // Para la liga no, para mantener el widget minimalista.
        return if (match.leagueName != Constants.DOMESTIC_LEAGUE_NAME && match.leagueName.isNotBlank()) {
            "$base · ${match.leagueName}"
        } else {
            base
        }
    }

    // ========================================================
    // Cache de escudos en disco
    // ========================================================
    // Los RemoteViews del widget necesitan Bitmaps locales.
    // Descargamos el PNG una vez a filesDir/logos/{teamId}.png;
    // la composición después lo decodifica sincrónicamente.
    // ========================================================
    private suspend fun cacheLogo(teamId: Int, url: String): String {
        if (url.isBlank()) return ""

        val dir = File(context.filesDir, "logos").apply { mkdirs() }
        val file = File(dir, "$teamId.png")
        if (file.exists()) return file.absolutePath

        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(96)
                .build()
            val drawable = ImageLoader(context).execute(request).drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return ""

            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo descargar escudo $url: ${e.message}")
            ""
        }
    }
}

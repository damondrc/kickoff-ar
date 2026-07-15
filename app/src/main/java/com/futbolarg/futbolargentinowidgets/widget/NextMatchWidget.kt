package com.futbolarg.futbolargentinowidgets.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.futbolarg.futbolargentinowidgets.MainActivity

// ============================================================
// NextMatchWidget.kt
// ============================================================
// El widget minimalista. Según el momento muestra:
//
//   PROGRAMADO →  [escudo] RIV vs BOC [escudo]
//                 dom 26 jul · 18:30
//
//   EN JUEGO   →  [escudo] RIV 1 - 0 BOC [escudo]
//                 ● Jugando
//
//   TERMINADO  →  [escudo] RIV 2 - 1 BOC [escudo]
//                 Final
//
// ARQUITECTURA (v2, tras el bug del estado congelado):
// este widget es "tonto" a propósito. NO consulta Room ni
// DataStore ni internet: SOLO lee su estado Glance con
// currentState(). Quien calcula y escribe ese estado es
// WidgetUpdater. Cuando él llama a update(), Glance recompone
// este contenido con los valores nuevos — esa es la única vía
// de actualización, y por eso siempre refleja datos frescos.
// ============================================================

class NextMatchWidget : GlanceAppWidget() {

    companion object {
        // Tamaños que el widget distingue. Glance recompone con
        // el más cercano al tamaño real cuando el usuario lo
        // redimensiona (SizeMode.Responsive).
        val COMPACT = DpSize(180.dp, 60.dp)    // 3x1: solo el próximo partido
        val EXPANDED = DpSize(180.dp, 130.dp)  // 3x2: + los 2 siguientes
    }

    // Estado por instancia basado en Preferences (lo que escribe
    // WidgetUpdater con updateAppWidgetState)
    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT, EXPANDED)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // OJO: nada de cargar datos acá afuera — este bloque corre
        // una sola vez por sesión del widget. Todo va adentro de
        // la composición, leyendo currentState().
        provideContent {
            WidgetContent()
        }
    }
}

// ============================================================
// UI del widget (Glance composables)
// ============================================================

// Paleta ÚNICA y uniforme del widget (v1.1: se quitó el tinte
// por color de club — rompía la legibilidad con varios equipos)
private val BG_COLOR = ColorProvider(Color(0xE6101418))
private val TEXT_PRIMARY = ColorProvider(Color(0xFFFFFFFF))
private val TEXT_SECONDARY = ColorProvider(Color(0xFFB0BEC5))
private val LIVE_COLOR = ColorProvider(Color(0xFF4CAF50))    // verde "en vivo"

@Composable
private fun WidgetContent() {
    // Estado escrito por WidgetUpdater. Si él vuelve a escribir
    // y llama update(), esta composición se re-ejecuta.
    val prefs = currentState<Preferences>()

    val configured = prefs[WidgetStateKeys.CONFIGURED] ?: false
    val teamName = prefs[WidgetStateKeys.TEAM_NAME] ?: ""
    val lineMain = prefs[WidgetStateKeys.LINE_MAIN] ?: ""
    val lineStatus = prefs[WidgetStateKeys.LINE_STATUS] ?: ""
    val isLive = prefs[WidgetStateKeys.IS_LIVE] ?: false
    val homeLogoPath = prefs[WidgetStateKeys.HOME_LOGO_PATH] ?: ""
    val awayLogoPath = prefs[WidgetStateKeys.AWAY_LOGO_PATH] ?: ""
    val upcoming1 = prefs[WidgetStateKeys.UPCOMING_1] ?: ""
    val upcoming2 = prefs[WidgetStateKeys.UPCOMING_2] ?: ""

    // ¿En qué tamaño nos está dibujando el launcher? Con
    // SizeMode.Responsive, LocalSize devuelve uno de los tamaños
    // declarados (COMPACT o EXPANDED), el más cercano al real.
    val isExpanded = LocalSize.current.height >= NextMatchWidget.EXPANDED.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BG_COLOR)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            // Recién agregado, sin configuración (no debería verse:
            // la pantalla de selección es obligatoria al agregarlo)
            !configured -> {
                Text(
                    text = "Configurando…",
                    style = TextStyle(color = TEXT_SECONDARY, fontSize = 13.sp)
                )
            }

            // Configurado pero el primer sync no terminó / sin internet
            lineMain.isEmpty() -> {
                Text(
                    text = teamName,
                    style = TextStyle(
                        color = TEXT_PRIMARY,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Sincronizando fixture…",
                    style = TextStyle(color = TEXT_SECONDARY, fontSize = 12.sp)
                )
            }

            else -> {
                MatchRow(
                    lineMain = lineMain,
                    lineStatus = lineStatus,
                    isLive = isLive,
                    homeLogoPath = homeLogoPath,
                    awayLogoPath = awayLogoPath
                )

                // Tamaño expandido: los siguientes partidos debajo
                if (isExpanded && upcoming1.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    UpcomingRow(upcoming1)
                    if (upcoming2.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        UpcomingRow(upcoming2)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(text: String) {
    Text(
        text = text,
        style = TextStyle(color = TEXT_SECONDARY, fontSize = 12.sp)
    )
}

@Composable
private fun MatchRow(
    lineMain: String,
    lineStatus: String,
    isLive: Boolean,
    homeLogoPath: String,
    awayLogoPath: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Logo(homeLogoPath)
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = lineMain,
            style = TextStyle(
                color = TEXT_PRIMARY,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Logo(awayLogoPath)
    }

    Spacer(modifier = GlanceModifier.height(4.dp))

    Text(
        text = lineStatus,
        style = TextStyle(
            color = if (isLive) LIVE_COLOR else TEXT_SECONDARY,
            fontSize = 12.sp,
            fontWeight = if (isLive) FontWeight.Medium else FontWeight.Normal
        )
    )
}

@Composable
private fun Logo(path: String) {
    // Decodificar desde disco es rápido (PNG de 96px ya cacheado
    // por WidgetUpdater). remember evita re-decodificar en cada
    // recomposición con la misma ruta.
    val bitmap: Bitmap? = remember(path) {
        if (path.isEmpty()) null else BitmapFactory.decodeFile(path)
    }

    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp)
        )
    } else {
        // Espacio reservado para que el layout no "salte"
        Spacer(modifier = GlanceModifier.size(28.dp))
    }
}

package com.futbolarg.futbolargentinowidgets.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.futbolarg.futbolargentinowidgets.data.preferences.WidgetPreferences
import com.futbolarg.futbolargentinowidgets.work.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================
// NextMatchWidgetReceiver.kt
// ============================================================
// El BroadcastReceiver que Android usa para comunicarse con
// nuestro widget (agregar, actualizar, eliminar instancias).
// Glance hace casi todo; nosotros solo agregamos limpieza.
//
// @AndroidEntryPoint funciona en receivers, así que acá sí
// podemos inyectar directamente.
// ============================================================

@AndroidEntryPoint
class NextMatchWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = NextMatchWidget()

    @Inject
    lateinit var widgetPreferences: WidgetPreferences

    @Inject
    lateinit var syncScheduler: SyncScheduler

    // El usuario eliminó una o más instancias del widget:
    // borramos sus preferencias y reprogramamos el sync
    // (quizás ya no haya que seguir a ese equipo)
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        // goAsync mantiene vivo el receiver mientras corre la corrutina
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { widgetPreferences.deleteWidget(it) }
                syncScheduler.scheduleNext()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

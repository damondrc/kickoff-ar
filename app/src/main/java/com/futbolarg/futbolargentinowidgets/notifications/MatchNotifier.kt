package com.futbolarg.futbolargentinowidgets.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.futbolarg.futbolargentinowidgets.MainActivity
import com.futbolarg.futbolargentinowidgets.R
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.util.Constants
import com.futbolarg.futbolargentinowidgets.util.DateFormatting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// MatchNotifier.kt
// ============================================================
// Construye y muestra las tres notificaciones de la app:
//
//   1. "Por empezar"  → ~30 min antes del kickoff
//   2. "Arrancó"      → cuando el sync detecta que pasó a vivo
//   3. "Suspendido"   → cuando el fixture cambia a
//                       postergado/cancelado
//
// El id de notificación deriva del id del partido + el tipo,
// así un mismo partido puede tener su aviso previo y el de
// inicio sin pisarse, pero nunca duplica el mismo aviso.
//
// PERMISO: en Android 13+ notificar requiere POST_NOTIFICATIONS
// (se pide desde la pantalla de ajustes al activar un switch).
// areNotificationsEnabled() cubre el caso de permiso revocado:
// simplemente no se muestra, sin crashear.
// ============================================================

@Singleton
class MatchNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    init {
        createChannel()
    }

    // Canal obligatorio desde Android 8 (lo creamos una sola
    // vez; volver a crearlo es un no-op)
    private fun createChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            "Partidos",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos de partidos de tus equipos"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun notifyPreMatch(match: Match) {
        show(
            id = (match.id * 10 + 1).toInt(),
            title = "Ya casi arranca",
            text = "${match.homeTeamName} vs ${match.awayTeamName} · " +
                DateFormatting.formatKickoff(match.kickoffMillis)
        )
    }

    fun notifyKickoff(match: Match) {
        show(
            id = (match.id * 10 + 2).toInt(),
            title = "¡Arrancó el partido!",
            text = "${match.homeTeamAbbr} vs ${match.awayTeamAbbr} está en juego"
        )
    }

    fun notifyFinished(match: Match) {
        show(
            id = (match.id * 10 + 3).toInt(),
            title = "Final del partido",
            text = "${match.homeTeamAbbr} ${match.homeScore ?: 0} - " +
                "${match.awayScore ?: 0} ${match.awayTeamAbbr}"
        )
    }

    private fun show(id: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        // Sin permiso (Android 13+) o canal bloqueado: salir sin crashear
        if (!manager.areNotificationsEnabled()) return

        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Permiso revocado entre el check y el notify: ignorar
        }
    }
}

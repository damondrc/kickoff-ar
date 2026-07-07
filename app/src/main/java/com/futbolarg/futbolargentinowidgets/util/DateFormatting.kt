package com.futbolarg.futbolargentinowidgets.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================================================
// DateFormatting.kt
// ============================================================
// Formatea el kickoff (epoch millis UTC) en la ZONA HORARIA
// LOCAL del teléfono, en castellano.
//
// Resultados:
//   - hoy     → "Hoy 18:30"
//   - mañana  → "Mañana 18:30"
//   - resto   → "dom 26 jul · 18:30"
// ============================================================

object DateFormatting {

    private val LOCALE_ES = Locale("es", "AR")

    private val TIME = DateTimeFormatter.ofPattern("HH:mm", LOCALE_ES)
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", LOCALE_ES)

    fun formatKickoff(kickoffMillis: Long): String {
        val zone = ZoneId.systemDefault()
        val dateTime = Instant.ofEpochMilli(kickoffMillis).atZone(zone)
        val today = LocalDate.now(zone)

        val time = dateTime.format(TIME)

        return when (dateTime.toLocalDate()) {
            today -> "Hoy $time"
            today.plusDays(1) -> "Mañana $time"
            else -> "${dateTime.format(DAY)} · $time"
        }
    }

    // Encabezado de grupo para la lista de partidos:
    // "Hoy", "Mañana" o "vie 17 jul"
    fun formatDayHeader(kickoffMillis: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(kickoffMillis).atZone(zone)
        val today = LocalDate.now(zone)

        return when (date.toLocalDate()) {
            today -> "Hoy"
            today.plusDays(1) -> "Mañana"
            else -> date.format(DAY)
        }
    }

    // Solo la hora local: "19:15" (para filas agrupadas por día)
    fun formatTime(kickoffMillis: Long): String {
        return Instant.ofEpochMilli(kickoffMillis)
            .atZone(ZoneId.systemDefault())
            .format(TIME)
    }
}

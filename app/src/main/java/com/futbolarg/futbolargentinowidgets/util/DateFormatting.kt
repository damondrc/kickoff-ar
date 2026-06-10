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
}

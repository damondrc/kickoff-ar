package com.futbolarg.futbolargentinowidgets.domain.model

// ============================================================
// MatchStatus.kt
// ============================================================
// Estados posibles de un partido, mapeados desde la API de ESPN.
//
// ESPN describe el estado con dos campos:
//   - state: "pre" | "in" | "post"  (la fase general)
//   - name:  "STATUS_SCHEDULED", "STATUS_FIRST_HALF", etc.
//
// Estrategia de mapeo (de lo general a lo específico):
//   state "pre"  → SCHEDULED (o POSTPONED/CANCELLED según name)
//   state "in"   → intentamos detectar 1T/HT/2T; si no, LIVE
//   state "post" → FINISHED (o POSTPONED/CANCELLED según name)
//
// En Room guardamos el nombre del enum (ej: "SCHEDULED"),
// nunca los códigos de ESPN.
// ============================================================

enum class MatchStatus {
    SCHEDULED,      // Programado, no empezó
    FIRST_HALF,     // Primer tiempo
    HALFTIME,       // Entretiempo
    SECOND_HALF,    // Segundo tiempo
    LIVE,           // En juego (cuando ESPN no detalla la fase)
    FINISHED,       // Terminado
    POSTPONED,      // Postergado
    CANCELLED,      // Cancelado
    UNKNOWN;        // No reconocido

    // ¿El partido está en curso ahora mismo?
    val isLive: Boolean
        get() = this == FIRST_HALF || this == HALFTIME ||
                this == SECOND_HALF || this == LIVE

    // ¿El partido ya terminó (con resultado válido)?
    val isFinished: Boolean
        get() = this == FINISHED

    companion object {

        // Convierte el par (state, name) de ESPN a nuestro enum
        fun fromEspn(state: String?, name: String?): MatchStatus {
            // Casos especiales que pueden aparecer en cualquier fase
            when (name) {
                "STATUS_POSTPONED" -> return POSTPONED
                "STATUS_CANCELED", "STATUS_CANCELLED" -> return CANCELLED
            }

            return when (state) {
                "pre" -> SCHEDULED
                "in" -> when (name) {
                    "STATUS_FIRST_HALF" -> FIRST_HALF
                    "STATUS_HALFTIME" -> HALFTIME
                    "STATUS_SECOND_HALF" -> SECOND_HALF
                    else -> LIVE
                }
                "post" -> FINISHED
                else -> UNKNOWN
            }
        }

        // Convierte el String guardado en Room de vuelta al enum
        fun fromName(name: String): MatchStatus {
            return entries.find { it.name == name } ?: UNKNOWN
        }
    }
}

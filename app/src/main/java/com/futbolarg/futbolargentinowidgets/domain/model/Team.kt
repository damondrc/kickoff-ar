package com.futbolarg.futbolargentinowidgets.domain.model

// ============================================================
// Team.kt
// ============================================================
// Modelo de dominio de un equipo. Se usa en la pantalla de
// configuración del widget (lista de los 30 equipos de la liga).
// ============================================================

data class Team(
    val id: Int,
    val name: String,       // "River Plate"
    val abbreviation: String, // "RIV"
    val logoUrl: String,
    // Color oficial del club en hex SIN "#" (ej: "C60000").
    // Vacío si la fuente no lo provee.
    val colorHex: String = ""
)

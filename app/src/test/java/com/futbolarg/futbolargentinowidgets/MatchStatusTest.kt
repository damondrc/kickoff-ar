package com.futbolarg.futbolargentinowidgets

import com.futbolarg.futbolargentinowidgets.domain.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ============================================================
// MatchStatusTest.kt
// ============================================================
// Tests del mapeo de estados de ESPN a nuestro enum: la pieza
// de lógica de la que depende TODO el comportamiento del widget
// (qué se muestra, cuándo se notifica, cómo se programa el sync).
// ============================================================

class MatchStatusTest {

    // ---------- fromEspn: fases básicas ----------

    @Test
    fun `estado pre mapea a SCHEDULED`() {
        assertEquals(
            MatchStatus.SCHEDULED,
            MatchStatus.fromEspn("pre", "STATUS_SCHEDULED")
        )
    }

    @Test
    fun `estado post mapea a FINISHED`() {
        assertEquals(
            MatchStatus.FINISHED,
            MatchStatus.fromEspn("post", "STATUS_FULL_TIME")
        )
    }

    @Test
    fun `fases del partido en vivo se distinguen`() {
        assertEquals(MatchStatus.FIRST_HALF, MatchStatus.fromEspn("in", "STATUS_FIRST_HALF"))
        assertEquals(MatchStatus.HALFTIME, MatchStatus.fromEspn("in", "STATUS_HALFTIME"))
        assertEquals(MatchStatus.SECOND_HALF, MatchStatus.fromEspn("in", "STATUS_SECOND_HALF"))
    }

    @Test
    fun `en vivo con nombre desconocido cae en LIVE generico`() {
        assertEquals(
            MatchStatus.LIVE,
            MatchStatus.fromEspn("in", "STATUS_ALGO_NUEVO_DE_ESPN")
        )
    }

    // ---------- fromEspn: casos especiales ----------

    @Test
    fun `postergado y cancelado ganan sin importar la fase`() {
        assertEquals(MatchStatus.POSTPONED, MatchStatus.fromEspn("pre", "STATUS_POSTPONED"))
        assertEquals(MatchStatus.CANCELLED, MatchStatus.fromEspn("post", "STATUS_CANCELED"))
        // ESPN usa las dos grafías en distintos endpoints
        assertEquals(MatchStatus.CANCELLED, MatchStatus.fromEspn("pre", "STATUS_CANCELLED"))
    }

    @Test
    fun `estado nulo o desconocido no crashea`() {
        assertEquals(MatchStatus.UNKNOWN, MatchStatus.fromEspn(null, null))
        assertEquals(MatchStatus.UNKNOWN, MatchStatus.fromEspn("otra_cosa", null))
    }

    // ---------- fromName (lo que llega del proxy / de Room) ----------

    @Test
    fun `fromName reconoce nombres validos y degrada los invalidos`() {
        assertEquals(MatchStatus.FINISHED, MatchStatus.fromName("FINISHED"))
        assertEquals(MatchStatus.UNKNOWN, MatchStatus.fromName("BASURA"))
        assertEquals(MatchStatus.UNKNOWN, MatchStatus.fromName(""))
    }

    // ---------- Los helpers que usa el widget ----------

    @Test
    fun `isLive cubre exactamente los cuatro estados en juego`() {
        assertTrue(MatchStatus.FIRST_HALF.isLive)
        assertTrue(MatchStatus.HALFTIME.isLive)
        assertTrue(MatchStatus.SECOND_HALF.isLive)
        assertTrue(MatchStatus.LIVE.isLive)
        assertFalse(MatchStatus.SCHEDULED.isLive)
        assertFalse(MatchStatus.FINISHED.isLive)
        assertFalse(MatchStatus.POSTPONED.isLive)
    }

    @Test
    fun `isFinished solo es cierto para FINISHED`() {
        assertTrue(MatchStatus.FINISHED.isFinished)
        assertFalse(MatchStatus.CANCELLED.isFinished)
        assertFalse(MatchStatus.LIVE.isFinished)
    }
}

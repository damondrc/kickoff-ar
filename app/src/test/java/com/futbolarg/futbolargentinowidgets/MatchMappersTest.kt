package com.futbolarg.futbolargentinowidgets

import com.futbolarg.futbolargentinowidgets.data.mapper.parseEspnDate
import com.futbolarg.futbolargentinowidgets.data.mapper.toEntity
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnCompetitionDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnCompetitorDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnEventDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnStatusDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnStatusTypeDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.EspnTeamDto
import com.futbolarg.futbolargentinowidgets.data.remote.dto.ProxyMatchDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import java.time.Instant
import org.junit.Test

// ============================================================
// MatchMappersTest.kt
// ============================================================
// Tests de la transformación ESPN/proxy → Room. Cubren las
// particularidades reales de la fuente que descubrimos
// depurando: fechas sin segundos, marcador "0" antes de
// empezar, y el orden no garantizado de local/visitante.
// ============================================================

class MatchMappersTest {

    // ---------- Constructores de datos de prueba ----------

    private fun competitor(homeAway: String, teamId: String, abbr: String, score: String) =
        EspnCompetitorDto(
            homeAway = homeAway,
            score = score,
            team = EspnTeamDto(
                id = teamId,
                displayName = "Equipo $abbr",
                abbreviation = abbr,
                logo = "https://logo/$teamId.png"
            )
        )

    private fun event(
        id: String? = "401873392",
        date: String? = "2026-07-26T19:00Z",
        state: String = "pre",
        statusName: String = "STATUS_SCHEDULED",
        competitors: List<EspnCompetitorDto> = listOf(
            competitor("home", "16", "RIV", "0"),
            competitor("away", "9785", "CAT", "0")
        )
    ) = EspnEventDto(
        id = id,
        date = date,
        competitions = listOf(
            EspnCompetitionDto(
                status = EspnStatusDto(EspnStatusTypeDto(statusName, state, state == "post")),
                competitors = competitors
            )
        )
    )

    // ---------- parseEspnDate ----------

    @Test
    fun `parsea fecha con y sin segundos al mismo instante`() {
        val conSegundos = parseEspnDate("2026-05-24T18:30:00Z")
        val sinSegundos = parseEspnDate("2026-05-24T18:30Z")

        assertNotNull(conSegundos)
        assertEquals(conSegundos, sinSegundos)
        assertEquals(
            Instant.parse("2026-05-24T18:30:00Z").toEpochMilli(),
            conSegundos
        )
    }

    @Test
    fun `fecha invalida devuelve null en lugar de crashear`() {
        assertNull(parseEspnDate("no-es-fecha"))
        assertNull(parseEspnDate(""))
    }

    // ---------- EspnEventDto.toEntity ----------

    @Test
    fun `mapea local y visitante por homeAway aunque vengan invertidos`() {
        // ESPN lista al visitante primero en shortName y a veces
        // en competitors: el orden NUNCA debe importar
        val invertido = event(
            competitors = listOf(
                competitor("away", "9785", "CAT", "0"),
                competitor("home", "16", "RIV", "0")
            )
        )

        val entity = invertido.toEntity("Liga Profesional")!!

        assertEquals(16, entity.homeTeamId)
        assertEquals("RIV", entity.homeTeamAbbr)
        assertEquals(9785, entity.awayTeamId)
        assertEquals("CAT", entity.awayTeamAbbr)
        assertEquals("Liga Profesional", entity.leagueName)
    }

    @Test
    fun `antes de empezar los marcadores son null aunque ESPN mande cero`() {
        val entity = event(state = "pre").toEntity("Liga Profesional")!!

        assertNull(entity.homeScore)
        assertNull(entity.awayScore)
        assertEquals("SCHEDULED", entity.status)
    }

    @Test
    fun `terminado conserva el marcador real`() {
        val terminado = event(
            state = "post",
            statusName = "STATUS_FULL_TIME",
            competitors = listOf(
                competitor("home", "16", "RIV", "2"),
                competitor("away", "9785", "CAT", "1")
            )
        )

        val entity = terminado.toEntity("Liga Profesional")!!

        assertEquals(2, entity.homeScore)
        assertEquals(1, entity.awayScore)
        assertEquals("FINISHED", entity.status)
    }

    @Test
    fun `eventos incompletos se descartan sin crashear`() {
        // Sin id numérico
        assertNull(event(id = "abc").toEntity("Liga"))
        // Sin fecha parseable
        assertNull(event(date = "???").toEntity("Liga"))
        // Sin visitante
        assertNull(
            event(competitors = listOf(competitor("home", "16", "RIV", "0")))
                .toEntity("Liga")
        )
    }

    // ---------- ProxyMatchDto.toEntity ----------

    private fun proxyMatch(
        id: Long? = 401873392L,
        status: String? = "SCHEDULED"
    ) = ProxyMatchDto(
        id = id,
        league = "Copa Argentina",
        kickoffMillis = 1_784_000_000_000L,
        status = status,
        homeId = 16, homeName = "River Plate", homeAbbr = "RIV", homeLogo = "l1",
        awayId = 9785, awayName = "Atlético Tucumán", awayAbbr = "CAT", awayLogo = "l2",
        homeScore = null, awayScore = null
    )

    @Test
    fun `dto del proxy valido mapea completo`() {
        val entity = proxyMatch().toEntity()!!

        assertEquals(401873392L, entity.id)
        assertEquals("Copa Argentina", entity.leagueName)
        assertEquals("SCHEDULED", entity.status)
        assertEquals(16, entity.homeTeamId)
    }

    @Test
    fun `status desconocido del proxy degrada a UNKNOWN`() {
        val entity = proxyMatch(status = "ALGO_RARO").toEntity()!!
        assertEquals("UNKNOWN", entity.status)
    }

    @Test
    fun `dto del proxy sin id se descarta`() {
        assertNull(proxyMatch(id = null).toEntity())
    }
}

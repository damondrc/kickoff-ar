// ============================================================
// worker.js — Servidor de Kickoff AR (Cloudflare Worker)
// ============================================================
// Intermediario con caché entre la app y la API pública de ESPN.
//
//   App ──► este Worker ──► ESPN (solo si la caché expiró)
//
// Rutas:
//   GET /fixtures  → partidos de las 4 competiciones, JSON compacto
//   GET /teams     → los 30 equipos de la Liga Profesional
//
// ¿Por qué existe?
//   - Mil usuarios de la app generan sobre ESPN el mismo tráfico
//     que uno solo (la caché absorbe el resto).
//   - Si ESPN cambia su formato, se corrige AQUÍ, sin publicar
//     una actualización de la app.
//   - La app recibe un JSON mínimo ya transformado (~50 veces
//     más liviano que la respuesta cruda de ESPN).
//
// Cómo desplegarlo: pegar este archivo completo en el editor de
// dash.cloudflare.com → Workers & Pages → Create Worker → Deploy.
// ============================================================

// ------------------------------------------------------------
// Configuración
// ------------------------------------------------------------

// Competiciones que sincronizamos (slug de ESPN → nombre corto).
// Debe coincidir con Constants.LEAGUES de la app.
const LEAGUES = {
  "arg.1": "Liga Profesional",
  "arg.copa": "Copa Argentina",
  "conmebol.libertadores": "Libertadores",
  "conmebol.sudamericana": "Sudamericana",
};

const ESPN_BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer";

// Ventana del fixture: días hacia atrás y hacia adelante
const DAYS_BACK = 7;
const DAYS_FORWARD = 90;

// TTL de la caché en segundos (15 min). Durante este tiempo,
// TODAS las peticiones se responden desde la caché del borde
// sin tocar ESPN.
const CACHE_TTL_SECONDS = 900;

// ------------------------------------------------------------
// Punto de entrada: Cloudflare llama a fetch() en cada petición
// ------------------------------------------------------------
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // 1. ¿Tenemos esta respuesta en la caché del borde?
    //    La clave de caché es la URL completa de la petición.
    const cache = caches.default;
    const cached = await cache.match(request);
    if (cached) {
      return cached; // ¡Ni un byte hacia ESPN!
    }

    // 2. No hay caché fresca: construir la respuesta
    let data;
    try {
      if (url.pathname === "/fixtures") {
        data = await buildFixtures();
      } else if (url.pathname === "/teams") {
        data = await buildTeams();
      } else {
        return json({ error: "Rutas disponibles: /fixtures, /teams" }, 404);
      }
    } catch (e) {
      // Error hablando con ESPN: 502 = "el de más atrás falló".
      // Sin Cache-Control, así que NO se cachea el error.
      return json({ error: "Error consultando la fuente: " + e.message }, 502);
    }

    // 3. Responder y guardar en caché con el TTL configurado.
    //    "max-age" es lo que le dice a la caché cuánto vive.
    const response = json(data, 200, {
      "cache-control": `public, max-age=${CACHE_TTL_SECONDS}`,
    });

    // waitUntil = "termina esto en segundo plano aunque la
    // respuesta ya se haya enviado" (no retrasa al usuario)
    ctx.waitUntil(cache.put(request, response.clone()));

    return response;
  },
};

// ------------------------------------------------------------
// GET /fixtures
// ------------------------------------------------------------
// Consulta las 4 competiciones EN PARALELO (Promise.allSettled:
// si una falla, las demás siguen — misma filosofía que la app).
// ------------------------------------------------------------
async function buildFixtures() {
  const dates = dateRange();

  const requests = Object.entries(LEAGUES).map(async ([slug, name]) => {
    const res = await fetch(
      `${ESPN_BASE}/${slug}/scoreboard?dates=${dates}&limit=1000`,
      { headers: { "user-agent": "KickoffAR/1.0" } }
    );
    if (!res.ok) throw new Error(`${slug} HTTP ${res.status}`);
    const body = await res.json();
    return (body.events || [])
      .map((event) => transformEvent(event, name))
      .filter((m) => m !== null);
  });

  const results = await Promise.allSettled(requests);

  const matches = [];
  let okCount = 0;
  for (const r of results) {
    if (r.status === "fulfilled") {
      matches.push(...r.value);
      okCount++;
    }
  }

  // Si TODAS fallaron, propagamos el error (la app conservará
  // su último dato en Room, igual que hoy)
  if (okCount === 0) throw new Error("ninguna competición respondió");

  // Deduplicar por id y ordenar por fecha
  const unique = [...new Map(matches.map((m) => [m.id, m])).values()];
  unique.sort((a, b) => a.kickoffMillis - b.kickoffMillis);

  return {
    updatedAt: new Date().toISOString(),
    matches: unique,
  };
}

// Convierte un evento de ESPN al formato compacto de la app.
// Espejo de MatchMappers.toEntity() en Kotlin: mismos descartes,
// misma lógica de estados y de marcadores.
function transformEvent(event, leagueName) {
  const comp = event.competitions?.[0];
  if (!comp) return null;

  const home = comp.competitors?.find((c) => c.homeAway === "home");
  const away = comp.competitors?.find((c) => c.homeAway === "away");
  if (!home?.team || !away?.team) return null;

  const id = parseInt(event.id, 10);
  const kickoffMillis = Date.parse(event.date); // acepta "…T18:30Z"
  if (!id || !kickoffMillis) return null;

  const status = mapStatus(comp.status?.type);

  // ESPN manda score "0" antes de empezar: solo es marcador real
  // si el partido está en juego o terminado
  const live = ["FIRST_HALF", "HALFTIME", "SECOND_HALF", "LIVE"].includes(status);
  const scoresValid = live || status === "FINISHED";

  return {
    id,
    league: leagueName,
    kickoffMillis,
    status,
    homeId: parseInt(home.team.id, 10) || 0,
    homeName: home.team.displayName || "",
    homeAbbr: home.team.abbreviation || "",
    homeLogo: home.team.logo || "",
    awayId: parseInt(away.team.id, 10) || 0,
    awayName: away.team.displayName || "",
    awayAbbr: away.team.abbreviation || "",
    awayLogo: away.team.logo || "",
    homeScore: scoresValid ? toIntOrNull(home.score) : null,
    awayScore: scoresValid ? toIntOrNull(away.score) : null,
  };
}

// parseInt devuelve NaN (no null) cuando falla, y NaN rompería
// el JSON para la app. Esta función lo normaliza a null.
function toIntOrNull(value) {
  const n = parseInt(value, 10);
  return Number.isNaN(n) ? null : n;
}

// Espejo de MatchStatus.fromEspn() en Kotlin. El Worker envía
// directamente los NOMBRES del enum de la app, así el mapper
// Android es trivial (MatchStatus.fromName).
function mapStatus(type) {
  const name = type?.name;
  const state = type?.state;

  if (name === "STATUS_POSTPONED") return "POSTPONED";
  if (name === "STATUS_CANCELED" || name === "STATUS_CANCELLED") return "CANCELLED";

  switch (state) {
    case "pre":
      return "SCHEDULED";
    case "in":
      if (name === "STATUS_FIRST_HALF") return "FIRST_HALF";
      if (name === "STATUS_HALFTIME") return "HALFTIME";
      if (name === "STATUS_SECOND_HALF") return "SECOND_HALF";
      return "LIVE";
    case "post":
      return "FINISHED";
    default:
      return "UNKNOWN";
  }
}

// ------------------------------------------------------------
// GET /teams
// ------------------------------------------------------------
async function buildTeams() {
  const res = await fetch(`${ESPN_BASE}/arg.1/teams?limit=50`, {
    headers: { "user-agent": "KickoffAR/1.0" },
  });
  if (!res.ok) throw new Error(`teams HTTP ${res.status}`);
  const body = await res.json();

  const teams = body.sports?.[0]?.leagues?.[0]?.teams || [];
  return teams
    .map((t) => t.team)
    .filter((t) => t?.id && t?.displayName)
    .map((t) => ({
      id: parseInt(t.id, 10),
      name: t.displayName,
      abbr: t.abbreviation || "",
      logo: t.logos?.[0]?.href || "",
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

// ------------------------------------------------------------
// Utilidades
// ------------------------------------------------------------

// Rango "YYYYMMDD-YYYYMMDD" en UTC (formato que espera ESPN)
function dateRange() {
  const fmt = (d) => d.toISOString().slice(0, 10).replaceAll("-", "");
  const now = Date.now();
  const from = new Date(now - DAYS_BACK * 86400000);
  const to = new Date(now + DAYS_FORWARD * 86400000);
  return `${fmt(from)}-${fmt(to)}`;
}

// Respuesta JSON con headers correctos
function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      ...extraHeaders,
    },
  });
}

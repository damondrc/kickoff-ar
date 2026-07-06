# ⚽ Kickoff AR

Widget minimalista para Android que muestra el próximo partido de tu equipo de la **Liga Profesional Argentina**: cuándo juega, si está jugando, y el resultado final — directo en tu pantalla de inicio, sin abrir ninguna app.

## La historia

Este proyecto nació de la idea de un amigo argentino: quería una app *supersencilla* de configurar que simplemente le dijera cuándo jugaba su equipo favorito. Nada de apps pesadas, minuto a minuto, ni registros — por eso funciona con la liga argentina y por eso el widget tiene exactamente tres estados:

```
┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐
│   🛡  RIV vs CAT  🛡    │   │   🛡  RIV 1-0 CAT  🛡   │   │   🛡  RIV 2-1 CAT  🛡   │
│    dom 26 jul · 16:00   │ → │       ● Jugando         │ → │         Final           │
└─────────────────────────┘   └─────────────────────────┘   └─────────────────────────┘
```

El diseño final es una mezcla de esa idea original y de las adaptaciones que fui haciendo en el camino: este es mi primer proyecto en territorio Android, así que varias decisiones (la fuente de datos, el modelo de actualización, la arquitectura del widget) fueron evolucionando a medida que el terreno me lo iba enseñando.

## Características

El widget se configura **por instancia**: puedes tener varios en pantalla, cada uno siguiendo un equipo distinto de los 30 de la liga. Y responde a la pregunta completa "¿cuándo juega mi equipo?": sincroniza **todas las competiciones** que disputan los clubes argentinos — Liga Profesional, Copa Argentina, Libertadores y Sudamericana — y muestra el partido que venga primero, indicando el torneo cuando no es de liga. Las notificaciones son opcionales y configurables desde la app: aviso ~30 minutos antes del partido, al comenzar, y al finalizar con el resultado.

La filosofía de actualización es lo central del diseño: **el widget no hace polling**. Sabe a qué hora es el partido, así que sincroniza una vez al inicio, re-chequea cada 30 minutos mientras se juega, muestra el resultado al final, y recién vuelve a actualizar para el siguiente partido. Más un sync diario de respaldo que capta cambios de fixture. Resultado: consumo de batería y datos prácticamente nulo.

## Datos y backend

La app consume un **backend propio**: un Cloudflare Worker (`server/worker.js`) que actúa como proxy con caché de borde sobre la API pública de ESPN. El Worker consulta las cuatro competiciones en paralelo, transforma la respuesta a un JSON compacto (~50× más liviano) con los estados ya normalizados, y la cachea 15 minutos — así, todos los usuarios juntos generan sobre ESPN el tráfico de uno solo, y un cambio de formato en la fuente se corrige en el servidor sin publicar actualización de la app.

- `GET /fixtures` — partidos de Liga Profesional, Copa Argentina, Libertadores y Sudamericana
- `GET /teams` — los 30 equipos de la liga, con escudos

La app conserva además el modo directo a ESPN (flag `USE_PROXY` en `Constants.kt`), útil para desarrollo.

## Stack técnico

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Widget | Glance App Widgets (estado por instancia con `currentState`) |
| Datos remotos | Retrofit + OkHttp + Gson |
| Cache local | Room (offline-first: el widget siempre lee de la DB) |
| Preferencias | DataStore (equipo por widget + ajustes globales) |
| Tareas en background | WorkManager (workers autoprogramados) |
| Inyección de dependencias | Hilt (incluye `@HiltWorker`) |
| Imágenes | Coil (escudos cacheados en disco para el widget) |
| Backend | Cloudflare Workers (proxy con caché de borde, JS) |

## Arquitectura

```
ESPN API ──► Cloudflare Worker ──► App (Retrofit) ──► Room
             (caché 15 min,                           (única fuente
              JSON compacto)                           de verdad)
                                                        │
                                          ┌─────────────┼─────────────┐
                                          ▼             ▼             ▼
                                    MatchSyncWorker  WidgetUpdater  MainActivity
                                    (autoprogramado) (escribe estado (fixture +
                                                      Glance)        ajustes)
                                                        │
                                                        ▼
                                                  NextMatchWidget
                                                  (solo lee currentState)
```

## Lo que aprendí en el camino

Siendo mi primer proyecto Android, cada capa dejó una lección concreta. Las APIs deportivas comerciales restringen sus planes gratuitos hasta volverlos inservibles para datos actuales, y la solución fue encontrar una fuente pública estable y leer con cuidado sus particularidades (fechas UTC sin segundos, marcadores que existen antes de que el partido empiece). En Glance aprendí — depurando en dispositivo físico — que los datos cargados en `provideGlance` quedan congelados tras la primera composición: la solución correcta es un widget "tonto" que solo renderiza su estado (`currentState`) y un componente aparte (`WidgetUpdater`) como único escritor de ese estado. Con WorkManager, que un worker que re-encola su propio nombre único con `REPLACE` se cancela a sí mismo (de ahí `APPEND_OR_REPLACE`). Y de forma más general: que un buen modelo de actualización se diseña alrededor del dominio (un partido tiene hora conocida) y no a base de polling.

## Requisitos

- Android 8.0+ (minSdk 26)
- Android Studio con AGP 9.x / Kotlin 2.2

## Licencia

Uso educativo y personal. Los datos pertenecen a ESPN; los escudos y nombres de los clubes pertenecen a sus respectivos dueños.

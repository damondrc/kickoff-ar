# ⚽ Kickoff AR

[![Build](https://github.com/damondrc/kickoff-ar/actions/workflows/build.yml/badge.svg)](https://github.com/damondrc/kickoff-ar/actions/workflows/build.yml)

**[⬇ Descargar la última versión (APK)](https://github.com/damondrc/kickoff-ar/releases/latest)**

Widget minimalista para Android que muestra el próximo partido de tu equipo del fútbol argentino: cuándo juega, si está jugando, y el resultado final — directo en tu pantalla de inicio, sin abrir ninguna app.

```
┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐
│   🛡  RIV vs CAT  🛡    │   │   🛡  RIV 1-0 CAT  🛡   │   │   🛡  RIV 2-1 CAT  🛡   │
│    dom 26 jul · 16:00   │ → │       ● Jugando         │ → │         Final           │
└─────────────────────────┘   └─────────────────────────┘   └─────────────────────────┘
```

## Capturas

<p align="center">
  <img width="200" alt="Widget en pantalla de inicio" src="https://github.com/user-attachments/assets/10e33f0f-7b86-416d-adee-864cb6b30e27" />
  <img width="200" alt="Widget expandido" src="https://github.com/user-attachments/assets/9375ccec-ffb7-4b1f-b93f-a9aa897e2243" />
  <img width="200" alt="Selección de equipo" src="https://github.com/user-attachments/assets/b9a6d413-c607-4943-a89b-4d362e16baeb" />
</p>
<p align="center">
  <img width="200" alt="Pestaña Partidos" src="https://github.com/user-attachments/assets/769a8b22-65da-4a91-98ee-7d93af0e6880" />
  <img width="200" alt="Calendario" src="https://github.com/user-attachments/assets/d931a6eb-934e-486a-af7a-14c054abb91b" />
  <img width="200" alt="Ajustes" src="https://github.com/user-attachments/assets/baf6db22-0dbb-456c-a5d5-40da7a1e097e" />
</p>

## La historia

Este proyecto nació de la idea de un amigo argentino: quería una app *supersencilla* de configurar que simplemente le dijera cuándo jugaba su equipo favorito. Nada de apps pesadas, minuto a minuto, ni registros. El diseño final es una mezcla de esa idea original y de las adaptaciones que fui haciendo en el camino: este fue mi primer proyecto en territorio Android, desarrollado iterativamente entre mayo y julio de 2026, y varias decisiones (la fuente de datos, el modelo de actualización, la arquitectura del widget) fueron evolucionando a medida que el terreno me lo iba enseñando.

## Características

- **Widget por instancia**: varios widgets en pantalla, cada uno siguiendo un equipo distinto de los 30 de la Primera División. La configuración se abre sola al agregarlo.
- **Redimensionable**: en tamaño compacto muestra el próximo partido; al agrandarlo, también los dos siguientes.
- **Todas las competiciones**: Liga Profesional, Copa Argentina, Libertadores y Sudamericana — responde "¿cuándo juega mi equipo?" sin importar el torneo, indicándolo cuando no es de liga.
- **Actualización puntual**: alarmas del sistema despiertan la app justo al comenzar cada partido, así el widget cambia de estado y las notificaciones llegan sin necesidad de abrir nada.
- **Notificaciones opcionales**: ~30 minutos antes del partido, al comenzar, y al finalizar con el resultado.
- **Historial de la temporada**: todos los resultados ya jugados, filtrables por competición y agrupados por día, con los partidos de tus equipos primero.
- **Tres pestañas**: *Partidos* (tarjetas desplegables por equipo seguido, filtros por competición y fixture en vista de lista o **calendario mensual** que también abarca los días pasados con sus marcadores), *Historial* y *Ajustes*.
- **Sin registro, sin cuentas, gratis.**

## El diseño central: cero polling

El widget **no se actualiza "a cada rato"**. Sabe a qué hora es el partido, así que una alarma del sistema despierta la app en el kickoff; durante el juego re-chequea cada 30 minutos (y cada 10 en el tramo final, para marcar el resultado cerca del pitazo real); muestra el final, y la siguiente alarma queda puesta para el próximo partido — más un sync diario de respaldo que capta cambios de fixture. Entre partido y partido pueden pasar días sin un solo request. Consumo de batería y datos: prácticamente nulo.

El detalle que lo hace funcionar: los trabajos *diferibles* de WorkManager no sirven para esto. Una app de widget puede pasar días sin abrirse, Android la degrada a un *standby bucket* restringido y sus trabajos se posponen indefinidamente — el widget quedaba congelado hasta abrir la app. `AlarmManager` existe justamente para eventos anclados a una hora concreta: la alarma despierta la app y WorkManager ejecuta el sync con su restricción de red. Cada herramienta en su rol.

## Arquitectura

La app consume un **backend propio**: un Cloudflare Worker (`server/worker.js`) que actúa como proxy con caché de borde sobre la API pública de ESPN. Consulta las cuatro competiciones en paralelo, sirve la temporada completa (para el historial), transforma la respuesta a un JSON compacto (~50× más liviano) con estados normalizados, y la cachea con TTL dinámico: 5 minutos si hay partido en vivo, 15 si no. Todos los usuarios juntos generan sobre la fuente el tráfico de uno solo, y un cambio de formato upstream se corrige en el servidor sin publicar actualización de la app.

```
ESPN API ──► Cloudflare Worker ──► App (Retrofit) ──► Room
             (caché 5/15 min,                         (única fuente
              temporada completa)                      de verdad)
                                                        │
                                    ┌───────────────────┼───────────────┐
                                    ▼                   ▼               ▼
  AlarmManager ──► MatchSyncWorker            WidgetUpdater        MainActivity
  (kickoff)        (sync + notificaciones)    (escribe estado      (Partidos ·
                                               Glance)              Historial ·
                                                  │                 Ajustes)
                                                  ▼
                                            NextMatchWidget
                                            (solo lee currentState)
```

Decisión de diseño clave: el widget Glance es deliberadamente "tonto" — solo renderiza su estado (`currentState`). `WidgetUpdater` es el único escritor de ese estado. Esto evita el problema clásico de Glance donde los datos cargados en `provideGlance` quedan congelados tras la primera composición.

## Stack técnico

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose + Material 3 (NavigationBar, FilterChips) |
| Widget | Glance App Widgets (estado por instancia, SizeMode.Responsive) |
| Datos remotos | Retrofit + OkHttp + Gson |
| Cache local | Room (offline-first: el widget siempre lee de la DB) |
| Preferencias | DataStore (equipo por widget + ajustes, con Flows reactivos) |
| Tareas en background | AlarmManager (kickoff) + WorkManager (ejecución y respaldo diario) |
| Inyección de dependencias | Hilt (incluye `@HiltWorker`) |
| Imágenes | Coil (escudos cacheados en disco para el widget) |
| Backend | Cloudflare Workers (proxy con caché de borde, JS) |

## Lo que aprendí en el camino

Cada capa dejó una lección concreta. Las APIs deportivas comerciales restringen sus planes gratuitos hasta volverlos inservibles para datos actuales; la solución fue una fuente pública estable, leyendo con cuidado sus particularidades (fechas UTC sin segundos, marcadores que existen antes de empezar el partido). En Glance aprendí — depurando en dispositivo físico — que los datos cargados en `provideGlance` quedan congelados tras la primera composición, y que la optimización de batería de Samsung puede dejar un widget en blanco si la app no se auto-repara. Con WorkManager, dos lecciones: que re-encolar tu propio nombre único con `REPLACE` te cancela a ti mismo (de ahí `APPEND_OR_REPLACE`), y — la más cara — que sus trabajos diferidos **no sirven para eventos con hora exacta**: probando la app en partidos reales descubrí que el widget no despertaba al kickoff porque Android posterga el trabajo de las apps que llevan días sin abrirse; la solución fue mover el disparo a `AlarmManager`. Con Kotlin, que un bloque `init` que dispara corrutinas antes de que se declaren las propiedades que toca compila perfecto y crashea en runtime. Con R8, que la minificación rompe silenciosamente el parseo de Gson sin reglas de keep. Y una de producto: llegué a derivar el número de fecha del torneo agrupando partidos por cercanía, pero como la fuente no lo publica y la heurística fallaba, preferí agrupar por el día real de juego — mejor un dato exacto que uno inventado.

## Requisitos

- Android 8.0+ (minSdk 26)
- Para compilar: Android Studio con AGP 9.x / Kotlin 2.2
- El backend se despliega pegando `server/worker.js` en un Worker gratuito de Cloudflare (la URL se configura en `Constants.kt`)

## Licencia

Uso educativo y personal; no se autoriza la redistribución comercial. Proyecto sin afiliación con AFA, los clubes ni los proveedores de datos; los escudos y nombres pertenecen a sus respectivos dueños.

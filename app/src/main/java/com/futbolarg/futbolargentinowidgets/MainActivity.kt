package com.futbolarg.futbolargentinowidgets

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.domain.model.Team
import com.futbolarg.futbolargentinowidgets.ui.theme.FútbolArgentinoWidgetsTheme
import com.futbolarg.futbolargentinowidgets.util.Constants
import com.futbolarg.futbolargentinowidgets.util.DateFormatting
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============================================================
// MainActivity.kt — Kickoff AR (v1.1)
// ============================================================
// Pestaña PARTIDOS:
//   - "Mis equipos": una tarjeta desplegable por equipo (escudo
//     + nombre; tocar = expandir sus próximos partidos; mantener
//     presionado = ocultar de la sección)
//   - "Fixture": chips por competición + selector de vista
//     (lista agrupada por día, o calendario mensual que resalta
//     los días con partidos)
//
// Pestaña AJUSTES: notificaciones + personalización del widget.
//
// TEMA: el acento de la UI se tiñe con el color oficial del
// último equipo elegido (celeste argentino si aún no hay).
// ============================================================

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FútbolArgentinoWidgetsTheme {
                KickoffTheme {
                    KickoffApp(viewModel)
                }
            }
        }
    }
}

// ============================================================
// Tema de Kickoff AR: paleta ÚNICA y uniforme.
// ============================================================
// v1.1: se eliminó el tinte por color de equipo — los colores
// oficiales de los clubes (amarillos, rojos brillantes) rompían
// la legibilidad y la coherencia visual con demasiada
// frecuencia. El acento fijo es el celeste de la marca.
// ============================================================

private val CELESTE = Color(0xFF74ACDF)

@Composable
private fun KickoffTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val scheme = remember(base) {
        base.copy(
            primary = CELESTE,
            secondaryContainer = CELESTE.copy(alpha = 0.22f).compositeOver(base.surface),
            onSecondaryContainer = base.onSurface
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

// ============================================================
// Estructura: Scaffold + barra de navegación inferior
// ============================================================

@Composable
private fun KickoffApp(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("Partidos") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Ajustes") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> PartidosScreen(viewModel, Modifier.padding(innerPadding))
            else -> AjustesScreen(viewModel, Modifier.padding(innerPadding))
        }
    }
}

// ============================================================
// Pestaña PARTIDOS
// ============================================================

@Composable
private fun PartidosScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val visibleTeams by viewModel.visibleTeams.collectAsStateWithLifecycle()
    val hiddenCount by viewModel.hiddenTeamCount.collectAsStateWithLifecycle()
    val misEquipos by viewModel.misEquipos.collectAsStateWithLifecycle()
    val fixture by viewModel.fixture.collectAsStateWithLifecycle()
    val leagueFilter by viewModel.leagueFilter.collectAsStateWithLifecycle()
    val fixtureView by viewModel.fixtureView.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Header() }

        // -------- Mis equipos (tarjetas desplegables) --------
        item {
            Text(
                text = "Mis equipos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        if (visibleTeams.isEmpty()) {
            item {
                Text(
                    text = if (hiddenCount > 0) {
                        "Todos tus equipos están ocultos."
                    } else {
                        "Agrega un widget a tu pantalla de inicio para seguir a un equipo."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(visibleTeams, key = { "equipo-${it.id}" }) { team ->
                TeamCard(
                    team = team,
                    matches = misEquipos.filter {
                        it.homeTeamId == team.id || it.awayTeamId == team.id
                    },
                    onHide = { viewModel.hideTeam(team.id) }
                )
            }
        }
        // (La restauración de equipos ocultos vive en Ajustes →
        // Personalización, para no dejar un botón permanente acá)

        // -------- Fixture: filtros + selector de vista --------
        item {
            Text(
                text = "Fixture",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            LeagueFilterChips(
                selected = leagueFilter,
                onSelect = viewModel::setLeagueFilter
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = fixtureView == FixtureView.LIST,
                    onClick = { viewModel.setFixtureView(FixtureView.LIST) },
                    label = { Text("Lista") }
                )
                FilterChip(
                    selected = fixtureView == FixtureView.CALENDAR,
                    onClick = { viewModel.setFixtureView(FixtureView.CALENDAR) },
                    label = { Text("Calendario") }
                )
            }
        }

        // -------- Contenido del fixture --------
        when {
            fixture.isEmpty() -> item {
                Text(
                    text = "No hay partidos para este filtro todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            fixtureView == FixtureView.LIST -> {
                val grouped = fixture.groupBy {
                    DateFormatting.formatDayHeader(it.kickoffMillis)
                }
                grouped.forEach { (day, matches) ->
                    item(key = "dia-$day") {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(matches, key = { it.id }) { match ->
                        FixtureMatchRow(match)
                    }
                }
            }

            else -> item(key = "calendario") {
                CalendarSection(fixture)
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Logo Kickoff AR",
            modifier = Modifier.size(56.dp)
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = "Kickoff AR",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fútbol argentino en tu pantalla de inicio",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ============================================================
// Tarjeta desplegable de "Mis equipos"
// ============================================================
// Tocar: expande/colapsa los próximos partidos del equipo.
// Mantener presionado: diálogo para ocultarlo de la sección.
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeamCard(team: Team, matches: List<Match>, onHide: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showHideDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = { showHideDialog = true }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = team.logoUrl,
                    contentDescription = team.name,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = team.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                if (matches.isEmpty()) {
                    Text(
                        text = "Sin próximos partidos por ahora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                } else {
                    matches.take(4).forEach { match ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "${match.homeTeamAbbr} vs ${match.awayTeamAbbr}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = match.leagueName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (match.status.isLive) {
                                    "● Jugando"
                                } else {
                                    DateFormatting.formatKickoff(match.kickoffMillis)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (match.status.isLive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    Box(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showHideDialog) {
        AlertDialog(
            onDismissRequest = { showHideDialog = false },
            title = { Text("¿Quitar de Mis equipos?") },
            text = {
                Text(
                    "${team.name} dejará de verse en esta sección. " +
                        "Su widget sigue funcionando normal, y puedes " +
                        "restaurarlo cuando quieras."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showHideDialog = false
                    onHide()
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { showHideDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ============================================================
// Fixture: vista LISTA
// ============================================================

@Composable
private fun LeagueFilterChips(selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Todas") }
            )
        }
        items(Constants.LEAGUES.values.toList()) { league ->
            FilterChip(
                selected = selected == league,
                onClick = { onSelect(league) },
                label = { Text(league) }
            )
        }
    }
}

// Fila del fixture. v1.1: los DOS escudos van pegados al texto
// del partido (grupo izquierdo compacto) y la hora a la derecha.
// Antes el escudo visitante quedaba flotando junto a la hora,
// lejos del "CABJ vs RIV", y rompía la coherencia visual.
@Composable
private fun FixtureMatchRow(match: Match) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grupo izquierdo: [escudo] CABJ vs RIV [escudo]
            AsyncImage(
                model = match.homeTeamLogo,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "${match.homeTeamAbbr} vs ${match.awayTeamAbbr}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            AsyncImage(
                model = match.awayTeamLogo,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )

            // El weight empuja lo siguiente al borde derecho
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (match.status.isLive) {
                        "● Jugando"
                    } else {
                        DateFormatting.formatTime(match.kickoffMillis)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (match.status.isLive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = match.leagueName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================
// Fixture: vista CALENDARIO
// ============================================================
// Grilla mensual construida a mano (7 columnas, lunes a domingo).
// Los días con partidos llevan un punto; tocar un día muestra
// sus partidos debajo.
// ============================================================

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("es", "AR"))

@Composable
private fun CalendarSection(matches: List<Match>) {
    val zone = ZoneId.systemDefault()
    val matchesByDate = remember(matches) {
        matches.groupBy {
            Instant.ofEpochMilli(it.kickoffMillis).atZone(zone).toLocalDate()
        }
    }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    Column {
        // Navegación de mes
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
            }
            Text(
                text = month.format(MONTH_FORMAT)
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
            }
        }

        // Encabezado de días de la semana
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("lu", "ma", "mi", "ju", "vi", "sá", "do").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Grilla del mes
        val firstDayOffset = month.atDay(1).dayOfWeek.value - 1 // lunes = 0
        val daysInMonth = month.lengthOfMonth()
        val totalCells = firstDayOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNumber = row * 7 + col - firstDayOffset + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            val hasMatches = matchesByDate.containsKey(date)
                            val isSelected = date == selected

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable {
                                        selected = if (isSelected) null else date
                                    }
                            ) {
                                Text(
                                    text = "$dayNumber",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (hasMatches) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Partidos del día seleccionado
        selected?.let { date ->
            val dayMatches = matchesByDate[date]
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dayMatches.isNullOrEmpty()) {
                    Text(
                        text = "Sin partidos este día.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    dayMatches.forEach { match -> FixtureMatchRow(match) }
                }
            }
        }
    }
}

// ============================================================
// Pestaña AJUSTES
// ============================================================

@Composable
private fun AjustesScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val notifyBefore by viewModel.notifyBeforeStart.collectAsStateWithLifecycle()
    val notifyKickoff by viewModel.notifyKickoff.collectAsStateWithLifecycle()
    val notifyFinished by viewModel.notifyFinished.collectAsStateWithLifecycle()
    val hiddenCount by viewModel.hiddenTeamCount.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var pendingToggle by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingToggle?.invoke()
        pendingToggle = null
    }

    fun toggleWithPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingToggle = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // -------- Alarmas exactas (solo si falta el permiso) --------
        // Sin este permiso las actualizaciones del kickoff pueden
        // derivar unos minutos. La app funciona igual, pero avisamos.
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
        val needsExactAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        if (needsExactAlarm) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Actualizaciones puntuales",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Activa las alarmas exactas para que el widget " +
                                "se actualice justo al comenzar cada partido. " +
                                "Vuelve a esta pantalla después de activarlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                )
                            )
                        }) { Text("Activar alarmas exactas") }
                    }
                }
            }
        }

        // -------- Personalización --------
        item {
            Text(
                text = "Personalización",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = "Equipos ocultos",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (hiddenCount > 0) {
                                "$hiddenCount equipo(s) fuera de \"Mis equipos\""
                            } else {
                                "No has ocultado ningún equipo"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { viewModel.restoreHiddenTeams() },
                        enabled = hiddenCount > 0
                    ) { Text("Restaurar") }
                }
            }
        }

        item {
            Text(
                text = "Notificaciones",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    SettingRow(
                        title = "Antes del partido",
                        subtitle = "Aviso ~30 minutos antes del inicio",
                        checked = notifyBefore,
                        onToggle = { enabled ->
                            if (enabled) {
                                toggleWithPermission { viewModel.setNotifyBeforeStart(true) }
                            } else {
                                viewModel.setNotifyBeforeStart(false)
                            }
                        }
                    )
                    SettingRow(
                        title = "Al comenzar",
                        subtitle = "Aviso cuando el partido arranca",
                        checked = notifyKickoff,
                        onToggle = { enabled ->
                            if (enabled) {
                                toggleWithPermission { viewModel.setNotifyKickoff(true) }
                            } else {
                                viewModel.setNotifyKickoff(false)
                            }
                        }
                    )
                    SettingRow(
                        title = "Al finalizar",
                        subtitle = "Aviso con el resultado final",
                        checked = notifyFinished,
                        onToggle = { enabled ->
                            if (enabled) {
                                toggleWithPermission { viewModel.setNotifyFinished(true) }
                            } else {
                                viewModel.setNotifyFinished(false)
                            }
                        }
                    )
                }
            }
        }

        item {
            Text(
                text = "Widget",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Text(
                text = "Mantén presionada la pantalla de inicio → Widgets → " +
                    "Kickoff AR, y elige tu equipo. Puedes agregar varios, " +
                    "uno por equipo, y agrandarlos para ver más partidos. " +
                    "Para que las actualizaciones funcionen sin trabas, pon " +
                    "la app en \"Sin restricciones\" en Ajustes → Batería.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

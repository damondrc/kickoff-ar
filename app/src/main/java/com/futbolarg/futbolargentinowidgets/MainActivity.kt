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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.futbolarg.futbolargentinowidgets.domain.model.Match
import com.futbolarg.futbolargentinowidgets.ui.theme.FútbolArgentinoWidgetsTheme
import com.futbolarg.futbolargentinowidgets.util.Constants
import com.futbolarg.futbolargentinowidgets.util.DateFormatting
import dagger.hilt.android.AndroidEntryPoint

// ============================================================
// MainActivity.kt — Kickoff AR (v2, con navegación)
// ============================================================
// Dos pestañas (NavigationBar de Material 3):
//
//   PARTIDOS: sección "Mis equipos" (los que siguen tus
//   widgets), chips para filtrar por competición, y el fixture
//   completo agrupado por día.
//
//   AJUSTES: notificaciones + cómo agregar el widget.
//
// La UI es "tonta" a propósito: no filtra ni agrupa nada — solo
// observa los StateFlows que MainViewModel ya combinó. Misma
// filosofía que el widget con su estado Glance.
// ============================================================

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FútbolArgentinoWidgetsTheme {
                KickoffApp(viewModel)
            }
        }
    }
}

// ============================================================
// Estructura: Scaffold + barra de navegación inferior
// ============================================================

@Composable
private fun KickoffApp(viewModel: MainViewModel) {
    // 0 = Partidos, 1 = Ajustes. rememberSaveable no hace falta:
    // si el sistema recrea la Activity, volver a Partidos es OK.
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
    val misEquipos by viewModel.misEquipos.collectAsStateWithLifecycle()
    val fixture by viewModel.fixture.collectAsStateWithLifecycle()
    val leagueFilter by viewModel.leagueFilter.collectAsStateWithLifecycle()
    val followedIds by viewModel.followedTeamIds.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // -------- Encabezado --------
        item { Header() }

        // -------- Mis equipos --------
        item {
            Text(
                text = "Mis equipos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        if (misEquipos.isEmpty()) {
            item {
                Text(
                    text = if (followedIds.isEmpty()) {
                        "Agrega un widget a tu pantalla de inicio para seguir a un equipo."
                    } else {
                        "Sin próximos partidos de tus equipos por ahora."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(misEquipos, key = { "mio-${it.id}" }) { match ->
                MatchCard(match, highlight = true)
            }
        }

        // -------- Filtro por competición --------
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
        }

        // -------- Fixture agrupado por día --------
        if (fixture.isEmpty()) {
            item {
                Text(
                    text = "No hay partidos para este filtro todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // groupBy conserva el orden de la lista (ya viene
            // ordenada por fecha desde Room)
            val grouped = fixture.groupBy { DateFormatting.formatDayHeader(it.kickoffMillis) }
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
                    MatchCard(match, highlight = false)
                }
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

@Composable
private fun LeagueFilterChips(selected: String?, onSelect: (String?) -> Unit) {
    // "Todas" + una chip por competición (desde Constants, así
    // agregar una liga nueva no requiere tocar la UI)
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

@Composable
private fun MatchCard(match: Match, highlight: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${match.homeTeamAbbr} vs ${match.awayTeamAbbr}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = match.leagueName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = when {
                    match.status.isLive ->
                        "● ${match.homeScore ?: 0}-${match.awayScore ?: 0} Jugando"
                    highlight ->
                        DateFormatting.formatKickoff(match.kickoffMillis)
                    else ->
                        DateFormatting.formatTime(match.kickoffMillis)
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
}

// ============================================================
// Pestaña AJUSTES
// ============================================================

@Composable
private fun AjustesScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val notifyBefore by viewModel.notifyBeforeStart.collectAsStateWithLifecycle()
    val notifyKickoff by viewModel.notifyKickoff.collectAsStateWithLifecycle()
    val notifyFinished by viewModel.notifyFinished.collectAsStateWithLifecycle()

    // Permiso POST_NOTIFICATIONS (Android 13+): se pide al
    // activar cualquier switch; la acción pendiente solo se
    // ejecuta si el usuario concede
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
        item {
            Text(
                text = "Notificaciones",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
                    "uno por equipo. Para que las actualizaciones funcionen " +
                    "sin trabas, pon la app en \"Sin restricciones\" en " +
                    "Ajustes → Batería.",
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
        Column(modifier = Modifier.padding(end = 16.dp)) {
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

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.futbolarg.futbolargentinowidgets.util.DateFormatting
import dagger.hilt.android.AndroidEntryPoint

// ============================================================
// MainActivity.kt
// ============================================================
// Pantalla principal de Kickoff AR. El protagonista es el
// WIDGET; esta pantalla:
//
// 1. Explica cómo agregarlo
// 2. Permite configurar las notificaciones
// 3. Muestra los próximos partidos sincronizados
// ============================================================

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FútbolArgentinoWidgetsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(viewModel: MainViewModel) {
    val matches by viewModel.upcomingMatches.collectAsStateWithLifecycle()
    val notifyBefore by viewModel.notifyBeforeStart.collectAsStateWithLifecycle()
    val notifyKickoff by viewModel.notifyKickoff.collectAsStateWithLifecycle()
    val notifyFinished by viewModel.notifyFinished.collectAsStateWithLifecycle()

    // ========================================================
    // Permiso POST_NOTIFICATIONS (Android 13+).
    // Al activar cualquier switch lo pedimos si falta; la acción
    // pendiente se ejecuta solo si el usuario lo concede.
    // ========================================================
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // -------- Encabezado con logo --------
        item {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo Kickoff AR",
                    modifier = Modifier.size(64.dp)
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "Kickoff AR",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tu equipo de la Liga Profesional, en tu pantalla de inicio",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = "Mantén presionada la pantalla de inicio → Widgets → " +
                    "Kickoff AR, y elige tu equipo. Puedes agregar varios, " +
                    "uno por equipo.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // -------- Ajustes de notificaciones --------
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

        // -------- Próximos partidos --------
        if (matches.isEmpty()) {
            item {
                Text(
                    text = "Todavía no hay partidos sincronizados. " +
                        "Al agregar un widget se descarga el fixture.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            item {
                Text(
                    text = "Próximos partidos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            items(matches, key = { it.id }) { match ->
                MatchCard(match)
            }
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

@Composable
private fun MatchCard(match: Match) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${match.homeTeamAbbr} vs ${match.awayTeamAbbr}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (match.status.isLive) {
                    "● Jugando"
                } else {
                    DateFormatting.formatKickoff(match.kickoffMillis)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

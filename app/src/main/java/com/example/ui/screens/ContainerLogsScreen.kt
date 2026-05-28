package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PortainerApiClient
import com.example.data.PortainerContainer
import com.example.ui.viewmodel.ConnectionViewModel
import com.example.ui.viewmodel.NativeSessionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerLogsScreen(
    state: NativeSessionState.Success,
    container: PortainerContainer,
    viewModel: ConnectionViewModel,
    onBack: () -> Unit
) {
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    val endpointId = state.selectedEndpoint?.Id ?: 1
    val containerName = container.Names?.firstOrNull()?.removePrefix("/") ?: container.Id.take(12)

    fun fetchLogs() {
        isLoading = true
        error = null
        scope.launch {
            try {
                val connection = viewModel.selectedConnection.value ?: return@launch
                val api = PortainerApiClient.create(connection.url)
                val responseBody = api.getContainerLogs(
                    token = state.token,
                    endpointId = endpointId,
                    containerId = container.Id,
                    tail = 100
                )
                // Docker logs from Portainer API are prefixed with 8 bytes of stream metadata
                // We'll just strip out non-printable chars or do basic string parse
                val rawString = responseBody.string()
                val parsedLogs = rawString.lines()
                    .map { it.replace(Regex("^.{8}"), "") } // Quick strip docker header
                    .filter { it.isNotBlank() }
                
                logs = parsedLogs
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Failed to fetch logs"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(container.Id) {
        fetchLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Logs: $containerName",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = container.Id.take(12),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { fetchLogs() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Logs")
                    }
                }
            )
        }
    ) { paddingVals ->
        Box(modifier = Modifier.padding(paddingVals).fillMaxSize()) {
            if (isLoading && logs.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (error != null && logs.isEmpty()) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .padding(8.dp)
                ) {
                    items(logs) { logLine ->
                        Text(
                            text = logLine,
                            color = Color(0xFFA9B7C6),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

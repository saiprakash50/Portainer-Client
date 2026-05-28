package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.viewmodel.ConnectionViewModel
import com.example.ui.viewmodel.NativeSessionState
import kotlinx.coroutines.launch

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackEditScreen(
    state: NativeSessionState.Success,
    stack: PortainerStack,
    viewModel: ConnectionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var composeFile by remember { mutableStateOf("") }
    var envVars by remember { mutableStateOf(stack.Env ?: emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val endpointId = state.selectedEndpoint?.Id ?: 1

    LaunchedEffect(stack.Id) {
        scope.launch {
            try {
                if (stack.Id < 0) {
                    val containers = state.containers.filter { it.Labels?.get("com.docker.compose.project") == stack.Name }
                    val sb = StringBuilder("version: '3.8'\nservices:\n")
                    containers.forEach { c ->
                        val serviceName = c.Labels?.get("com.docker.compose.service") ?: c.Names?.firstOrNull()?.removePrefix("/") ?: "unknown"
                        sb.append("  $serviceName:\n")
                        sb.append("    image: ${c.Image}\n")
                        sb.append("    container_name: ${c.Names?.firstOrNull()?.removePrefix("/")}\n")
                        // Add more basic properties if needed, this is approximate
                        sb.append("    # Note: This is an automatically generated approximation based on running container inspection.\n")
                        sb.append("    # Volumes, exact ports, and networks may not be fully represented.\n")
                    }
                    composeFile = sb.toString()
                } else {
                    val connection = viewModel.selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val fileData = api.getStackFile(state.token, stack.Id)
                    composeFile = fileData.StackFileContent ?: ""
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load stack file", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    val saveAction = {
        if (composeFile.isNotBlank()) {
            isSaving = true
            scope.launch {
                try {
                    val connection = viewModel.selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val request = UpdateStackRequest(
                        env = envVars,
                        prunable = false,
                        pullImage = true,
                        stackFileContent = composeFile
                    )
                    api.updateStack(state.token, stack.Id, endpointId, request)
                    Toast.makeText(context, "Stack updated and relaunched!", Toast.LENGTH_SHORT).show()
                    viewModel.connectNatively(connection)
                    onBack()
                } catch (e: Exception) {
                    Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isSaving = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Stack: ${stack.Name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { saveAction() },
                        enabled = stack.Id > 0 && !isLoading && !isSaving && composeFile.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save & Relaunch Stack")
                        }
                    }
                }
            )
        }
    ) { paddingVals ->
        Box(modifier = Modifier.padding(paddingVals).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Compose Editor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = composeFile,
                        onValueChange = { composeFile = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        readOnly = stack.Id < 0,
                        placeholder = { Text("version: '3'\nservices:\n  ...") }
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Environment Variables", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.weight(0.5f)) {
                        itemsIndexed(envVars) { index, env ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = env.name,
                                    onValueChange = { newVal -> 
                                        val newEnv = envVars.toMutableList()
                                        newEnv[index] = env.copy(name = newVal)
                                        envVars = newEnv
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    readOnly = stack.Id < 0,
                                    placeholder = { Text("Name") }
                                )
                                OutlinedTextField(
                                    value = env.value,
                                    onValueChange = { newVal -> 
                                        val newEnv = envVars.toMutableList()
                                        newEnv[index] = env.copy(value = newVal)
                                        envVars = newEnv
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    readOnly = stack.Id < 0,
                                    placeholder = { Text("Value") }
                                )
                            }
                        }
                        if (stack.Id > 0) {
                            item {
                                Button(onClick = { envVars = envVars + PortainerEnvVar("", "") }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Add Environment Variable")
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    if (stack.Id > 0) {
                        Button(
                            onClick = { saveAction() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !isSaving && composeFile.isNotBlank()
                        ) {
                            Text(if (isSaving) "Deploying..." else "Update & Relaunch Stack")
                        }
                    }
                }
            }
        }
    }
}

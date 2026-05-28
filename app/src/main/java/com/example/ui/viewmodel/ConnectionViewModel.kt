package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

sealed class NativeSessionState {
    object Idle : NativeSessionState()
    object Loading : NativeSessionState()
    data class Success(
        val token: String,
        val endpoints: List<PortainerEndpoint>,
        val selectedEndpoint: PortainerEndpoint?,
        val stacks: List<PortainerStack>,
        val containers: List<PortainerContainer>,
        val volumes: List<PortainerVolume>,
        val templates: List<PortainerTemplate>,
        val customTemplates: List<PortainerCustomTemplate>,
        val containerStats: Map<String, ContainerStatsResponse> = emptyMap()
    ) : NativeSessionState()
    data class Error(val message: String) : NativeSessionState()
}

enum class ContainerAction {
    START, STOP, RESTART, PAUSE, UNPAUSE, KILL, REMOVE
}

enum class StackAction {
    START, STOP, REMOVE
}

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ConnectionRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ConnectionRepository(database.connectionDao())
    }

    val connections: StateFlow<List<PortainerConnection>> = repository.allConnections
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedConnection = MutableStateFlow<PortainerConnection?>(null)
    val selectedConnection: StateFlow<PortainerConnection?> = _selectedConnection.asStateFlow()

    private val _processingContainers = MutableStateFlow<Set<String>>(emptySet())
    val processingContainers: StateFlow<Set<String>> = _processingContainers.asStateFlow()

    private val _processingStacks = MutableStateFlow<Set<Int>>(emptySet())
    val processingStacks: StateFlow<Set<Int>> = _processingStacks.asStateFlow()
    
    private val _processingVolumes = MutableStateFlow<Set<String>>(emptySet())
    val processingVolumes: StateFlow<Set<String>> = _processingVolumes.asStateFlow()

    private val _nativeSessionState = MutableStateFlow<NativeSessionState>(NativeSessionState.Idle)
    val nativeSessionState: StateFlow<NativeSessionState> = _nativeSessionState.asStateFlow()

    fun selectConnection(connection: PortainerConnection?) {
        _selectedConnection.value = connection
        if (connection == null) {
            _nativeSessionState.value = NativeSessionState.Idle
        } else if (connection.username.isNotBlank() && connection.password.isNotBlank()) {
            connectNatively(connection)
        } else {
            _nativeSessionState.value = NativeSessionState.Idle
        }
    }

    fun connectNatively(connection: PortainerConnection) {
        viewModelScope.launch {
            _nativeSessionState.value = NativeSessionState.Loading
            try {
                val api = PortainerApiClient.create(connection.url)
                val authResponse = api.login(LoginRequest(connection.username, connection.password))
                val token = "Bearer ${authResponse.jwt}"
                
                // Fetch environments (endpoints)
                val endpoints = api.getEndpoints(token)
                val defaultEndpoint = endpoints.firstOrNull()
                
                var stacks = emptyList<PortainerStack>()
                var containers = emptyList<PortainerContainer>()
                var volumes = emptyList<PortainerVolume>()
                var templates = emptyList<PortainerTemplate>()
                var customTemplates = emptyList<PortainerCustomTemplate>()
                
                try {
                    templates = api.getTemplates(token).templates ?: emptyList()
                } catch (e: Exception) {}
                
                try {
                    customTemplates = api.getCustomTemplates(token)
                } catch (e: Exception) {}
                
                if (defaultEndpoint != null) {
                    var allStacks: List<PortainerStack>? = null
                    try { allStacks = api.getStacks(token) } catch (e: Exception) {}
                    try { containers = api.getContainers(token, defaultEndpoint.Id) } catch (e: Exception) {}
                    try { volumes = api.getVolumes(token, defaultEndpoint.Id).Volumes ?: emptyList() } catch (e: Exception) {}
                    
                    stacks = buildFilteredStacks(allStacks, containers, defaultEndpoint.Id)
                }
                
                _nativeSessionState.value = NativeSessionState.Success(
                    token = token,
                    endpoints = endpoints,
                    selectedEndpoint = defaultEndpoint,
                    stacks = stacks,
                    containers = containers,
                    volumes = volumes,
                    templates = templates,
                    customTemplates = customTemplates
                )
            } catch (e: Exception) {
                _nativeSessionState.value = NativeSessionState.Error(
                    e.localizedMessage ?: "Failed to authenticate or contact server. Verify address and credentials."
                )
            }
        }
    }

    fun selectEndpoint(endpoint: PortainerEndpoint) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                _nativeSessionState.value = NativeSessionState.Loading
                try {
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val token = currentState.token
                    
                    var allStacks: List<PortainerStack>? = null
                    var containers = emptyList<PortainerContainer>()
                    var volumes = emptyList<PortainerVolume>()
                    
                    try { allStacks = api.getStacks(token) } catch (e: Exception) {}
                    try { containers = api.getContainers(token, endpoint.Id) } catch (e: Exception) {}
                    try { volumes = api.getVolumes(token, endpoint.Id).Volumes ?: emptyList() } catch (e: Exception) {}
                    
                    val stacks = buildFilteredStacks(allStacks, containers, endpoint.Id)
                    
                    _nativeSessionState.value = currentState.copy(
                        selectedEndpoint = endpoint,
                        stacks = stacks,
                        containers = containers,
                        volumes = volumes
                    )
                } catch (e: Exception) {
                    _nativeSessionState.value = NativeSessionState.Error(
                        e.localizedMessage ?: "Failed to switch environment."
                    )
                }
            }
        }
    }

    fun refreshDashboard() {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                performRefresh(currentState)
            }
        }
    }

    suspend fun performRefresh(currentState: NativeSessionState.Success? = _nativeSessionState.value as? NativeSessionState.Success) {
        if (currentState == null) return
        try {
            val connection = _selectedConnection.value ?: return
            val api = PortainerApiClient.create(connection.url)
            val token = currentState.token
            val endpoint = currentState.selectedEndpoint ?: return
            
            var allStacks: List<PortainerStack>? = null
            var containers = emptyList<PortainerContainer>()
            var volumes = emptyList<PortainerVolume>()
            
            try { allStacks = api.getStacks(token) } catch (e: Exception) {}
            try { containers = api.getContainers(token, endpoint.Id) } catch (e: Exception) {}
            try { volumes = api.getVolumes(token, endpoint.Id).Volumes ?: emptyList() } catch (e: Exception) {}
            
            val stacks = buildFilteredStacks(allStacks, containers, endpoint.Id)
            
            val statsMap = mutableMapOf<String, ContainerStatsResponse>()
            val runningContainers = containers.filter { it.State?.lowercase() == "running" }
            coroutineScope {
                runningContainers.map { container ->
                    async(Dispatchers.IO) {
                        try {
                            val stats = api.getContainerStats(token, endpoint.Id, container.Id)
                            Pair(container.Id, stats)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.forEach { deferred ->
                    deferred.await()?.let { statsMap[it.first] = it.second }
                }
            }
            
            // Simple, direct fallback in case snapshots are empty or changed
            val updatedEndpoints = try {
                api.getEndpoints(token)
            } catch (e: Exception) {
                currentState.endpoints
            }
            val updatedSelectedEndpoint = updatedEndpoints.find { it.Id == endpoint.Id } ?: endpoint
            
            _nativeSessionState.value = currentState.copy(
                endpoints = updatedEndpoints,
                selectedEndpoint = updatedSelectedEndpoint,
                stacks = stacks,
                containers = containers,
                volumes = volumes,
                containerStats = statsMap
            )
        } catch (e: Exception) {
            // Fail silently during background auto-refresh to not break user UI
        }
    }

    fun triggerContainerAction(containerId: String, containerName: String, action: ContainerAction, onComplete: (Boolean, String) -> Unit) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                try {
                    _processingContainers.value = _processingContainers.value + containerId
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val token = currentState.token
                    val endpointId = currentState.selectedEndpoint?.Id ?: return@launch
                    
                    val actionStr = when (action) {
                        ContainerAction.START -> "Starting"
                        ContainerAction.STOP -> "Stopping"
                        ContainerAction.RESTART -> "Restarting"
                        ContainerAction.PAUSE -> "Pausing"
                        ContainerAction.UNPAUSE -> "Unpausing"
                        ContainerAction.KILL -> "Killing"
                        ContainerAction.REMOVE -> "Removing"
                    }
                    onComplete(true, "$actionStr $containerName...")
                    
                    when (action) {
                        ContainerAction.START -> api.startContainer(token, endpointId, containerId)
                        ContainerAction.STOP -> api.stopContainer(token, endpointId, containerId)
                        ContainerAction.RESTART -> api.restartContainer(token, endpointId, containerId)
                        ContainerAction.PAUSE -> api.pauseContainer(token, endpointId, containerId)
                        ContainerAction.UNPAUSE -> api.unpauseContainer(token, endpointId, containerId)
                        ContainerAction.KILL -> api.killContainer(token, endpointId, containerId)
                        ContainerAction.REMOVE -> api.removeContainer(token, endpointId, containerId)
                    }
                    
                    val pastTenseStr = when (action) {
                        ContainerAction.START -> "Started"
                        ContainerAction.STOP -> "Stopped"
                        ContainerAction.RESTART -> "Restarted"
                        ContainerAction.PAUSE -> "Paused"
                        ContainerAction.UNPAUSE -> "Unpaused"
                        ContainerAction.KILL -> "Killed"
                        ContainerAction.REMOVE -> "Removed"
                    }
                    onComplete(true, "$pastTenseStr $containerName successfully.")
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                } catch (e: retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                    val actionStr = action.name.lowercase().replaceFirstChar { it.uppercase() }
                    onComplete(true, "$actionStr action sent for $containerName.")
                } catch (e: Exception) {
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                    // Portainer API often returns empty responses that cause EOFExceptions in Retrofit,
                    // but the action actually succeeds.
                    val isEOF = e is java.io.EOFException || e.message?.contains("EOF") == true || e.message?.contains("End of input") == true
                    
                    val actionStr = when (action) {
                        ContainerAction.START -> "Started"
                        ContainerAction.STOP -> "Stopped"
                        ContainerAction.RESTART -> "Restarted"
                        ContainerAction.PAUSE -> "Paused"
                        ContainerAction.UNPAUSE -> "Unpaused"
                        ContainerAction.KILL -> "Killed"
                        ContainerAction.REMOVE -> "Removed"
                    }
                    
                    if (isEOF) {
                        onComplete(true, "$actionStr $containerName successfully.")
                    } else {
                        onComplete(true, "${action.name.lowercase().replaceFirstChar { it.uppercase() }} command sent for $containerName.") 
                    }
                } finally {
                    _processingContainers.value = _processingContainers.value - containerId
                }
            }
        }
    }

    fun removeVolume(volumeName: String, onComplete: (Boolean, String) -> Unit) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                try {
                    _processingVolumes.value = _processingVolumes.value + volumeName
                    onComplete(true, "Removing volume $volumeName...")
                    
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val token = currentState.token
                    val endpointId = currentState.selectedEndpoint?.Id ?: return@launch
                    
                    api.removeVolume(token, endpointId, volumeName)
                    
                    onComplete(true, "Volume $volumeName removed successfully.")
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                } catch (e: retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                    onComplete(true, "Remove command sent for $volumeName (Server: ${e.code()})")
                } catch (e: Exception) {
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                    val isEOF = e is java.io.EOFException || e.message?.contains("EOF") == true || e.message?.contains("End of input") == true
                    if (isEOF) {
                        onComplete(true, "Volume $volumeName removed successfully.")
                    } else {
                        onComplete(true, "Remove command sent for $volumeName.")
                    }
                } finally {
                    _processingVolumes.value = _processingVolumes.value - volumeName
                }
            }
        }
    }

    fun pruneVolumes(onComplete: (Boolean, String) -> Unit) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                try {
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val token = currentState.token
                    val endpointId = currentState.selectedEndpoint?.Id ?: return@launch
                    
                    api.pruneVolumes(token, endpointId)
                    
                    onComplete(true, "Unused volumes pruned successfully!")
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                } catch (e: retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                    onComplete(true, "Prune command sent (Server: ${e.code()})")
                } catch (e: Exception) {
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                    val isEOF = e is java.io.EOFException || e.message?.contains("EOF") == true || e.message?.contains("End of input") == true
                    if (isEOF) {
                        onComplete(true, "Unused volumes pruned successfully.")
                    } else {
                        onComplete(true, "Prune command sent.")
                    }
                }
            }
        }
    }

    fun triggerStackAction(stackId: Int, stackEndpointId: Int, stackName: String, action: StackAction, onComplete: (Boolean, String) -> Unit) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                try {
                    _processingStacks.value = _processingStacks.value + stackId
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val token = currentState.token
                    val targetEndpointId = currentState.selectedEndpoint?.Id ?: stackEndpointId
                    
                    val actionStr = when (action) {
                        StackAction.START -> "Starting"
                        StackAction.STOP -> "Stopping"
                        StackAction.REMOVE -> "Removing"
                    }
                    onComplete(true, "$actionStr stack $stackName...")
                    
                    when (action) {
                        StackAction.START -> api.startStack(token, stackId, targetEndpointId)
                        StackAction.STOP -> api.stopStack(token, stackId, targetEndpointId)
                        StackAction.REMOVE -> api.removeStack(token, stackId, targetEndpointId)
                    }
                    
                    val pastTenseStr = when (action) {
                        StackAction.START -> "Started"
                        StackAction.STOP -> "Stopped"
                        StackAction.REMOVE -> "Removed"
                    }
                    onComplete(true, "$pastTenseStr stack $stackName successfully.")
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                } catch (e: retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                    val actionStr = action.name.lowercase().replaceFirstChar { it.uppercase() }
                    onComplete(true, "$actionStr action sent for stack $stackName.")
                } catch (e: Exception) {
                    kotlinx.coroutines.delay(1000)
                    refreshDashboard()
                    val isEOF = e is java.io.EOFException || e.message?.contains("EOF") == true || e.message?.contains("End of input") == true
                    
                    val actionStr = when (action) {
                        StackAction.START -> "Started"
                        StackAction.STOP -> "Stopped"
                        StackAction.REMOVE -> "Removed"
                    }
                    
                    if (isEOF) {
                        onComplete(true, "$actionStr stack $stackName successfully.")
                    } else {
                        onComplete(true, "${action.name.lowercase().replaceFirstChar { it.uppercase() }} command sent for stack $stackName.") // Still return true so UI updates
                    }
                } finally {
                    _processingStacks.value = _processingStacks.value - stackId
                }
            }
        }
    }

    fun getCustomTemplateFileContent(templateId: Int, onComplete: (String?) -> Unit) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                try {
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val result = api.getCustomTemplateFile(currentState.token, templateId)
                    onComplete(result.FileContent)
                } catch (e: Exception) {
                    onComplete(null)
                }
            }
        } else {
            onComplete(null)
        }
    }

    fun updateCustomTemplate(templateId: Int, title: String, description: String, fileContent: String, onComplete: (Boolean, String) -> Unit) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                try {
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    
                    val request = UpdateCustomTemplateRequest(
                        Title = title,
                        Description = description,
                        FileContent = fileContent
                    )
                    api.updateCustomTemplate(currentState.token, templateId, request)
                    onComplete(true, "Custom template updated successfully")
                    refreshDashboard()
                } catch (e: Exception) {
                    onComplete(false, "Update failed: ${e.localizedMessage}")
                }
            }
        } else {
            onComplete(false, "Not connected")
        }
    }

    fun fetchAppTemplateComposeFile(template: PortainerTemplate, onComplete: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (template.repository == null) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }
                val repoUrl = template.repository.url
                val stackFile = template.repository.stackfile
                if (repoUrl == null || stackFile == null) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }
                
                var downloadUrl = repoUrl
                if (downloadUrl.startsWith("https://github.com/")) {
                    val path = downloadUrl.removePrefix("https://github.com/").removeSuffix("/")
                    downloadUrl = "https://raw.githubusercontent.com/$path/master/$stackFile"
                }
                
                val client = PortainerApiClient.getUnsafeOkHttpClient()
                val req = okhttp3.Request.Builder().url(downloadUrl).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val content = resp.body?.string()
                    withContext(Dispatchers.Main) { onComplete(content) }
                } else {
                    val mainUrl = downloadUrl.replace("/master/", "/main/")
                    val req2 = okhttp3.Request.Builder().url(mainUrl).build()
                    val resp2 = client.newCall(req2).execute()
                    if (resp2.isSuccessful) {
                        val content = resp2.body?.string()
                        withContext(Dispatchers.Main) { onComplete(content) }
                    } else {
                        withContext(Dispatchers.Main) { onComplete(null) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(null) }
            }
        }
    }

    fun deployComposeStack(name: String, composeContent: String, onComplete: (Boolean, String) -> Unit) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                try {
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val token = currentState.token
                    val endpointId = currentState.selectedEndpoint?.Id ?: return@launch

                    val request = CreateStackStringRequest(
                        name = name,
                        env = emptyList(),
                        stackFileContent = composeContent
                    )
                    
                    api.createStackString(token, endpointId, request)
                    
                    // refresh after successful deployment
                    val allStacks = try { api.getStacks(token) } catch (e: Exception) { null }
                    val containers = try { api.getContainers(token, endpointId) } catch (e: Exception) { emptyList() }
                    val stacks = buildFilteredStacks(allStacks, containers, endpointId)
                    
                    _nativeSessionState.value = currentState.copy(
                        stacks = stacks,
                        containers = containers
                    )
                    
                    onComplete(true, "Stack deployed successfully")
                } catch (e: Exception) {
                    onComplete(false, "Deployment failed: ${e.message}")
                }
            }
        } else {
            onComplete(false, "Not connected")
        }
    }

    fun deployApp(name: String, image: String, containerPort: String, hostPort: String, onComplete: (Boolean, String) -> Unit) {
        val currentState = _nativeSessionState.value
        if (currentState is NativeSessionState.Success) {
            viewModelScope.launch {
                try {
                    val connection = _selectedConnection.value ?: return@launch
                    val api = PortainerApiClient.create(connection.url)
                    val token = currentState.token
                    val endpointId = currentState.selectedEndpoint?.Id ?: return@launch

                    // 1. Trigger Image Pull (Portainer blocks until pulled or timeouts)
                    try {
                        api.pullImage(token, endpointId, fromImage = image)
                    } catch (e: Exception) {
                        // Sometimes pulling timeouts on client but succeds on server, we continue
                    }

                    // 2. Create Container
                    val portBindings = if (containerPort.isNotBlank() && hostPort.isNotBlank()) {
                        mapOf("$containerPort/tcp" to listOf(PortBinding(hostPort)))
                    } else null

                    val exposedPorts: Map<String, Any>? = if (containerPort.isNotBlank()) {
                        mapOf("$containerPort/tcp" to emptyMap<String, Any>())
                    } else null

                    val request = CreateContainerRequest(
                        Image = image,
                        ExposedPorts = exposedPorts,
                        HostConfig = HostConfig(PortBindings = portBindings)
                    )

                    val response = api.createContainer(token, endpointId, name = name, request = request)

                    // 3. Start Container
                    api.startContainer(token, endpointId, response.Id)

                    onComplete(true, "Successfully deployed $name")
                    refreshDashboard()
                } catch (e: Exception) {
                    onComplete(false, "Deployment failed: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    fun addConnection(name: String, url: String, username: String = "", password: String = "", rememberPassword: Boolean = false) {
        viewModelScope.launch {
            val formattedUrl = formatUrl(url)
            repository.insert(
                PortainerConnection(
                    name = name,
                    url = formattedUrl,
                    username = username,
                    password = password,
                    rememberPassword = rememberPassword
                )
            )
        }
    }

    fun updateConnection(connection: PortainerConnection) {
        viewModelScope.launch {
            val formattedUrl = formatUrl(connection.url)
            repository.update(connection.copy(url = formattedUrl))
        }
    }

    fun deleteConnection(connection: PortainerConnection) {
        viewModelScope.launch {
            repository.delete(connection)
            if (_selectedConnection.value?.id == connection.id) {
                _selectedConnection.value = null
            }
        }
    }

    private fun buildFilteredStacks(
        allStacks: List<PortainerStack>?,
        containers: List<PortainerContainer>?,
        endpointId: Int
    ): List<PortainerStack> {
        val result = mutableListOf<PortainerStack>()
        if (allStacks != null) {
            result.addAll(allStacks.filter { it.EndpointId == endpointId })
        }
        val portainerStackNames = result.map { it.Name }.toSet()
        val unmanagedNames = mutableSetOf<String>()
        
        containers?.forEach { container ->
            val projectLabel = container.Labels?.get("com.docker.compose.project")
            if (projectLabel != null && !portainerStackNames.contains(projectLabel)) {
                unmanagedNames.add(projectLabel)
            }
        }
        
        unmanagedNames.forEach { name ->
            result.add(
                PortainerStack(
                    Id = -(name.hashCode() and 0x7FFFFFFF), // Ensure stable, negative ID to prevent conflicts and LazyColumn crashes
                    Name = name,
                    Type = 2, // 2 indicates Compose Stack (approximate)
                    EndpointId = endpointId,
                    Status = 1, // Assume active if containers are present
                    Env = null
                )
            )
        }
        return result
    }

    private fun formatUrl(url: String): String {
        val trimmed = url.trim()
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed" // Default to modern secure HTTPS
        } else {
            trimmed
        }
    }
}


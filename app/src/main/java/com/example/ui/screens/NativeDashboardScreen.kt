package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.viewmodel.ContainerAction
import com.example.ui.viewmodel.StackAction
import com.example.ui.viewmodel.ConnectionViewModel
import com.example.ui.viewmodel.NativeSessionState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeDashboardScreen(
    connection: PortainerConnection,
    nativeState: NativeSessionState,
    viewModel: ConnectionViewModel,
    onClose: () -> Unit,
    onOpenWebView: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = connection.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = connection.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to List")
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (nativeState) {
                is NativeSessionState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { viewModel.connectNatively(connection) }) {
                            Text("Connect Natively")
                        }
                    }
                }
                is NativeSessionState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Syncing with Portainer Nodes securely...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is NativeSessionState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Connection Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Native Connection Failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            nativeState.message,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = { viewModel.connectNatively(connection) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Retry Native")
                            }
                        }
                    }
                }
                is NativeSessionState.Success -> {
                    NativeSuccessDashboard(
                        state = nativeState,
                        viewModel = viewModel,
                        onOpenWebView = onOpenWebView
                    )
                }
            }
        }
    }
}

sealed class DashboardSubScreen {
    data class ContainerLogs(val container: PortainerContainer) : DashboardSubScreen()
    data class ContainerConsole(val container: PortainerContainer) : DashboardSubScreen()
    data class StackEdit(val stack: PortainerStack) : DashboardSubScreen()
    data class CustomTemplateEdit(val template: PortainerCustomTemplate) : DashboardSubScreen()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NativeSuccessDashboard(
    state: NativeSessionState.Success,
    viewModel: ConnectionViewModel,
    onOpenWebView: (String?) -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0=Dashboard, 1=Containers, 2=Stacks, 3=Deploy, 4=Templates
    var childScreen by remember { mutableStateOf<DashboardSubScreen?>(null) }
    var sortAlphabetical by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var showVolumesPopup by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var templateSearchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    
    val processingContainers by viewModel.processingContainers.collectAsState()
    val processingStacks by viewModel.processingStacks.collectAsState()
    val processingVolumes by viewModel.processingVolumes.collectAsState()
    
    if (childScreen != null) {
        when (val screen = childScreen) {
            is DashboardSubScreen.ContainerLogs -> {
                ContainerLogsScreen(
                    state = state,
                    container = screen.container,
                    viewModel = viewModel,
                    onBack = { childScreen = null }
                )
            }
            is DashboardSubScreen.ContainerConsole -> {
                ContainerConsoleScreen(
                    state = state,
                    container = screen.container,
                    viewModel = viewModel,
                    onBack = { childScreen = null }
                )
            }
            is DashboardSubScreen.StackEdit -> {
                StackEditScreen(
                    state = state,
                    stack = screen.stack,
                    viewModel = viewModel,
                    onBack = { childScreen = null }
                )
            }
            is DashboardSubScreen.CustomTemplateEdit -> {
                CustomTemplateEditScreen(
                    state = state,
                    template = screen.template,
                    viewModel = viewModel,
                    onBack = { childScreen = null }
                )
            }
            else -> {}
        }
        return
    }

    // Environment Switch dropdown helper
    var showEnvDropdown by remember { mutableStateOf(false) }
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                viewModel.performRefresh()
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Environment Selector Bar
        val selected = state.selectedEndpoint
        if (selected != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (state.endpoints.size > 1) showEnvDropdown = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (selected.Status == 1) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Environment: ${selected.Name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Type: Docker Endpoint • URL: ${selected.URL}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.endpoints.size > 1) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Environment"
                            )
                        }
                    }
                    if (state.endpoints.size > 1) {
                        DropdownMenu(
                            expanded = showEnvDropdown,
                            onDismissRequest = { showEnvDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.95f)
                        ) {
                            state.endpoints.forEach { ep ->
                                DropdownMenuItem(
                                    text = { Text(ep.Name) },
                                    onClick = {
                                        viewModel.selectEndpoint(ep)
                                        showEnvDropdown = false
                                    },
                                    enabled = ep.Status == 1,
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    color = if (ep.Status == 1) Color(0xFF4CAF50) else Color(0xFFF44336),
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Overview") },
                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Containers (${state.containers.size})") },
                icon = { Icon(Icons.Default.Dns, contentDescription = null) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Stacks (${state.stacks.size})") },
                icon = { Icon(Icons.Default.Layers, contentDescription = null) }
            )
            Tab(
                selected = activeTab == 3,
                onClick = { activeTab = 3 },
                text = { Text("Deploy") },
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) }
            )
            Tab(
                selected = activeTab == 4,
                onClick = { activeTab = 4 },
                text = { Text("Templates") },
                icon = { Icon(Icons.Default.Apps, contentDescription = null) }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        var deployAppName by remember { mutableStateOf("") }
        var deployImageName by remember { mutableStateOf("") }
        var deployHostPort by remember { mutableStateOf("") }
        var deployContainerPort by remember { mutableStateOf("") }
        var deployComposeMode by remember { mutableStateOf(false) }
        var deployComposeContent by remember { mutableStateOf("") }
        
        when (activeTab) {
            0 -> {
                // OVERVIEW TAB
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        val snapshot = selected?.Snapshots?.firstOrNull()
                        val total = snapshot?.ContainersCount ?: state.containers.size
                        val running = snapshot?.RunningContainersCount ?: state.containers.count { it.State?.lowercase() == "running" }
                        val stopped = snapshot?.StoppedContainersCount ?: (total - running)
                        
                        Text(
                            text = "Node Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Total Containers",
                                value = total.toString(),
                                icon = Icons.Default.Dns,
                                cardColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    sortAlphabetical = false
                                    statusFilter = null
                                    activeTab = 1
                                }
                            )
                            StatCard(
                                title = "Running",
                                value = running.toString(),
                                icon = Icons.Default.PlayArrow,
                                cardColor = Color(0xFFE8F5E9),
                                tintColor = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    sortAlphabetical = false
                                    statusFilter = "running"
                                    activeTab = 1
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Stopped",
                                value = stopped.toString(),
                                icon = Icons.Default.Stop,
                                cardColor = Color(0xFFFFEBEE),
                                tintColor = Color(0xFFC62828),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    sortAlphabetical = false
                                    statusFilter = "exited"
                                    activeTab = 1
                                }
                            )
                            StatCard(
                                title = "Compose Stacks",
                                value = state.stacks.size.toString(),
                                icon = Icons.Default.Layers,
                                cardColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    sortAlphabetical = false
                                    statusFilter = null
                                    activeTab = 2
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Volumes",
                                value = state.volumes.size.toString(),
                                icon = Icons.Default.Storage,
                                cardColor = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.weight(1f),
                                onClick = { showVolumesPopup = true }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            1 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.weight(1f)
                        ) {
                            val options = listOf("All", "Running", "Stopped", "Paused")
                            options.forEachIndexed { index, label ->
                                val selected = when (label) {
                                    "All" -> statusFilter == null
                                    "Running" -> statusFilter == "running"
                                    "Stopped" -> statusFilter == "exited"
                                    "Paused" -> statusFilter == "paused"
                                    else -> false
                                }
                                SegmentedButton(
                                    selected = selected,
                                    onClick = {
                                        statusFilter = when (label) {
                                            "All" -> null
                                            "Running" -> "running"
                                            "Stopped" -> "exited"
                                            "Paused" -> "paused"
                                            else -> null
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                                ) {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        
                        Spacer(Modifier.width(8.dp))
                        
                        FilterChip(
                            selected = sortAlphabetical,
                            onClick = { sortAlphabetical = !sortAlphabetical },
                            label = { Text("A-Z") }
                        )
                    }
                    
                    val filteredContainers = state.containers.filter { container ->
                        val statusMatch = if (statusFilter != null) {
                            container.State?.lowercase() == statusFilter
                        } else true
                        
                        statusMatch
                    }.let { list ->
                        if (sortAlphabetical) {
                            list.sortedBy { it.Names?.firstOrNull() ?: it.Id }
                        } else {
                            list // Keeping default derived order
                        }
                    }
                    
                    if (filteredContainers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No containers matching the filter rules",
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 16.dp, start = 12.dp, end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredContainers, key = { it.Id }) { container ->
                                val stats = state.containerStats[container.Id]
                                ContainerItemRow(
                                    container = container,
                                    stats = stats,
                                    isProcessing = processingContainers.contains(container.Id),
                                    onAction = { action ->
                                        val cName = container.Names?.firstOrNull()?.removePrefix("/") ?: container.Id.take(8)
                                        val actionStr = when (action) {
                                            ContainerAction.START -> "Starting"
                                            ContainerAction.STOP -> "Stopping"
                                            ContainerAction.RESTART -> "Restarting"
                                            ContainerAction.PAUSE -> "Pausing"
                                            ContainerAction.UNPAUSE -> "Unpausing"
                                            ContainerAction.KILL -> "Killing"
                                            ContainerAction.REMOVE -> "Removing"
                                        }
                                        viewModel.triggerContainerAction(container.Id, cName, action) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLogs = {
                                        childScreen = DashboardSubScreen.ContainerLogs(container)
                                    },
                                    onConsole = {
                                        childScreen = DashboardSubScreen.ContainerConsole(container)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            2 -> {
                if (state.stacks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No active Docker stacks registered.",
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.stacks, key = { it.Id }) { stack ->
                            val using = state.containers.filter { it.Labels?.get("com.docker.compose.project") == stack.Name }
                            StackItemRow(stack = stack, stackContainers = using, isProcessing = processingStacks.contains(stack.Id), onAction = { action ->
                                val actionStr = when (action) {
                                    StackAction.START -> "Starting"
                                    StackAction.STOP -> "Stopping"
                                    StackAction.REMOVE -> "Removing"
                                }
                                viewModel.triggerStackAction(stack.Id, stack.EndpointId, stack.Name, action) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }, onEdit = {
                                childScreen = DashboardSubScreen.StackEdit(stack)
                            })
                        }
                    }
                }
            }
            3 -> {
                var isDeploying by remember { mutableStateOf(false) }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Custom Deployment",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Explicit Environment Selection for Deployment
                        var showDeployEnvDropdown by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = state.selectedEndpoint?.Name ?: "Select Environment",
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                readOnly = true,
                                label = { Text("Target Environment") },
                                trailingIcon = {
                                    IconButton(onClick = { showDeployEnvDropdown = !showDeployEnvDropdown }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Environment")
                                    }
                                },
                                interactionSource = remember { MutableInteractionSource() }.also { interactionSource ->
                                    LaunchedEffect(interactionSource) {
                                        interactionSource.interactions.collect {
                                            if (it is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                                showDeployEnvDropdown = true
                                            }
                                        }
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = showDeployEnvDropdown,
                                onDismissRequest = { showDeployEnvDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                state.endpoints.forEach { ep ->
                                    DropdownMenuItem(
                                        text = { Text(ep.Name) },
                                        onClick = {
                                            viewModel.selectEndpoint(ep)
                                            showDeployEnvDropdown = false
                                        },
                                        enabled = ep.Status == 1
                                    )
                                }
                            }
                        }

                        // Toggle between Container and Compose Stack
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !deployComposeMode,
                                onClick = { deployComposeMode = false },
                                label = { Text("Container") }
                            )
                            FilterChip(
                                selected = deployComposeMode,
                                onClick = { deployComposeMode = true },
                                label = { Text("Compose Stack") }
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = deployAppName,
                                    onValueChange = { deployAppName = it },
                                    label = { Text(if (deployComposeMode) "Stack Name" else "Container Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                
                                if (deployComposeMode) {
                                    OutlinedTextField(
                                        value = deployComposeContent,
                                        onValueChange = { deployComposeContent = it },
                                        label = { Text("docker-compose.yml content") },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = deployImageName,
                                        onValueChange = { deployImageName = it },
                                        label = { Text("Image (e.g., nginx:latest)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = deployHostPort,
                                            onValueChange = { deployHostPort = it },
                                            label = { Text("Host Port") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = deployContainerPort,
                                            onValueChange = { deployContainerPort = it },
                                            label = { Text("Container Port") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }
                                
                                Button(
                                    onClick = {
                                        if (deployComposeMode) {
                                            if (deployAppName.isBlank() || deployComposeContent.isBlank()) {
                                                Toast.makeText(context, "Name and Compose Content are required", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isDeploying = true
                                            viewModel.deployComposeStack(
                                                name = deployAppName,
                                                composeContent = deployComposeContent
                                            ) { success, msg ->
                                                isDeploying = false
                                                Toast.makeText(context, msg, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                                                if (success) {
                                                    activeTab = 2 // Go to Stacks
                                                    deployAppName = ""
                                                    deployComposeContent = ""
                                                }
                                            }
                                        } else {
                                            if (deployAppName.isBlank() || deployImageName.isBlank()) {
                                                Toast.makeText(context, "Name and Image are required", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isDeploying = true
                                            viewModel.deployApp(
                                                name = deployAppName,
                                                image = deployImageName,
                                                containerPort = deployContainerPort,
                                                hostPort = deployHostPort
                                            ) { success, msg ->
                                                isDeploying = false
                                                Toast.makeText(context, msg, if (success) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                                                if (success) {
                                                    activeTab = 1
                                                    deployAppName = ""
                                                    deployImageName = ""
                                                    deployHostPort = ""
                                                    deployContainerPort = ""
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    enabled = !isDeploying
                                ) {
                                    if (isDeploying) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Deploying...")
                                    } else {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Deploy Application")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            4 -> {
                val filteredCustomTemplates = state.customTemplates.filter { templateSearchQuery.isEmpty() || (it.Title?.contains(templateSearchQuery, ignoreCase = true) == true) || (it.Description?.contains(templateSearchQuery, ignoreCase = true) == true) }
                val filteredAppTemplates = state.templates.filter { templateSearchQuery.isEmpty() || (it.title?.contains(templateSearchQuery, ignoreCase = true) == true) || (it.description?.contains(templateSearchQuery, ignoreCase = true) == true) }

                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = templateSearchQuery,
                        onValueChange = { templateSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search Templates...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (templateSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { templateSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = "Custom Templates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (filteredCustomTemplates.isEmpty()) {
                                Text(
                                    if (state.customTemplates.isEmpty()) "No Custom Templates available." else "No Custom Templates match your search.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (filteredCustomTemplates.isNotEmpty()) {
                            items(filteredCustomTemplates) { template ->
                                CustomTemplateCard(
                                    template = template,
                                    viewModel = viewModel,
                                    onEdit = {
                                        childScreen = DashboardSubScreen.CustomTemplateEdit(template)
                                    },
                                    onDeploy = { title, content ->
                                        deployAppName = title.lowercase().replace(" ", "-")
                                        deployComposeMode = true
                                        deployComposeContent = content
                                        activeTab = 3
                                    }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "App Templates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (filteredAppTemplates.isEmpty()) {
                                Text(
                                    if (state.templates.isEmpty()) "No App Templates available from server." else "No App Templates match your search.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (filteredAppTemplates.isNotEmpty()) {
                            items(filteredAppTemplates) { template ->
                                AppTemplateCard(
                                    template = template,
                                    viewModel = viewModel,
                                    onDeployContainer = { 
                                        deployAppName = (template.title ?: "app").lowercase().replace(" ", "-")
                                        deployImageName = template.image ?: ""
                                        deployContainerPort = template.ports?.firstOrNull() ?: ""
                                        deployHostPort = ""
                                        deployComposeMode = false
                                        activeTab = 3
                                    },
                                    onDeployCompose = { content ->
                                        deployAppName = (template.title ?: "app").lowercase().replace(" ", "-")
                                        deployComposeMode = true
                                        deployComposeContent = content
                                        activeTab = 3
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    } // Close PullToRefreshBox
    
    if (showVolumesPopup) {
        AlertDialog(
            onDismissRequest = { showVolumesPopup = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Volumes")
                    IconButton(onClick = {
                        viewModel.pruneVolumes { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = "Prune unused volumes",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            text = {
                if (state.volumes.isEmpty()) {
                    Text("No volumes found.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth()
                    ) {
                        items(state.volumes, key = { it.Name }) { volume ->
                            val using = state.containers.filter { c ->
                                c.Mounts?.any { it.Name == volume.Name } == true
                            }
                            VolumeItemRow(
                                volume = volume,
                                usingContainers = using,
                                isProcessing = processingVolumes.contains(volume.Name),
                                onDelete = {
                                    viewModel.removeVolume(volume.Name) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVolumesPopup = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun AppTemplateCard(
    template: PortainerTemplate,
    viewModel: ConnectionViewModel,
    onDeployContainer: () -> Unit,
    onDeployCompose: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(template.title ?: "Unknown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        when(template.type) { 1 -> "Container"; 2 -> "Swarm Stack"; 3 -> "Compose Stack"; else -> "Unknown" },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (!template.description.isNullOrBlank()) {
                Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (!template.image.isNullOrBlank()) {
                    Text("Image", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(template.image, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (!template.ports.isNullOrEmpty()) {
                    Text("Exposed Ports", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val portsStr = template.ports.joinToString(", ")
                    Text(portsStr, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (!template.env.isNullOrEmpty()) {
                    Text("Environment Variables", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    template.env.forEach { env ->
                        Text("${env.name}${env.default?.let { "=$it" } ?: ""}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (template.repository != null) {
                    Text("Repository", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("${template.repository.url} (${template.repository.stackfile})", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Button(
                    onClick = {
                        if (template.type == 1) {
                            onDeployContainer()
                        } else if (template.type == 2 || template.type == 3) {
                            isLoading = true
                            viewModel.fetchAppTemplateComposeFile(template) { content ->
                                isLoading = false
                                if (content != null) {
                                    onDeployCompose(content)
                                } else {
                                    Toast.makeText(context, "Failed to download compose file from repository.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching...")
                    } else {
                        Text(if (template.type == 1) "Deploy Container" else "Deploy Stack")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomTemplateCard(
    template: PortainerCustomTemplate,
    viewModel: ConnectionViewModel,
    onEdit: () -> Unit,
    onDeploy: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var loadedContent by remember { mutableStateOf(template.FileContent) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded && loadedContent.isNullOrBlank()) {
            isLoading = true
            viewModel.getCustomTemplateFileContent(template.Id) { content ->
                loadedContent = content
                isLoading = false
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(template.Title ?: "Unknown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        when(template.Type) { 1 -> "Container"; 2 -> "Swarm Stack"; 3 -> "Compose Stack"; else -> "Unknown" },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (!template.Description.isNullOrBlank()) {
                Text(template.Description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (!loadedContent.isNullOrBlank()) {
                    Text("Compose File Structure", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            item {
                                Text(
                                    loadedContent ?: "", 
                                    color = Color(0xFFA9B7C6), 
                                    style = MaterialTheme.typography.bodySmall, 
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit")
                    }
                    Button(
                        onClick = { 
                            onDeploy(template.Title ?: "", loadedContent ?: "")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Deploy")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cardColor: Color,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        onClick = { onClick?.invoke() },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (tintColor != Color.Unspecified) tintColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tintColor != Color.Unspecified) tintColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = if (tintColor != Color.Unspecified) tintColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ContainerItemRow(
    container: PortainerContainer,
    stats: com.example.data.ContainerStatsResponse? = null,
    isProcessing: Boolean = false,
    onAction: (ContainerAction) -> Unit,
    onLogs: (() -> Unit)? = null,
    onConsole: (() -> Unit)? = null
) {
    val isRunning = container.State?.lowercase() == "running"
    val isStopped = container.State?.lowercase() == "exited"
    
    val badgeColor = when {
        isRunning -> Color(0xFFE8F5E9)
        isStopped -> Color(0xFFFFEBEE)
        else -> Color(0xFFFFF3E0)
    }
    val badgeTextColor = when {
        isRunning -> Color(0xFF2E7D32)
        isStopped -> Color(0xFFC62828)
        else -> Color(0xFFE65100)
    }
    
    val name = container.Names?.firstOrNull()?.removePrefix("/") ?: "Unnamed"
    val image = container.Image ?: "No Image ID"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(badgeColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = (container.State ?: "unknown").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Image: $image",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (container.Status != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = container.Status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (stats != null) {
                        val cpuDelta = stats.cpu_stats?.cpu_usage?.total_usage?.minus(stats.precpu_stats?.cpu_usage?.total_usage ?: 0) ?: 0
                        val systemDelta = stats.cpu_stats?.system_cpu_usage?.minus(stats.precpu_stats?.system_cpu_usage ?: 0) ?: 0
                        val onlineCpus = stats.cpu_stats?.online_cpus ?: 1
                        val cpuPercent = if (systemDelta > 0 && cpuDelta > 0) {
                            (cpuDelta.toDouble() / systemDelta.toDouble()) * onlineCpus * 100.0
                        } else 0.0
                        
                        val memUsage = stats.memory_stats?.usage ?: 0
                        val memLimit = stats.memory_stats?.limit ?: 0
                        
                        fun formatMem(bytes: Long): String {
                            val kb = bytes / 1024.0
                            val mb = kb / 1024.0
                            val gb = mb / 1024.0
                            return when {
                                gb >= 1.0 -> String.format("%.2f GB", gb)
                                mb >= 1.0 -> String.format("%.2f MB", mb)
                                kb >= 1.0 -> String.format("%.2f KB", kb)
                                else -> "$bytes B"
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CPU: ${String.format("%.2f", cpuPercent)}% • RAM: ${formatMem(memUsage)} / ${formatMem(memLimit)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (isProcessing) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(start = 8.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isRunning) {
                            IconButton(
                                onClick = { onAction(ContainerAction.START) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = Color(0xFF2E7D32)
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start Container")
                            }
                        } else {
                            IconButton(
                                onClick = { onAction(ContainerAction.STOP) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = Color(0xFFC62828)
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop Container")
                            }
                        }
                        IconButton(
                            onClick = { onAction(ContainerAction.RESTART) },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart Container")
                        }
                        
                        var showOptions by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showOptions = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                if (isRunning && container.State?.lowercase() != "paused") {
                                    DropdownMenuItem(
                                        text = { Text("Pause") },
                                        onClick = { onAction(ContainerAction.PAUSE); showOptions = false },
                                        leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) }
                                    )
                                } else if (container.State?.lowercase() == "paused") {
                                    DropdownMenuItem(
                                        text = { Text("Unpause") },
                                        onClick = { onAction(ContainerAction.UNPAUSE); showOptions = false },
                                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                                    )
                                }
                                if (isRunning) {
                            DropdownMenuItem(
                                text = { Text("Kill") },
                                onClick = { onAction(ContainerAction.KILL); showOptions = false },
                                leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = { onAction(ContainerAction.REMOVE); showOptions = false },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                        if (onLogs != null) {
                            DropdownMenuItem(
                                text = { Text("Logs") },
                                onClick = { onLogs(); showOptions = false },
                                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) }
                            )
                        }
                        if (onConsole != null) {
                            DropdownMenuItem(
                                text = { Text("Console") },
                                onClick = { onConsole(); showOptions = false },
                                leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null) }
                            )
                        }
                    } // DropdownMenu
                } // Box
            } // Row
            } // else
        } // Row
        } // Column
    }
}

@Composable
fun StackItemRow(stack: PortainerStack, stackContainers: List<PortainerContainer>, isProcessing: Boolean = false, onAction: (StackAction) -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stack.Name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Type: ${if (stack.Type == 2) "Docker Compose" else "Swarm Stack"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stackContainers.isNotEmpty()) {
                    Text(
                        text = "Containers: " + stackContainers.mapNotNull { it.Names?.firstOrNull()?.removePrefix("/") }.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isProcessing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (stack.Status == 1) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (stack.Status == 1) "ACTIVE" else "INACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (stack.Status == 1) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                if (stack.Id > 0) {
                    var showOptions by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOptions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                            if (stack.Type == 2) {
                                if (stack.Status == 1) {
                                    DropdownMenuItem(
                                        text = { Text("Stop") },
                                        onClick = { onAction(StackAction.STOP); showOptions = false },
                                        leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Start") },
                                        onClick = { onAction(StackAction.START); showOptions = false },
                                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text("Edit Stack") },
                                onClick = { onEdit(); showOptions = false },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove") },
                                onClick = { onAction(StackAction.REMOVE); showOptions = false },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                            )
                        }
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "EXTERNAL",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VolumeItemRow(
    volume: PortainerVolume,
    usingContainers: List<PortainerContainer>,
    isProcessing: Boolean = false,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = volume.Name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Driver: ${volume.Driver}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (usingContainers.isNotEmpty()) {
                    Text(
                        text = "In use by: " + usingContainers.mapNotNull { it.Names?.firstOrNull()?.removePrefix("/") }.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (isProcessing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp), enabled = usingContainers.isEmpty()) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Volume",
                        tint = if (usingContainers.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}
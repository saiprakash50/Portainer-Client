package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.PortainerConnection
import com.example.security.BiometricHelper
import com.example.security.BiometricStatus
import com.example.ui.components.PortainerWebView
import com.example.ui.viewmodel.ConnectionViewModel
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortainerAppScreen(
    activity: FragmentActivity,
    viewModel: ConnectionViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connections by viewModel.connections.collectAsState()
    val selectedConnection by viewModel.selectedConnection.collectAsState()
    val nativeState by viewModel.nativeSessionState.collectAsState()

    // Authentication States
    val biometricStatus = remember { BiometricHelper.isBiometricsAvailable(context) }
    var isAuthenticated by androidx.compose.runtime.saveable.rememberSaveable { 
        mutableStateOf(biometricStatus != BiometricStatus.AVAILABLE) 
    }
    var authError by remember { mutableStateOf<String?>(null) }

    // View state toggles
    var webViewPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    // Dialog States
    var showAddDialog by remember { mutableStateOf(false) }
    var connectionToEdit by remember { mutableStateOf<PortainerConnection?>(null) }

    // Reset web view toggle on new connection select
    LaunchedEffect(selectedConnection) {
        webViewPath = null
    }

    // Trigger biometric prompt on startup if biometrics are available
    LaunchedEffect(biometricStatus) {
        if (biometricStatus == BiometricStatus.AVAILABLE && !isAuthenticated) {
            try {
                kotlinx.coroutines.delay(500) // Ensure activity lifecycle is fully stable and resumed
                BiometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.biometric_auth_title),
                    subtitle = context.getString(R.string.biometric_auth_subtitle),
                    negativeButtonText = context.getString(R.string.biometric_auth_negative),
                    onSuccess = {
                        isAuthenticated = true
                        authError = null
                    },
                    onError = { _, errString ->
                        authError = errString.toString()
                    }
                )
            } catch (e: Exception) {
                authError = "Tap unlock to authenticate"
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isAuthenticated && biometricStatus == BiometricStatus.AVAILABLE) {
            // HIGH-FIDELITY BIOMETRIC GATE SCREEN
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .testTag("biometric_gate_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Security Screen Lock",
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Portainer connection endpoints, configurations, and session cookies are cryptographically locked on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                BiometricHelper.authenticate(
                                    activity = activity,
                                    title = context.getString(R.string.biometric_auth_title),
                                    subtitle = context.getString(R.string.biometric_auth_subtitle),
                                    negativeButtonText = context.getString(R.string.biometric_auth_negative),
                                    onSuccess = {
                                        isAuthenticated = true
                                        authError = null
                                    },
                                    onError = { _, errString ->
                                        authError = errString.toString()
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("unlock_biometric_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint scan"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock Application")
                        }

                        authError?.let { err ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Authentication failed: $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // MAIN APPLICATION LAYOUT
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    modifier = Modifier.size(28.dp),
                                    contentDescription = "Portainer Connection",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.app_name),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            // If biometrics are available, support instant manual locking!
                            if (biometricStatus == BiometricStatus.AVAILABLE) {
                                IconButton(onClick = { isAuthenticated = false }) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Lock App Now"
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("add_connection_fab"),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Portainer Instance"
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    // Informative warning box if biometrics are configured as unavailable
                    if (biometricStatus != BiometricStatus.AVAILABLE) {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.warningContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Security Note",
                                    tint = MaterialTheme.colorScheme.onWarningContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.biometric_not_available),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onWarningContainer
                                )
                            }
                        }
                    }

                    if (connections.isEmpty()) {
                        // EMPTY STATE UX
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = "No connections",
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.no_connections),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "To start managing environments, press the Add button below to register a secure Portainer host address.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        // DASHBOARD CONNECTION PROFILES
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(connections, key = { it.id }) { connection ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectConnection(connection)
                                        }
                                        .testTag("connection_card_${connection.id}"),
                                    shape = RoundedCornerShape(24.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Dns,
                                                    contentDescription = "Instance logo",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = connection.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = connection.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(onClick = { connectionToEdit = connection }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Profile",
                                                tint = MaterialTheme.colorScheme.outline
                                            )
                                        }

                                        IconButton(onClick = { viewModel.deleteConnection(connection) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Profile",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FULLSCREEN WEBVIEW OR NATIVE OVERLAY FOR CONNECTED SESSION
        AnimatedVisibility(
            visible = (selectedConnection != null && isAuthenticated),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            selectedConnection?.let { conn ->
                val hasCredentials = conn.username.isNotBlank() && conn.password.isNotBlank()
                
                if (hasCredentials && webViewPath == null) {
                    NativeDashboardScreen(
                        connection = conn,
                        nativeState = nativeState,
                        viewModel = viewModel,
                        onClose = { viewModel.selectConnection(null) },
                        onOpenWebView = { path -> webViewPath = path ?: "" },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PortainerWebView(
                        url = conn.url.removeSuffix("/") + (webViewPath ?: ""),
                        title = conn.name,
                        username = conn.username,
                        password = conn.password,
                        onClose = { 
                            if (hasCredentials && webViewPath != null) {
                                webViewPath = null
                            } else {
                                viewModel.selectConnection(null) 
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ADD CONNECTION DIALOG
        if (showAddDialog) {
            ConnectionFormDialog(
                title = stringResource(R.string.add_connection),
                onDismiss = { showAddDialog = false },
                onConfirm = { name, url, username, password, rememberPassword ->
                    viewModel.addConnection(name, url, username, password, rememberPassword)
                    showAddDialog = false
                    Toast.makeText(context, "Connection added successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // EDIT CONNECTION DIALOG
        connectionToEdit?.let { connection ->
            ConnectionFormDialog(
                title = stringResource(R.string.edit_connection),
                initialName = connection.name,
                initialUrl = connection.url,
                initialUsername = connection.username,
                initialPassword = connection.password,
                initialRememberPassword = connection.rememberPassword,
                onDismiss = { connectionToEdit = null },
                onConfirm = { name, url, username, password, rememberPassword ->
                    viewModel.updateConnection(
                        connection.copy(
                            name = name,
                            url = url,
                            username = username,
                            password = password,
                            rememberPassword = rememberPassword
                        )
                    )
                    connectionToEdit = null
                    Toast.makeText(context, "Connection updated successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ConnectionFormDialog(
    title: String,
    initialName: String = "",
    initialUrl: String = "",
    initialUsername: String = "",
    initialPassword: String = "",
    initialRememberPassword: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, username: String, password: String, rememberPassword: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var rememberPassword by remember { mutableStateOf(initialRememberPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    val usernameAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Username),
            onFill = { username = it }
        )
    }

    val passwordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { password = it }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Connection Name (e.g., Home Swarm)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connection_name_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Host URL (e.g., 192.168.1.100:9443)") },
                    singleLine = true,
                    placeholder = { Text("https://host_address:port") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connection_url_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.label_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { usernameAutofillNode.boundingBox = it.boundsInWindow() }
                        .onFocusChanged { focusState ->
                            autofillTree.children.remove(usernameAutofillNode.id)
                            if (focusState.isFocused) {
                                autofillTree.children[usernameAutofillNode.id] = usernameAutofillNode
                                autofill?.requestAutofillForNode(usernameAutofillNode)
                            } else {
                                autofill?.cancelAutofillForNode(usernameAutofillNode)
                            }
                        }
                        .testTag("connection_username_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.label_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { passwordAutofillNode.boundingBox = it.boundsInWindow() }
                        .onFocusChanged { focusState ->
                            autofillTree.children.remove(passwordAutofillNode.id)
                            if (focusState.isFocused) {
                                autofillTree.children[passwordAutofillNode.id] = passwordAutofillNode
                                autofill?.requestAutofillForNode(passwordAutofillNode)
                            } else {
                                autofill?.cancelAutofillForNode(passwordAutofillNode)
                            }
                        }
                        .testTag("connection_password_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { rememberPassword = !rememberPassword }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberPassword,
                        onCheckedChange = { rememberPassword = it },
                        modifier = Modifier.testTag("remember_password_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.label_remember_password),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                error?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || url.isBlank()) {
                        error = "Name and URL are required."
                    } else {
                        val finalPassword = if (rememberPassword) password.trim() else ""
                        onConfirm(name.trim(), url.trim(), username.trim(), finalPassword, rememberPassword)
                    }
                },
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_dismiss_button")
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

// Simple color helper for custom warning theme status
val ColorScheme.warningContainer: androidx.compose.ui.graphics.Color
    @Composable get() = if (isSystemInDarkTheme()) {
        androidx.compose.ui.graphics.Color(0xFF4D3D00)
    } else {
        androidx.compose.ui.graphics.Color(0xFFFFF2D0)
    }

val ColorScheme.onWarningContainer: androidx.compose.ui.graphics.Color
    @Composable get() = if (isSystemInDarkTheme()) {
        androidx.compose.ui.graphics.Color(0xFFFFE082)
    } else {
        androidx.compose.ui.graphics.Color(0xFF5D4037)
    }

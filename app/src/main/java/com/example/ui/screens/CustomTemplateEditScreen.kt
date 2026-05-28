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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTemplateEditScreen(
    state: NativeSessionState.Success,
    template: PortainerCustomTemplate,
    viewModel: ConnectionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var composeFile by remember { mutableStateOf("") }
    var title by remember { mutableStateOf(template.Title ?: "") }
    var description by remember { mutableStateOf(template.Description ?: "") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    LaunchedEffect(template.Id) {
        viewModel.getCustomTemplateFileContent(template.Id) { content ->
            if (content != null) {
                composeFile = content
            } else {
                Toast.makeText(context, "Failed to load template file", Toast.LENGTH_SHORT).show()
                composeFile = template.FileContent ?: ""
            }
            isLoading = false
        }
    }

    val saveAction = {
        if (title.isNotBlank() && composeFile.isNotBlank()) {
            isSaving = true
            viewModel.updateCustomTemplate(template.Id, title, description, composeFile) { success, msg ->
                isSaving = false
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (success) onBack()
            }
        } else {
            Toast.makeText(context, "Title and Compose File cannot be empty", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Template: ${template.Title ?: "Unknown"}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { saveAction() },
                        enabled = !isLoading && !isSaving && composeFile.isNotBlank() && title.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save Template")
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
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Compose Editor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = composeFile,
                        onValueChange = { composeFile = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        placeholder = { Text("version: '3'\nservices:\n  ...") }
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { saveAction() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isSaving && composeFile.isNotBlank() && title.isNotBlank()
                    ) {
                        Text(if (isSaving) "Saving..." else "Update Template")
                    }
                }
            }
        }
    }
}

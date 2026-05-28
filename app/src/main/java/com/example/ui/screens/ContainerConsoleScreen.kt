package com.example.ui.screens

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.*
import com.example.ui.viewmodel.ConnectionViewModel
import com.example.ui.viewmodel.NativeSessionState
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerConsoleScreen(
    state: NativeSessionState.Success,
    container: PortainerContainer,
    viewModel: ConnectionViewModel,
    onBack: () -> Unit
) {
    val endpointId = state.selectedEndpoint?.Id ?: 1
    val containerName = container.Names?.firstOrNull()?.removePrefix("/") ?: container.Id.take(12)
    
    var execId by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var initialCommand by remember { mutableStateOf("/bin/sh") }
    var isConnecting by remember { mutableStateOf(false) }
    
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webSocket by remember { mutableStateOf<WebSocket?>(null) }
    val pendingTerminalMessages = remember { mutableListOf<String>() }
    
    val scope = rememberCoroutineScope()

    fun flushTerminalMessages(view: WebView) {
        if (pendingTerminalMessages.isEmpty()) return
        val combined = pendingTerminalMessages.joinToString("")
        pendingTerminalMessages.clear()
        
        val b64 = Base64.encodeToString(combined.toByteArray(), Base64.NO_WRAP)
        view.post {
            view.evaluateJavascript("if (window.decodeAndWrite) window.decodeAndWrite('$b64');", null)
        }
    }

    fun writeToTerminal(text: String) {
        val view = webViewRef
        if (view == null) {
            pendingTerminalMessages.add(text)
        } else {
            flushTerminalMessages(view) // ensure any pending are sent first
            val b64 = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
            view.post {
                view.evaluateJavascript("if (window.decodeAndWrite) window.decodeAndWrite('$b64');", null)
            }
        }
    }

    fun connect() {
        if (isConnecting) return
        isConnecting = true
        errorMsg = null
        scope.launch {
            try {
                val connection = viewModel.selectedConnection.value ?: return@launch
                val api = PortainerApiClient.create(connection.url)
                
                val parts = initialCommand.split("\\s+".toRegex()).filter { it.isNotBlank() }
                // Tty=true creates a clear raw byte stream
                val execReq = CreateExecRequest(Cmd = parts, Tty = true, AttachStdin = true, AttachStdout = true, AttachStderr = true, Env = listOf("TERM=xterm"))
                
                val execResponse = api.createExec(
                    token = state.token,
                    endpointId = endpointId,
                    containerId = container.Id,
                    request = execReq
                )
                
                execId = execResponse.Id
                
                // Now start WebSocket via OkHttp
                val rawToken = state.token.removePrefix("Bearer ").trim()
                val wsUrl = connection.url
                    .replace("http://", "ws://").replace("https://", "wss://")
                    .trimEnd('/') + "/api/websocket/exec?endpointId=${endpointId}&id=${execResponse.Id}&token=${rawToken}"
                
                val request = Request.Builder()
                    .url(wsUrl)
                    .addHeader("Authorization", state.token) // include Bearer
                    .build()
                val client = PortainerApiClient.getUnsafeOkHttpClient()
                
                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        writeToTerminal("\r\n--- Connected to terminal ---\r\n")
                    }
                    override fun onMessage(ws: WebSocket, text: String) {
                        writeToTerminal(text)
                    }
                    override fun onMessage(ws: WebSocket, bytes: ByteString) {
                        writeToTerminal(bytes.utf8())
                    }
                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        writeToTerminal("\r\n--- Connection closed ---\r\n")
                    }
                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        writeToTerminal("\r\n--- WebSocket Error: ${t.localizedMessage} ---\r\n")
                    }
                })
            } catch (e: Exception) {
                errorMsg = e.localizedMessage
            } finally {
                isConnecting = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webSocket?.close(1000, "User Disconnected")
            webSocket = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal: $containerName", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(container.Id.take(12), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { paddingVals ->
        Column(modifier = Modifier.padding(paddingVals).fillMaxSize().padding(16.dp).navigationBarsPadding().imePadding()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = initialCommand,
                    onValueChange = { initialCommand = it },
                    label = { Text("Initial Command (e.g. /bin/sh)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = execId == null && !isConnecting
                )
                Button(
                    onClick = { 
                        if (execId == null) {
                            connect()
                        } else {
                            webSocket?.close(1000, "User Disconnected")
                            webSocket = null
                            execId = null
                        }
                    },
                    enabled = !isConnecting
                ) {
                    Text(if (execId == null) "Connect" else "Disconnect")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
            ) {
                if (errorMsg != null) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Error: $errorMsg", color = MaterialTheme.colorScheme.error)
                    }
                } else if (isConnecting) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (execId != null) {
                    val htmlData = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css" />
                            <script src="https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.js"></script>
                            <script src="https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.js"></script>
                            <style>
                                body, html { margin: 0; padding: 0; height: 100%; background-color: #1E1E1E; overflow: hidden; }
                                #terminal { height: 100%; width: 100%; padding: 8px; box-sizing: border-box; }
                            </style>
                        </head>
                        <body>
                            <div id="error-log" style="color:red; font-size:10px; position:absolute; z-index:9999; top:0; left:0;"></div>
                            <div id="terminal"></div>
                            <script>
                                window.onerror = function(m, u, l) {
                                    document.getElementById('error-log').innerHTML += m + '<br>';
                                };
                                window.terminalReady = false;
                                window.terminalBuffer = [];
                                
                                const term = new Terminal({
                                    theme: { background: '#1E1E1E', foreground: '#FFFFFF' },
                                    fontFamily: 'monospace',
                                    cursorBlink: true
                                });
                                const fitAddon = new FitAddon.FitAddon();
                                term.loadAddon(fitAddon);
                                
                                term.open(document.getElementById('terminal'));
                                setTimeout(() => {
                                    try { fitAddon.fit(); } catch(e) {}
                                    window.terminalReady = true;
                                    window.terminalBuffer.forEach(text => term.write(text));
                                    window.terminalBuffer = [];
                                }, 200);
                                
                                window.addEventListener('resize', () => { 
                                    try { fitAddon.fit(); } catch(e) {} 
                                });
                                
                                document.getElementById('terminal').addEventListener('click', () => {
                                    term.focus();
                                    if (window.AndroidBridge) {
                                        window.AndroidBridge.showKeyboard();
                                    }
                                });
                                
                                term.onData(data => {
                                    if (window.AndroidBridge) {
                                        // Send input to Android OkHttp WebSocket
                                        window.AndroidBridge.sendData(data);
                                    }
                                });
                                
                                window.decodeAndWrite = function(b64) {
                                    const raw = atob(b64);
                                    const bytes = new Uint8Array(raw.length);
                                    for(let i = 0; i < raw.length; i++) {
                                        bytes[i] = raw.charCodeAt(i);
                                    }
                                    const text = new TextDecoder().decode(bytes);
                                    if (window.terminalReady) {
                                        term.write(text);
                                    } else {
                                        window.terminalBuffer.push(text);
                                    }
                                };
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    
                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun showKeyboard() {
                                            post {
                                                requestFocus()
                                                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                                imm.showSoftInput(this@apply, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                            }
                                        }
                                        
                                        @JavascriptInterface
                                        fun sendData(data: String) {
                                            webSocket?.send(data)
                                        }
                                    }, "AndroidBridge")
                                    
                                    webViewClient = WebViewClient()
                                    webChromeClient = WebChromeClient()
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    loadDataWithBaseURL("https://localhost/", htmlData, "text/html", "UTF-8", null)
                                    webViewRef = this
                                }
                            }
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Enter command and tap Connect", color = Color.Gray)
                    }
                }
            }
            
            if (execId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { 
                    webSocket?.send("\u0003") // Ctrl+C
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Force Ctrl+C (Interrupt)")
                }
            }
        }
    }
}


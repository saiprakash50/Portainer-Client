package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortainerWebView(
    url: String,
    title: String,
    username: String = "",
    password: String = "",
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var progress by remember { mutableFloatStateOf(0.0f) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close WebView"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Browser Back"
                        )
                    }
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Browser Forward"
                        )
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            cacheMode = WebSettings.LOAD_DEFAULT
                            
                            // User Agent config to prevent mobile block issues if server requires PC
                            userAgentString = userAgentString.replace("Mobile", "MobileCustom")
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false

                                if (view != null && username.isNotBlank()) {
                                    val escapedUsername = username.replace("\\", "\\\\").replace("'", "\\'")
                                    val escapedPassword = password.replace("\\", "\\\\").replace("'", "\\'")
                                    val js = """
                                        (function() {
                                            var username = '$escapedUsername';
                                            var password = '$escapedPassword';
                                            
                                            var attempts = 0;
                                            var maxAttempts = 30; // Try for 15 seconds
                                            var interval = setInterval(function() {
                                                attempts++;
                                                if (attempts > maxAttempts) {
                                                    clearInterval(interval);
                                                    return;
                                                }
                                                
                                                var userField = document.getElementById('username') || 
                                                                document.querySelector('input[type="text"]') || 
                                                                document.querySelector('input[name="username"]');
                                                                
                                                var passField = document.getElementById('password') || 
                                                                document.querySelector('input[type="password"]') || 
                                                                document.querySelector('input[name="password"]');
                                        
                                                if (userField) {
                                                    userField.value = username;
                                                    var event = new Event('input', { bubbles: true });
                                                    userField.dispatchEvent(event);
                                                    userField.dispatchEvent(new Event('change', { bubbles: true }));
                                                    userField.dispatchEvent(new Event('blur', { bubbles: true }));
                                                }
                                                
                                                if (passField && password) {
                                                    passField.value = password;
                                                    var event = new Event('input', { bubbles: true });
                                                    passField.dispatchEvent(event);
                                                    passField.dispatchEvent(event);
                                                    passField.dispatchEvent(new Event('change', { bubbles: true }));
                                                    passField.dispatchEvent(new Event('blur', { bubbles: true }));
                                                }
                                                
                                                if (userField && passField && password) {
                                                    var loginBtn = document.querySelector('button[type="submit"]') || 
                                                                   document.querySelector('button.btn-primary') ||
                                                                   document.querySelector('button[data-cy="login-submitButton"]') ||
                                                                   Array.from(document.querySelectorAll('button')).find(function(btn) {
                                                                       var txt = btn.innerText.toLowerCase();
                                                                       return txt.includes('log') || txt.includes('sign');
                                                                   });
                                                    
                                                    if (loginBtn) {
                                                        setTimeout(function() {
                                                            loginBtn.click();
                                                        }, 300);
                                                    }
                                                    clearInterval(interval);
                                                } else if (userField && !password) {
                                                    clearInterval(interval);
                                                }
                                            }, 500);
                                        })();
                                    """.trimIndent()
                                    view.evaluateJavascript(js, null)
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false // Let WebView handle it inside
                            }

                            // Support self-signed certificates in local LAN (essential for Home Labs!)
                            @SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                // Real server-side/network security configuration allows self-signed local certs
                                handler?.proceed()
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                progress = newProgress / 100f
                            }
                        }

                        loadUrl(url)
                        webView = this
                    }
                },
                update = {
                    // Update actions can be configured here if URL changes dynamically
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    }
}

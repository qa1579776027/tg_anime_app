package com.cameron.tganime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameron.tganime.R
import com.cameron.tganime.TgAnimeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null) {
    val app = TgAnimeApp.get()
    val current by app.settings.proxyBaseFlow.collectAsStateWithLifecycle("")
    var input by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(current) {
        if (input.isEmpty()) input = current
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_proxy_label), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_proxy_helper),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.settings_proxy_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        testResult = null
                        testing = true
                        scope.launch {
                            val urlStr = input.trim().trimEnd('/')
                            val msg = withContext(Dispatchers.IO) { pingHealth(urlStr) }
                            testResult = msg
                            testing = false
                        }
                    },
                    enabled = !testing && input.isNotBlank(),
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_test_btn))
                }
                Button(
                    onClick = {
                        scope.launch { app.settings.setProxyBase(input.trim()) }
                    },
                    enabled = input.isNotBlank() && input.trim() != current,
                ) {
                    Text(stringResource(R.string.settings_save_btn))
                }
            }

            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_about),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )

            if (current.isNotBlank()) {
                Text(
                    "当前已保存: $current",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Hit `<base>/health`. Returns a human-friendly status string. */
private fun pingHealth(base: String): String {
    if (base.isBlank()) return "请先填写地址"
    return try {
        val url = URL("${base.trimEnd('/')}/health")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
        }
        val code = conn.responseCode
        conn.disconnect()
        if (code == 200) "连接 OK ($code)" else "返回 $code,服务在但路径不对?"
    } catch (t: Throwable) {
        "连接失败: ${t.message ?: t::class.java.simpleName}"
    }
}

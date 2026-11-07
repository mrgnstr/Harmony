@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.mrgnstr.harmony.core.designsystem.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.AuthMethod
import com.mrgnstr.harmony.core.model.QuickConnectChallenge
import com.mrgnstr.harmony.core.model.ServerAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class NavidromeConnectViewModel @Inject constructor(
    val repository: NavidromeRepository,
) : ViewModel()

/**
 * Modal bottom sheet for connecting to a Navidrome / OpenSubsonic server using Password/Token,
 * OpenSubsonic API Key (`nds_…`), or Quick Connect.
 */
@Composable
fun NavidromeConnectSheet(
    onDismiss: () -> Unit,
    onConnected: (ServerAccount) -> Unit = {},
    viewModel: NavidromeConnectViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    var serverName by rememberSaveable { mutableStateOf("") }
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var authMethod by rememberSaveable { mutableStateOf(AuthMethod.Password) }
    var username by rememberSaveable { mutableStateOf("") }
    var secretOrPassword by rememberSaveable { mutableStateOf("") }
    var allowSelfSigned by rememberSaveable { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var quickConnect by remember { mutableStateOf<QuickConnectChallenge?>(null) }

    LaunchedEffect(quickConnect?.secret, authMethod) {
        val qc = quickConnect ?: return@LaunchedEffect
        if (authMethod != AuthMethod.QuickConnect || qc.secret.isBlank()) return@LaunchedEffect
        while (true) {
            delay(3_000L)
            val status = runCatching {
                viewModel.repository.pollQuickConnect(serverUrl, qc.secret, allowSelfSigned)
            }.getOrNull() ?: break
            if (status.authenticated) {
                quickConnect = status
                break
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbols.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Connect to Navidrome", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            Text(
                "Stream your library, sync favourites, playlists, ratings, lyrics and play queues with Navidrome or any OpenSubsonic server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; errorMessage = null },
                label = { Text("Server URL (e.g. https://music.example.com or 192.168.1.50:4533)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )

            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text("Server Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthMethod.entries.forEach { method ->
                    FilterChip(
                        selected = authMethod == method,
                        onClick = { authMethod = method; errorMessage = null },
                        label = { Text(method.label) },
                    )
                }
            }

            when (authMethod) {
                AuthMethod.Password -> {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; errorMessage = null },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value = secretOrPassword,
                        onValueChange = { secretOrPassword = it; errorMessage = null },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
                AuthMethod.ApiKey -> {
                    OutlinedTextField(
                        value = secretOrPassword,
                        onValueChange = { secretOrPassword = it; errorMessage = null },
                        label = { Text("API Key (nds_…)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (optional, resolved via tokenInfo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AuthMethod.QuickConnect -> {
                    val qc = quickConnect
                    if (qc == null) {
                        Button(
                            onClick = {
                                errorMessage = null
                                scope.launch {
                                    runCatching {
                                        viewModel.repository.initiateQuickConnect(serverUrl, Build.MODEL ?: "Android", allowSelfSigned)
                                    }.onSuccess { quickConnect = it }
                                        .onFailure { errorMessage = it.message ?: "Quick Connect unavailable" }
                                }
                            },
                            enabled = serverUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SymbolIcon(Symbols.Key, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate 6-Digit Quick Connect Code")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Enter this code in Navidrome Web UI", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    qc.code.chunked(3).joinToString(" "),
                                    style = MaterialTheme.typography.headlineLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                if (qc.authenticated) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Approved! Enter username below to finish.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(checked = allowSelfSigned, onCheckedChange = { allowSelfSigned = it })
                Text(
                    "Allow self-signed HTTPS certificates (LAN / home-lab)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            errorMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        msg,
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss, enabled = !connecting) { Text("Cancel") }
                Button(
                    onClick = {
                        connecting = true
                        errorMessage = null
                        scope.launch {
                            runCatching {
                                viewModel.repository.loginAndSaveAccount(
                                    name = serverName,
                                    baseUrl = serverUrl,
                                    username = username,
                                    passwordOrKey = secretOrPassword,
                                    authMethod = authMethod,
                                    allowSelfSignedTls = allowSelfSigned,
                                )
                            }.onSuccess { account ->
                                connecting = false
                                onConnected(account)
                                onDismiss()
                            }.onFailure { err ->
                                connecting = false
                                errorMessage = err.message ?: "Could not connect to server"
                            }
                        }
                    },
                    enabled = !connecting && serverUrl.isNotBlank() &&
                        (authMethod == AuthMethod.ApiKey && secretOrPassword.isNotBlank() ||
                            authMethod == AuthMethod.Password && username.isNotBlank() && secretOrPassword.isNotBlank() ||
                            authMethod == AuthMethod.QuickConnect && username.isNotBlank()),
                ) {
                    Text(if (connecting) "Connecting…" else "Connect")
                }
            }
        }
    }
}

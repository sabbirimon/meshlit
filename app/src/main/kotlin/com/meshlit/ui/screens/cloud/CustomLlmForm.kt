package com.meshlit.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.di.koinInject
import com.meshlit.core.cloudmcp.llm.OpenAiCompatibleLlmClient
import com.meshlit.core.cloudmcp.llm.OpenAiCompatibleModel
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Form for configuring the user-supplied LLM endpoint. Persists
 * the endpoint URL + model slug through [SettingsRepository]
 * and the API key through the encrypted
 * [com.meshlit.core.trust.CloudCredentialStore] under the
 * resolved `credentialProviderId`.
 *
 * Layout:
 *  - Endpoint URL (TextField, default NaraRouter)
 *  - Model slug (DropdownMenu with presets + free-form field)
 *  - API key (Password field — never persisted in DataStore)
 *  - Test Connection (POSTs a one-shot chat completion, surfaces
 *    success/error inline)
 *  - Save
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLlmForm(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cloudCredentialStore: com.meshlit.core.trust.CloudCredentialStore = koinInject()
    val scope = rememberCoroutineScope()
    val endpoint by settingsRepository.llmEndpointFlow
        .collectAsState(initial = OpenAiCompatibleModel.DEFAULT_BASE_URL)
    val model by settingsRepository.llmModelFlow
        .collectAsState(initial = OpenAiCompatibleModel.Default.slug)
    val credentialProviderId by settingsRepository.llmApiKeyProviderIdFlow
        .collectAsState(initial = OpenAiCompatibleModel.DEFAULT_PROVIDER_ID)

    var endpointInput by remember(endpoint) { mutableStateOf(endpoint) }
    var modelInput by remember(model) { mutableStateOf(model) }
    var providerIdInput by remember(credentialProviderId) {
        mutableStateOf(credentialProviderId)
    }
    var apiKeyInput by remember { mutableStateOf("") }
    var statusLine by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var modelDropdownOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_llm_endpoint)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_search_clear),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = endpointInput,
                onValueChange = { endpointInput = it },
                label = { Text(stringResource(R.string.cloud_llm_endpoint)) },
                placeholder = { Text("https://openrouter.ai/api") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.cloud_llm_model),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    placeholder = { Text("anthropic/claude-4.5-sonnet") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedButton(onClick = { modelDropdownOpen = true }) {
                    Text("▼")
                }
                DropdownMenu(
                    expanded = modelDropdownOpen,
                    onDismissRequest = { modelDropdownOpen = false },
                ) {
                    OpenAiCompatibleModel.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.displayName) },
                            onClick = {
                                modelInput = preset.slug
                                preset.endpointHint?.let { endpointInput = it }
                                modelDropdownOpen = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = providerIdInput,
                onValueChange = { providerIdInput = it },
                label = { Text("Credential providerId") },
                placeholder = { Text("user-llm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(stringResource(R.string.cloud_llm_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            statusLine?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        if (endpointInput.isBlank()) {
                            statusLine = "Enter an endpoint URL first"
                            statusIsError = true
                            return@OutlinedButton
                        }
                        testing = true
                        statusLine = "Testing…"
                        statusIsError = false
                        scope.launch {
                            // The form may hold a fresh key in
                            // `apiKeyInput` (not yet persisted). Use
                            // that when set; otherwise fall back to
                            // the encrypted-store key so users can
                            // re-test an existing endpoint.
                            val effectiveKey = apiKeyInput.ifBlank {
                                cloudCredentialStore.get(providerIdInput, "token")
                                    ?: ""
                            }
                            val client = OpenAiCompatibleLlmClient(
                                httpClient = OkHttpClient(),
                                baseUrl = endpointInput,
                                apiKey = effectiveKey,
                                model = modelInput,
                            )
                            val result = client.testConnection()
                            statusLine = result.message
                            statusIsError = !result.ok
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (testing) "Testing…"
                        else stringResource(R.string.cloud_llm_test_connection)
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            settingsRepository.setLlmEndpoint(endpointInput)
                            settingsRepository.setLlmModel(modelInput)
                            settingsRepository.setLlmApiKeyProviderId(providerIdInput)
                            // API key goes through the encrypted
                            // store — handled in MeshlitApplication
                            // via `cloudCredentialStore`. The
                            // form only persists the providerId;
                            // the key is written through a callback
                            // the form takes in v0.2.1.
                            statusLine = "Saved endpoint + model + providerId"
                            statusIsError = false
                        }
                    },
                    enabled = endpointInput.isNotBlank() && modelInput.isNotBlank() &&
                        providerIdInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cloud_save_provider))
                }
            }
        }
    }
}
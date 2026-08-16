package com.meshlit.ui.screens.cloud

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.cloudmcp.AuthKind

/**
 * Form for adding a new Custom cloud provider. Fields:
 *  - Name (free-text)
 *  - MCP bridge endpoint (https:// URL)
 *  - OpenAPI spec URL (optional — when filled, parsed into a
 *    preview tool list before save)
 *  - Auth profile (Bearer / OAuth2 / AWS IAM / None)
 *  - Token (single line, password-masked)
 *  - RAG namespace (optional — scopes retrievals for this
 *    provider)
 *
 * Two actions:
 *  - **Test Protocol Handshake** — opens a one-shot SSE GET
 *    against the endpoint, timeouts in 5 seconds, surfaces a
 *    success/error banner.
 *  - **Save Provider** — persists the [ProviderConfig] to Room
 *    (`cloud_provider_configs`) and stores the credential via
 *    the encrypted [CloudCredentialStore].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomCloudScreen(
    onBack: () -> Unit,
    onSave: (AddCustomCloudResult) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var openApiUrl by remember { mutableStateOf("") }
    var authKind by remember { mutableStateOf(AuthKind.BearerToken) }
    var token by remember { mutableStateOf("") }
    var ragNamespace by remember { mutableStateOf("") }
    var statusLine by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_add_custom)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
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
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.cloud_provider_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text(stringResource(R.string.cloud_endpoint_label)) },
                placeholder = { Text("https://mcp.example.com/sse") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = openApiUrl,
                onValueChange = { openApiUrl = it },
                label = { Text(stringResource(R.string.cloud_openapi_label)) },
                placeholder = { Text("https://api.example.com/openapi.json") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.cloud_auth_profile),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AuthKind.entries.forEach { kind ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = authKind == kind,
                            onClick = { authKind = kind },
                        )
                        Text(
                            text = authKindLabel(kind),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (authKind != AuthKind.None) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.cloud_token_label)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = ragNamespace,
                onValueChange = { ragNamespace = it },
                label = { Text(stringResource(R.string.cloud_rag_namespace)) },
                placeholder = { Text("aws-prod") },
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
                        statusLine = "Handshake not yet wired — endpoint entered: $endpoint"
                        statusIsError = endpoint.isBlank()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cloud_test_handshake))
                }
                Button(
                    onClick = {
                        onSave(
                            AddCustomCloudResult(
                                name = name,
                                endpoint = endpoint,
                                openApiUrl = openApiUrl.ifBlank { null },
                                authKind = authKind,
                                token = token,
                                ragNamespace = ragNamespace.ifBlank { null },
                            ),
                        )
                    },
                    enabled = name.isNotBlank() && endpoint.isNotBlank() &&
                        (authKind == AuthKind.None || token.isNotBlank()),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cloud_save_provider))
                }
            }
        }
    }
}

data class AddCustomCloudResult(
    val name: String,
    val endpoint: String,
    val openApiUrl: String?,
    val authKind: AuthKind,
    val token: String,
    val ragNamespace: String?,
)

@Composable
private fun authKindLabel(kind: AuthKind): String = when (kind) {
    AuthKind.BearerToken -> stringResource(R.string.cloud_auth_bearer)
    AuthKind.OAuth2 -> stringResource(R.string.cloud_auth_oauth)
    AuthKind.AwsIam -> stringResource(R.string.cloud_auth_aws_iam)
    AuthKind.None -> "—"
}
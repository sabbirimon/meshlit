package com.meshlit.ui.v2.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSuccess
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.theme.MeshlitWarning

/**
 * Compose card for the **Settings → Models → Hugging Face token** entry.
 * Renders two sub-cards:
 *
 *  1. **Free / personal** — every HF user fills this. One field
 *     (`Personal access token`). Save / Clear buttons. Empty
 *     placeholder shows the help link to the token-settings page.
 *  2. **Pro / Enterprise** — paid / org-scoped. Two fields:
 *     `Organization token` + `Organization slug`. Save / Clear buttons.
 *     Rendered as a separate card so a free user can ignore it; the
 *     card title clarifies "only if you're on a Pro / Enterprise plan".
 *
 * Token values are masked via [PasswordVisualTransformation]. Save
 * clears the field (the actual token is in DataStore, never re-rendered
 * after save) so a screen recording or shoulder-surfer never sees the
 * raw value. The "Token saved" badge uses the *presence* flag, not
 * the value itself.
 */
@Composable
fun HuggingFaceTokenCard(
    modifier: Modifier = Modifier,
    viewModel: HuggingFaceTokenViewModel = viewModel(factory = HuggingFaceTokenViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Personal / free card
        PersonalTokenCard(
            state = state,
            onChange = viewModel::onPersonalTokenChange,
            onSave = viewModel::savePersonal,
            onClear = viewModel::clearPersonal,
        )
        // Pro / Enterprise card
        ProTokenCard(
            state = state,
            onTokenChange = viewModel::onProTokenChange,
            onSlugChange = viewModel::onOrgSlugChange,
            onSave = viewModel::savePro,
            onClear = viewModel::clearPro,
        )
    }
}

@Composable
private fun PersonalTokenCard(
    state: HuggingFaceTokenUiState,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardIcon(
                icon = Icons.Filled.Key,
                tint = MeshlitPulseViolet,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Personal access token",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = "Free · every Hugging Face user",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshlitTextTertiaryV2,
                )
            }
            if (state.hasPersonalToken) {
                SavedBadge("Saved")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create one at huggingface.co → Settings → Access Tokens. " +
                "Meshlit uses it to download gated repos you have access to.",
            style = MaterialTheme.typography.bodySmall,
            color = MeshlitTextSecondaryV2,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.personalToken,
            onValueChange = onChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = if (state.hasPersonalToken) "•••••••• (saved — type to replace)" else "hf_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                    color = MeshlitTextTertiaryV2,
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = hfFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.showSaved) {
                Surface(
                    color = MeshlitSuccess.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MeshlitSuccess,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Token saved — restart a download to apply",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeshlitSuccess,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (state.hasPersonalToken) {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MeshlitWarning)
                }
            }
            TextButton(
                onClick = onSave,
                enabled = state.personalToken.isNotBlank(),
            ) {
                Text("Save", color = if (state.personalToken.isNotBlank()) MeshlitPulseViolet else MeshlitTextTertiaryV2)
            }
        }
    }
}

@Composable
private fun ProTokenCard(
    state: HuggingFaceTokenUiState,
    onTokenChange: (String) -> Unit,
    onSlugChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    val canSave = state.proToken.isNotBlank() && state.orgSlug.isNotBlank()
    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardIcon(
                icon = Icons.Filled.WorkspacePremium,
                tint = MeshlitPulseViolet,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pro / Enterprise org token",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = "Paid · only if you're on a Pro / Enterprise plan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshlitTextTertiaryV2,
                )
            }
            if (state.hasProToken && state.hasOrgSlug) {
                SavedBadge("Pro")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Some Hugging Face repos are gated to specific " +
                "organizations. To download from one, paste the org's " +
                "token and its slug (your-organization-name).",
            style = MaterialTheme.typography.bodySmall,
            color = MeshlitTextSecondaryV2,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.proToken,
            onValueChange = onTokenChange,
            singleLine = true,
            label = { Text("Org token", color = MeshlitTextTertiaryV2) },
            placeholder = {
                Text(
                    text = if (state.hasProToken) "•••••••• (saved — type to replace)" else "hf_org_xxxxxxxxxxxxxxxxxxxx",
                    color = MeshlitTextTertiaryV2,
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = hfFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.orgSlug,
            onValueChange = onSlugChange,
            singleLine = true,
            label = { Text("Organization slug", color = MeshlitTextTertiaryV2) },
            placeholder = {
                Text(
                    text = if (state.hasOrgSlug) "your-org (saved — type to replace)" else "your-org",
                    color = MeshlitTextTertiaryV2,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = hfFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.showProSaved) {
                Surface(
                    color = MeshlitSuccess.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            tint = MeshlitSuccess,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Pro credentials saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeshlitSuccess,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (state.hasProToken || state.hasOrgSlug) {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MeshlitWarning)
                }
            }
            TextButton(onClick = onSave, enabled = canSave) {
                Text(
                    "Save",
                    color = if (canSave) MeshlitPulseViolet else MeshlitTextTertiaryV2,
                )
            }
        }
    }
}

@Composable
private fun CardContainer(content: @Composable () -> Unit) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun CardIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    Surface(
        color = tint.copy(alpha = 0.18f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(36.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SavedBadge(label: String) {
    Surface(
        color = MeshlitSuccess.copy(alpha = 0.18f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MeshlitSuccess,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun hfFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MeshlitPulseViolet,
    unfocusedBorderColor = MeshlitOutlineV2.copy(alpha = 0.6f),
    cursorColor = MeshlitPulseViolet,
    focusedTextColor = MeshlitTextPrimaryV2,
    unfocusedTextColor = MeshlitTextPrimaryV2,
    focusedContainerColor = MeshlitSurfaceHigh,
    unfocusedContainerColor = MeshlitSurface,
)
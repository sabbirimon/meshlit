package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.inference.BundledModelInstaller
import com.meshlit.ui.components.RaListCard
import com.meshlit.ui.components.RaPillChip
import com.meshlit.ui.components.RaPillTone
import com.meshlit.ui.theme.MeshlitAmber

/**
 * Bundled SmolLM2-360M card. Now uses [RaListCard] for the row
 * surface (matches the upstream `BundledModelCard` shape with the
 * "BUNDLED" pill chip + amber "Re-extract" pill + outlined "Delete
 * bundled" button). Tap on the row body is inert — the row's
 * affordances are the action buttons below.
 */
@Composable
internal fun BundledModelCard(
    app: MeshlitApplication,
    onReextract: (String) -> Unit,
    status: String?,
) {
    val context = LocalContext.current
    val installer = remember { BundledModelInstaller() }
    val installed = remember { installer.installedFile(app) }
    val isInstalled = installed != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RaListCard(
            leadingIcon = Icons.Filled.Memory,
            title = stringResource(R.string.models_bundled_name),
            subtitle = stringResource(R.string.models_bundled_quant),
            chips = {
                RaPillChip(
                    text = stringResource(R.string.ra_bundled),
                    tone = RaPillTone.BUNDLED,
                )
                if (isInstalled) {
                    RaPillChip(
                        text = stringResource(R.string.ra_installed),
                        tone = RaPillTone.ACTIVE,
                    )
                }
            },
            // No trailing slot — actions live below the card so the
            // row stays compact and the user can scan status + chips
            // without a busy CTA competing for attention.
            onClick = null,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Amber filled "Re-extract" pill — matches the upstream
            // `Re-extract` CTA shown in the bundled model card.
            Button(
                onClick = {
                    onReextract(context.getString(R.string.models_reextract_in_progress))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshlitAmber,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.ra_re_extract))
            }
            OutlinedButton(
                onClick = { /* Delete-bundled is intentionally inert for
                                the bundled SmolLM2 — the asset lives in
                                the APK and is restored on every
                                re-extract. The button is rendered so
                                the UI surface matches the upstream
                                shape; future work can wire an actual
                                delete-from-files-dir pass. */ },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.ra_delete_bundled))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (installed != null) {
                app.resources.getString(
                    R.string.models_bundled_installed,
                    installed.absolutePath,
                    installed.length(),
                )
            } else {
                stringResource(R.string.models_bundled_not_installed)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
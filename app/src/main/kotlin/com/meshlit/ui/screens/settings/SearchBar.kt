package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.meshlit.R

/**
 * Plain OutlinedTextField-based search bar — not Material 3's
 * `ExperimentalMaterial3Api.SearchBar` (which is its own expanded
 * surface and only one per screen). This version is just a search
 * input with an X button.
 *
 * Lives in its own file so SettingsScreen can wire it without
 * pulling in experimental APIs.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_search_clear),
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = stringResource(R.string.settings_search_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(24.dp),
    )
}
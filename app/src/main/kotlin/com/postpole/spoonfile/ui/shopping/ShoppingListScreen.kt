package com.postpole.spoonfile.ui.shopping

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import com.postpole.spoonfile.BuildConfig
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ads.AdBanner
import com.postpole.spoonfile.data.ShoppingItemEntity
import com.postpole.spoonfile.domain.QuantityScaler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val otherLabel = stringResource(R.string.shopping_other)
    val shareTitle = stringResource(R.string.shopping_share)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_shopping_list)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clearChecked, enabled = state.hasChecked) {
                        Text(stringResource(R.string.shopping_clear_checked))
                    }
                    IconButton(
                        enabled = !state.isEmpty,
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.nav_shopping_list))
                                putExtra(Intent.EXTRA_TEXT, shareText(state.groups, otherLabel))
                            }
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = shareTitle)
                    }
                },
            )
        },
        bottomBar = {
            Column {
                // Banner above the add-item field, hidden while the keyboard is open so it never rides up
                // over what the user is typing. The add bar below owns the navigation-bar inset.
                if (WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                    AdBanner(BuildConfig.AD_UNIT_BANNER_SHOPPING)
                }
                AddItemBar(onAdd = viewModel::addItem)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.loaded -> Unit
                state.isEmpty -> Text(
                    stringResource(R.string.shopping_empty),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "h${group.recipeId}") {
                            Text(
                                group.title ?: otherLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(group.items, key = { "i${it.id}" }) { item ->
                            ShoppingItemRow(item,onChecked = { viewModel.setChecked(item.id, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingItemEntity, onChecked: (Boolean) -> Unit) {
    val (quantity, name) = remember(item.text) { QuantityScaler.split(item.text) }
    val decoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
    val color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = onChecked)
        // Quantity column, then the item. Text always wraps in full: no truncation.
        Text(
            quantity,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = decoration,
            color = color,
            modifier = Modifier.width(64.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = decoration,
            color = color,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
    }
}

/** Docked at the bottom: a 56 dp pill. New items go to "Other". */
@Composable
private fun AddItemBar(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.shopping_add_item)) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_add_item_cd))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        }
    }
}

package com.postpole.spoonfile.ui.shopping

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R
import com.postpole.spoonfile.data.ShoppingItemEntity
import com.postpole.spoonfile.domain.QuantityScaler
import com.postpole.spoonfile.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val otherLabel = stringResource(R.string.shopping_other)
    val shareTitle = stringResource(R.string.detail_share)

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
                    if (state.hasChecked) {
                        TextButton(onClick = viewModel::clearChecked) { Text(stringResource(R.string.shopping_clear_checked)) }
                    }
                    if (!state.isEmpty) {
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.nav_shopping_list))
                                putExtra(Intent.EXTRA_TEXT, ShoppingListViewModel.shareText(state.groups, otherLabel))
                            }
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.detail_share))
                        }
                    }
                },
            )
        },
        bottomBar = { AddItemBar(onAdd = viewModel::addItem) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Unit
                state.isEmpty -> Text(
                    stringResource(R.string.shopping_empty),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.groups.forEach { group ->
                        item(key = "g${group.recipeId}") {
                            Text(
                                group.title ?: otherLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(group.items, key = { "i${it.id}" }) { item ->
                            ShoppingRow(item, onToggle = { viewModel.toggle(item.id, it) })
                        }
                    }
                }
            }
        }
    }
}

/** Checked items stay in place, struck through, until "Clear checked". Full text, wrapped: never truncated. */
@Composable
private fun ShoppingRow(item: ShoppingItemEntity, onToggle: (Boolean) -> Unit) {
    val (quantity, name) = QuantityScaler.splitQuantity(item.text)
    val decoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
    val color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onToggle)
        Text(
            quantity,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = decoration,
            color = color,
            modifier = Modifier.width(88.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = decoration,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Docked "Add an item" field: a 56 dp pill at the bottom. New items go to "Other". */
@Composable
private fun AddItemBar(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
            singleLine = true,
            shape = PillShape,
            trailingIcon = {
                IconButton(onClick = submit, enabled = text.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_add_cd))
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
    }
}

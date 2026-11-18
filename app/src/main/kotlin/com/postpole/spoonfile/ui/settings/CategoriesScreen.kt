package com.postpole.spoonfile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.R

/** Categories manager (Settings > Recipes): create, rename, reorder and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    // 0 = no dialog, -1 = create, otherwise the id being renamed.
    var editing by rememberSaveable { mutableStateOf(0L) }
    var deleting by rememberSaveable { mutableStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = -1L }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.categories_add))
            }
        },
    ) { padding ->
        if (categories.isEmpty()) {
            Text(
                stringResource(R.string.categories_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                itemsIndexed(categories, key = { _, c -> c.category.id }) { index, c ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.category.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                androidx.compose.ui.res.pluralStringResource(R.plurals.categories_recipe_count, c.recipeCount, c.recipeCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.move(c.category.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.categories_move_up))
                        }
                        IconButton(onClick = { viewModel.move(c.category.id, 1) }, enabled = index < categories.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.categories_move_down))
                        }
                        IconButton(onClick = { editing = c.category.id }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.categories_rename))
                        }
                        IconButton(onClick = { deleting = c.category.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    if (editing != 0L) {
        val current = categories.firstOrNull { it.category.id == editing }
        NameDialog(
            title = stringResource(if (editing == -1L) R.string.categories_add else R.string.categories_rename),
            initial = current?.category?.name.orEmpty(),
            onDismiss = { editing = 0L },
            onConfirm = { name, fail ->
                val done = { ok: Boolean -> if (ok) editing = 0L else fail() }
                if (editing == -1L) viewModel.create(name, done) else viewModel.rename(editing, name, done)
            },
        )
    }

    categories.firstOrNull { it.category.id == deleting }?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = 0L },
            title = { Text(stringResource(R.string.categories_delete_title, c.category.name)) },
            text = { Text(stringResource(R.string.categories_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(c.category.id)
                    deleting = 0L
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = 0L }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String, () -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = false
                },
                singleLine = true,
                isError = error,
                label = { Text(stringResource(R.string.categories_name)) },
                supportingText = if (error) {
                    { Text(stringResource(R.string.categories_name_taken)) }
                } else {
                    null
                },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) { error = true } }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

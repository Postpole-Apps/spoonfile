package com.postpole.spoonfile.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.navigationBarsPadding
import com.postpole.spoonfile.BuildConfig
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ads.AdBanner

/** `nameId` below: null = dialog closed, 0 = new category, otherwise renaming that id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val nameTaken by viewModel.nameTaken.collectAsStateWithLifecycle()
    var nameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var nameText by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        bottomBar = {
            AdBanner(BuildConfig.AD_UNIT_BANNER_CATEGORIES, contentModifier = Modifier.navigationBarsPadding())
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.clearNameError()
                        nameText = ""
                        nameId = 0L
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.edit_new_category))
                    }
                },
            )
        },
    ) { padding ->
        if (categories.isEmpty()) {
            Text(
                stringResource(R.string.categories_manage_empty),
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                itemsIndexed(categories, key = { _, c -> c.category.id }) { index, c ->
                    ListItem(
                        headlineContent = { Text(c.category.name) },
                        supportingContent = { Text(stringResource(R.string.categories_recipe_count, c.recipeCount)) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.categories_move_up))
                                }
                                IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < categories.lastIndex) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.categories_move_down))
                                }
                                IconButton(onClick = {
                                    viewModel.clearNameError()
                                    nameText = c.category.name
                                    nameId = c.category.id
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.categories_rename))
                                }
                                IconButton(onClick = { deleteId = c.category.id }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    nameId?.let { id ->
        val close = { nameId = null }
        AlertDialog(
            onDismissRequest = close,
            title = {
                Text(stringResource(if (id == 0L) R.string.edit_new_category else R.string.categories_rename))
            },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        viewModel.clearNameError()
                    },
                    singleLine = true,
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text(stringResource(R.string.categories_name_taken)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameText.isNotBlank(),
                    onClick = {
                        val done: (Boolean) -> Unit = { saved -> if (saved) close() }
                        if (id == 0L) viewModel.create(nameText, done) else viewModel.rename(id, nameText, done)
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleteId?.let { id ->
        val name = remember(id) { categories.firstOrNull { it.category.id == id }?.category?.name.orEmpty() }
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.categories_delete_title, name)) },
            text = { Text(stringResource(R.string.categories_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id)
                    deleteId = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

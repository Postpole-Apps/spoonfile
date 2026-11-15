package com.postpole.spoonfile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.data.ThemeMode
import com.postpole.spoonfile.importer.web.SharedTextUrl
import com.postpole.spoonfile.ui.AppNavHost
import com.postpole.spoonfile.ui.MainViewModel
import com.postpole.spoonfile.ui.theme.SpoonfileTheme
import com.postpole.spoonfile.update.InAppUpdate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var analytics: Analytics

    private lateinit var update: InAppUpdate

    /** A link from the Share sheet, waiting to be routed to the import screen. */
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        update = InAppUpdate(this, onError = analytics::recordNonFatal)
        if (savedInstanceState == null) {
            update.check()
            handleShareIntent(intent)
        }
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val updateReady by update.readyToInstall.collectAsStateWithLifecycle()
            var dismissed by rememberSaveable { mutableStateOf(false) }
            val dark = when (theme.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SpoonfileTheme(darkTheme = dark) {
                AppNavHost(
                    onScreen = viewModel::onScreen,
                    sharedText = sharedText,
                    onSharedTextHandled = { sharedText = null },
                )
                if (updateReady && !dismissed) {
                    AlertDialog(
                        onDismissRequest = { dismissed = true },
                        title = { Text(stringResource(R.string.update_ready_title)) },
                        text = { Text(stringResource(R.string.update_ready_message)) },
                        confirmButton = {
                            TextButton(onClick = update::completeUpdate) { Text(stringResource(R.string.update_restart)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.update_later)) }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * Shared text is untrusted: capped, and only an http(s) link inside it is used (never a WebView,
     * never another scheme). Text with no link still opens the import screen so the user can paste one.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = try {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        } catch (_: RuntimeException) {
            null
        }
        sharedText = SharedTextUrl.extract(text).orEmpty()
    }

    override fun onDestroy() {
        update.release()
        super.onDestroy()
    }
}

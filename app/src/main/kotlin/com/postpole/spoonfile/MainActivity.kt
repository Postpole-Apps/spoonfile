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
import com.postpole.spoonfile.ui.SharedLink
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

    /** The newest web link shared into the app, if any; routes to the importer. */
    private var sharedLink by mutableStateOf<SharedLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: after recreation the intent was already handled.
        if (savedInstanceState == null) handleShare(intent)
        update = InAppUpdate(this, onError = analytics::recordNonFatal)
        if (savedInstanceState == null) update.check()
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
                AppNavHost(onScreen = viewModel::onScreen, sharedLink = sharedLink)
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
        handleShare(intent)
    }

    /** Shared text is untrusted: take only the first http(s) URL; it is never handed to a WebView. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/plain") != true) return
        val url = SharedTextUrl.extract(intent.getStringExtra(Intent.EXTRA_TEXT)) ?: return
        sharedLink = SharedLink(url)
    }

    override fun onDestroy() {
        update.release()
        super.onDestroy()
    }
}

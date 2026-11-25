package com.postpole.spoonfile.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/**
 * Shows [message] after dismissing any snackbar currently on screen, so repeated taps of the same
 * action replace the visible message instead of queueing one snackbar per tap.
 */
suspend fun SnackbarHostState.showLatestSnackbar(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(message, actionLabel, withDismissAction, duration)
}

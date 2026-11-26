package com.postpole.spoonfile.ui.recipe

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postpole.spoonfile.R
import kotlinx.coroutines.delay

private val PlayIcon: ImageVector = materialIcon("Play") {
    materialPath { moveTo(8f, 5f); verticalLineTo(19f); lineTo(19f, 12f); close() }
}

private val PauseIcon: ImageVector = materialIcon("Pause") {
    materialPath {
        moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); close()
        moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); close()
    }
}

private val ResetIcon: ImageVector = materialIcon("Reset") {
    materialPath {
        moveTo(12f, 5f); verticalLineTo(1f); lineTo(7f, 6f); lineToRelative(5f, 5f); verticalLineTo(7f)
        curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f); reflectiveCurveToRelative(-2.69f, 6f, -6f, 6f)
        reflectiveCurveToRelative(-6f, -2.69f, -6f, -6f); horizontalLineTo(4f)
        curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f); reflectiveCurveToRelative(8f, -3.58f, 8f, -8f)
        reflectiveCurveToRelative(-3.58f, -8f, -8f, -8f); close()
    }
}

private val TimerDigits = TextStyle(
    fontSize = 32.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)

/**
 * The cook-mode timer area above the Back / Ingredients / Next row: a start pill while idle, the
 * running / paused / finished bar otherwise. Drives the countdown and the end-of-timer alert.
 *
 * The alert is in-app only. Per #57 there is no notification permission, foreground service or
 * exact alarm, so while the screen is off or the app is in the background the countdown tick and
 * the chime/haptic can be delayed until the app is back in front. The running timer is anchored to
 * an `elapsedRealtime()` deadline, so it shows the correct (or finished) state as soon as the user
 * returns, even after the process was killed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CookTimerSection(
    timer: CookTimerState,
    onTimerChange: (CookTimerState) -> Unit,
    suggestedSeconds: Int?,
    content: Color,
) {
    val current by rememberUpdatedState(timer)
    val change by rememberUpdatedState(onTimerChange)
    val running = timer.status == TimerStatus.RUNNING

    // Deadline-based ticker. The first tick is immediate so a restored timer applies time spent away.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            change(current.tick(SystemClock.elapsedRealtime()))
            if (current.status != TimerStatus.RUNNING) break
            delay(200)
        }
    }

    val finished = timer.status == TimerStatus.FINISHED
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // Alert only when the timer becomes FINISHED and has not alerted yet. The flag is set before playing and
    // is saved with the state, so rotation or process restore while "Time's up" shows stays silent.
    LaunchedEffect(timer.alertDue) {
        if (timer.alertDue) {
            change(current.markAlerted())
            runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
            playChime(context)
        }
    }

    val pill = content.copy(alpha = 0.14f)
    when (timer.status) {
        TimerStatus.IDLE -> {
            val seconds = suggestedSeconds ?: CookTimerState.QUICK_SECONDS
            val label = if (suggestedSeconds != null) {
                stringResource(R.string.cook_timer_start, formatTimer(seconds * 1000L))
            } else {
                stringResource(R.string.cook_timer_quick)
            }
            Surface(
                onClick = { onTimerChange(timer.start(seconds, SystemClock.elapsedRealtime())) },
                shape = CircleShape,
                color = pill,
                contentColor = content,
                modifier = Modifier.padding(top = 8.dp).minimumInteractiveComponentSize(),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        else -> {
            val remainingCd = stringResource(R.string.cook_timer_remaining_cd, formatTimer(timer.remainingMillis))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = pill,
                contentColor = content,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.Center,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (finished) {
                        Text(
                            stringResource(R.string.cook_timer_done),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.heightIn(min = 48.dp).padding(end = 8.dp),
                        )
                    } else {
                        Text(
                            formatTimer(timer.remainingMillis),
                            style = TimerDigits,
                            modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = remainingCd },
                        )
                        val toggleCd = stringResource(
                            if (running) R.string.cook_timer_pause else R.string.cook_timer_resume,
                        )
                        IconButton(onClick = { onTimerChange(SystemClock.elapsedRealtime().let { if (running) timer.pause(it) else timer.resume(it) })
                        }) {
                            Icon(if (running) PauseIcon else PlayIcon, contentDescription = toggleCd)
                        }
                    }
                    val addCd = stringResource(R.string.cook_timer_add_minute_cd)
                    TextButton(
                        onClick = { onTimerChange(timer.addMinute(SystemClock.elapsedRealtime())) },
                        modifier = Modifier.minimumInteractiveComponentSize().semantics { contentDescription = addCd },
                    ) { Text(stringResource(R.string.cook_timer_add_minute), color = content) }
                    if (finished) {
                        TextButton(
                            onClick = { onTimerChange(timer.reset()) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) { Text(stringResource(R.string.cook_timer_dismiss), color = content) }
                    } else {
                        IconButton(onClick = { onTimerChange(timer.reset()) }) {
                            Icon(ResetIcon, contentDescription = stringResource(R.string.cook_timer_reset))
                        }
                    }
                }
            }
        }
    }
}

/** A short default notification sound. Never throws: emulators and silent mode just stay quiet. */
private fun playChime(context: Context) {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
    }
}

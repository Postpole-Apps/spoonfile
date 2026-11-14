package com.postpole.spoonfile

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.billing.BillingRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SpoonfileApp : Application() {
    @Inject lateinit var billing: BillingRepository
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var dataStore: DataStore<Preferences>

    override fun onCreate() {
        super.onCreate()
        billing.start()
        logLaunch()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = billing.onForeground()
            },
        )
    }

    private fun logLaunch() {
        analytics.logEvent(AnalyticsEvents.APP_OPEN)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            var first = false
            dataStore.edit {
                if (it[LAUNCHED_BEFORE] != true) {
                    first = true
                    it[LAUNCHED_BEFORE] = true
                }
            }
            if (first) analytics.logEvent(AnalyticsEvents.FIRST_LAUNCH)
        }
    }

    private companion object {
        val LAUNCHED_BEFORE = booleanPreferencesKey("launched_before")
    }
}

/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hlcyn.updater

import android.app.Application
import com.android.settingslib.spa.framework.common.SettingsPageProviderRepository
import com.android.settingslib.spa.framework.common.SpaEnvironment
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.hlcyn.updater.data.AppStateRepository
import org.hlcyn.updater.data.UpdatesRepository
import org.hlcyn.updater.data.UserPreferencesRepository
import org.hlcyn.updater.data.source.local.UpdatesDatabase
import org.hlcyn.updater.data.source.local.UpdatesLocalDataSource
import org.hlcyn.updater.data.source.network.UpdatesNetworkDataSource
import org.hlcyn.updater.notifications.NotificationHelper
import org.hlcyn.updater.util.BatteryMonitor
import org.hlcyn.updater.util.NetworkMonitor
import org.hlcyn.updater.util.SystemUpdateStatusHelper

class UpdaterApplication : Application() {
    private val coroutineScope = MainScope()
    val database by lazy { UpdatesDatabase.getInstance(applicationContext) }
    val networkDataSource by lazy { UpdatesNetworkDataSource(applicationContext) }
    val localDataSource by lazy { UpdatesLocalDataSource(database.updateDao()) }


    val batteryMonitor by lazy {
        BatteryMonitor(applicationContext, coroutineScope, userPreferencesRepository)
    }
    val networkMonitor by lazy { NetworkMonitor(applicationContext, coroutineScope) }
    val notificationHelper by lazy { NotificationHelper(applicationContext) }
    val appStateRepository by lazy { AppStateRepository(applicationContext) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(applicationContext) }
    val updatesRepository by lazy {
        UpdatesRepository(
            networkMonitor = networkMonitor,
            notificationHelper = notificationHelper,
            networkDataSource = networkDataSource,
            localDataSource = localDataSource,
        )
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.setUpNotificationChannels()
        SpaEnvironmentFactory.reset(object : SpaEnvironment(applicationContext) {
            override val pageProviderRepository = lazy {
                SettingsPageProviderRepository(emptyList())
            }

            override val isSpaExpressiveEnabled = true
        })

        coroutineScope.launch {
            combine(
                updatesRepository.observeLocalUpdates(),
                appStateRepository.lastCheckedTimestampFlow,
            ) { updates, lastChecked ->
                val hasUpdate = updates.isNotEmpty()
                val version = updates.firstOrNull()?.version
                SystemUpdateStatusHelper.updateStatus(
                    context = applicationContext,
                    hasUpdate = hasUpdate,
                    updateVersion = version,
                    lastCheckedTimestamp = lastChecked,
                )
            }.collect()
        }
    }
}

/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hlcyn.updater

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hlcyn.updater.data.Update
import org.hlcyn.updater.data.source.network.ChangelogResponse
import org.hlcyn.updater.deviceinfo.DeviceInfoUtils
import org.hlcyn.updater.updatescheck.UpdatesCheckModel
import org.hlcyn.updater.updatescheck.UpdatesCheckState

private const val TAG = "UpdatesViewModel"

sealed interface ChangelogUiState {
    data object Initial : ChangelogUiState
    data object Loading : ChangelogUiState
    data class Success(val changelog: ChangelogResponse) : ChangelogUiState
    data class Error(val message: String) : ChangelogUiState
}

class UpdatesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    data class UiState(
        val updates: List<Update> = emptyList(),
        val isCheckingForUpdates: Boolean = false,
        val isOnline: Boolean = true,
        val lastCheckedTimestamp: Long = 0L,
        val hasUpdateCheckFailed: Boolean = false,
    ) {
        val updatesCheckModel = UpdatesCheckModel(
            state = when {
                isCheckingForUpdates -> UpdatesCheckState.Checking
                !isOnline -> UpdatesCheckState.NoInternet
                hasUpdateCheckFailed -> UpdatesCheckState.Error
                else -> UpdatesCheckState.Idle
            },
            lastCheckedTimestamp = lastCheckedTimestamp,
            canCheckForUpdates = isOnline && !isCheckingForUpdates,
        )
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val uiStateLive: LiveData<UiState> = _uiState.asLiveData()

    private val updaterApplication = getApplication<UpdaterApplication>()
    private val repository = updaterApplication.updatesRepository
    private val appStateRepository = updaterApplication.appStateRepository
    private val networkMonitor = updaterApplication.networkMonitor

    private val _changelogUiState = MutableStateFlow<ChangelogUiState>(ChangelogUiState.Initial)
    val changelogUiState: StateFlow<ChangelogUiState> = _changelogUiState.asStateFlow()

    init {
        viewModelScope.launch {
            appStateRepository.lastCheckedTimestampFlow.collect { ts ->
                _uiState.update { it.copy(lastCheckedTimestamp = ts) }
            }
        }

        viewModelScope.launch {
            repository.observeLocalUpdates().collect { updates ->
                _uiState.update { it.copy(updates = updates) }
            }
        }

        viewModelScope.launch {
            networkMonitor.networkState
                .distinctUntilChangedBy { it.isOnline }
                .onEach { networkState ->
                    _uiState.update { it.copy(isOnline = networkState.isOnline) }
                }
                .filter { it.isOnline }
                .collect { fetchUpdates() }
        }
    }

    fun fetchUpdates() {
        if (_uiState.value.isCheckingForUpdates) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    hasUpdateCheckFailed = false,
                )
            }
            try {
                val fetchedAt = repository.fetchUpdates()
                fetchedAt?.let { fetchedAt ->
                    appStateRepository.setLastCheckedTimestamp(fetchedAt)
                }
                _uiState.update { it.copy(isCheckingForUpdates = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch updates", e)
                _uiState.update {
                    it.copy(
                        isCheckingForUpdates = false,
                        hasUpdateCheckFailed = true,
                    )
                }
            }
        }
    }

    fun loadChangelog(update: Update?) {
        val device = DeviceInfoUtils.device
        val rawVersion = update?.version?.ifEmpty { DeviceInfoUtils.halcyonVersion } ?: DeviceInfoUtils.halcyonVersion
        val version = rawVersion.replace(Regex("(?i)halcyon\\s*"), "").trim().ifEmpty { rawVersion }
        val candidate = listOfNotNull(update?.name, update?.downloadUrl).firstOrNull { it.isNotEmpty() }
        val buildDate = DeviceInfoUtils.resolveBuildDate(candidate)

        viewModelScope.launch {
            _changelogUiState.value = ChangelogUiState.Loading
            val result = withContext(Dispatchers.IO) {
                updaterApplication.networkDataSource.fetchChangelog(device, version, buildDate)
            }
            if (result != null) {
                _changelogUiState.value = ChangelogUiState.Success(result)
            } else {
                _changelogUiState.value = ChangelogUiState.Error("No changelog available for this build.")
            }
        }
    }

    fun dismissChangelog() {
        _changelogUiState.value = ChangelogUiState.Initial
    }
}

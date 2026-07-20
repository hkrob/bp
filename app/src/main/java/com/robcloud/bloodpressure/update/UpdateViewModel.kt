package com.robcloud.bloodpressure.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robcloud.bloodpressure.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(val progress: Int) : UpdateUiState
    data class ReadyToInstall(val file: File, val versionName: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun check() {
        if (!UpdateConfig.isConfigured) {
            _state.value = UpdateUiState.Error("Update source isn't configured in this build")
            return
        }
        _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            try {
                val latest = UpdateManager.checkLatest()
                _state.value = when {
                    latest == null ->
                        UpdateUiState.Error("Couldn't reach GitHub, or the latest release has no APK")
                    UpdateManager.isNewer(latest.versionName, BuildConfig.VERSION_NAME) ->
                        UpdateUiState.Available(latest)
                    else -> UpdateUiState.UpToDate
                }
            } catch (e: Exception) {
                _state.value = UpdateUiState.Error(e.message ?: "Update check failed")
            }
        }
    }

    fun download(release: ReleaseInfo) {
        _state.value = UpdateUiState.Downloading(0)
        viewModelScope.launch {
            try {
                val file = UpdateManager.download(getApplication(), release) { progress ->
                    _state.value = UpdateUiState.Downloading(progress)
                }
                _state.value = UpdateUiState.ReadyToInstall(file, release.versionName)
            } catch (e: Exception) {
                _state.value = UpdateUiState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun reset() {
        _state.value = UpdateUiState.Idle
    }
}

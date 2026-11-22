package com.learnlock.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.learnlock.app.data.ChildProfileEntity
import com.learnlock.app.data.LearnLockRepository
import com.learnlock.app.data.SessionState
import com.learnlock.app.data.TaskAttemptEntity
import com.learnlock.app.data.ParentPinVerification
import com.learnlock.app.lock.LockController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Состояние главного потока: профиль, сессия, статистика, флаг PIN. */
data class MainUiState(
    val profile: ChildProfileEntity = ChildProfileEntity(),
    val session: SessionState = SessionState(),
    val hasParentPin: Boolean = false,
    val attempts: List<TaskAttemptEntity> = emptyList(),
    val bootstrapped: Boolean = false
)

/**
 * ViewModel главного Activity: онбординг, настройки, вкл/выкл мониторинга,
 * preview задачи и завершение сессии.
 */
class MainViewModel(
    application: Application,
    private val repository: LearnLockRepository
) : AndroidViewModel(application) {
    var uiState by mutableStateOf(MainUiState())
        private set

    /** Инициализация дефолтов и подписка на потоки профиля/сессии/статистики. */
    fun bootstrap() {
        viewModelScope.launch {
            repository.ensureDefaults()
            repository.refreshNextLaunchIfExpired()
            LockController.scheduleNextLaunch(getApplication())
            uiState = uiState.copy(hasParentPin = repository.hasParentPin(), bootstrapped = true)
            launch {
                repository.observeProfile().collectLatest { profile ->
                    if (profile != null) uiState = uiState.copy(profile = profile)
                }
            }
            launch {
                repository.observeSession().collectLatest { session ->
                    uiState = uiState.copy(session = session)
                }
            }
            launch {
                repository.observeStats(7).collectLatest { attempts ->
                    uiState = uiState.copy(attempts = attempts)
                }
            }
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            repository.setParentPin(pin)
            planNextLaunch()
            uiState = uiState.copy(hasParentPin = true)
        }
    }


    fun verifySettingsPin(pin: String, onResult: (ParentPinVerification) -> Unit) {
        viewModelScope.launch {
            onResult(repository.verifyParentPin(pin))
        }
    }

    fun clearParentPin() {
        viewModelScope.launch {
            repository.clearParentPin()
            uiState = uiState.copy(hasParentPin = false)
        }
    }

    fun refreshNextLaunchIfExpired() {
        viewModelScope.launch { repository.refreshNextLaunchIfExpired() }
    }

    /** Сохраняет профиль и синхронизирует foreground-мониторинг с [ChildProfileEntity.lockEnabled]. */
    fun saveProfile(profile: ChildProfileEntity) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            planNextLaunch(profile)
            if (profile.lockEnabled) {
                LockController.startMonitoring(getApplication())
            } else {
                LockController.stopMonitoring(getApplication())
            }
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        saveProfile(uiState.profile.copy(lockEnabled = enabled))
    }

    /** Открывает LockActivity в режиме проверки UI. */
    fun previewLock(unlimitedMode: Boolean = false) {
        LockController.presentLock(getApplication(), forcePreview = true, unlimitedMode = unlimitedMode)
    }

    fun endSession() {
        viewModelScope.launch { repository.endSessionNow() }
    }

    private suspend fun planNextLaunch(profile: ChildProfileEntity? = null) {
        repository.scheduleNextLaunch(profile)
        LockController.scheduleNextLaunch(getApplication())
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val repository: LearnLockRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

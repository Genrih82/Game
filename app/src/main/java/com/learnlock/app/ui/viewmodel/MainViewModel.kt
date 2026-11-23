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
    val bootstrapped: Boolean = false,
    /** true после чтения строки профиля из Room, до этого сохранять профиль нельзя. */
    val profileLoaded: Boolean = false
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

    private var tasksOpened = false

    /** Инициализация дефолтов и подписка на потоки профиля/сессии/статистики. */
    fun bootstrap() {
        viewModelScope.launch {
            repository.ensureDefaults()
            val profile = repository.getProfileOrDefault()
            uiState = uiState.copy(
                profile = profile,
                profileLoaded = true,
                hasParentPin = repository.hasParentPin(),
                bootstrapped = true
            )
            LockController.scheduleNextLaunch(getApplication())
            launch {
                repository.observeProfile().collectLatest { loaded ->
                    if (loaded != null) uiState = uiState.copy(profile = loaded, profileLoaded = true)
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
            // Пустой PIN означает явное отключение родительской защиты.
            if (pin.isBlank()) {
                repository.clearParentPin()
                uiState = uiState.copy(hasParentPin = false)
            } else {
                repository.setParentPin(pin)
                uiState = uiState.copy(hasParentPin = true)
            }
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

    /**
     * Сохраняет профиль и синхронизирует мониторинг с [ChildProfileEntity.lockEnabled].
     * Срок переписывается только если он ещё не задан или изменился интервал.
     * Выключение блокировки alarm снимает и новый срок не ставит.
     */
    fun saveProfile(profile: ChildProfileEntity) {
        if (!uiState.profileLoaded) return
        val before = uiState.profile
        viewModelScope.launch {
            val stored = repository.saveProfile(profile)
            if (!stored.lockEnabled) {
                LockController.cancelScheduledLaunch(getApplication())
                LockController.stopMonitoring(getApplication())
                return@launch
            }
            repository.scheduleNextLaunchAfterProfileEdit(before, stored)
            LockController.startMonitoring(getApplication())
            LockController.scheduleNextLaunch(getApplication())
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        if (!uiState.profileLoaded) return
        saveProfile(uiState.profile.copy(lockEnabled = enabled))
    }

    /**
     * После настройки открывает окно заданий.
     * Если срок блокировки уже наступил — с лимитом из настроек, иначе свободный запуск с ярлыка.
     */
    fun suppressAutoTasks() {
        tasksOpened = true
    }

    fun openTasksIfReady() {
        if (tasksOpened) return
        tasksOpened = true
        viewModelScope.launch {
            val scheduled = repository.shouldPresentLock()
            LockController.presentLock(getApplication(), unlimited = !scheduled)
        }
    }

    /** Открывает свободный экран заданий без лимита. */
    fun previewLock() {
        LockController.presentLock(getApplication(), unlimited = true)
    }

    fun endSession() {
        viewModelScope.launch { repository.endSessionNow() }
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

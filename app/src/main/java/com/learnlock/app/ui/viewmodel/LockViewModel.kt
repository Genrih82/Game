package com.learnlock.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.learnlock.app.data.ChildProfileEntity
import com.learnlock.app.data.LearnLockRepository
import com.learnlock.app.lock.LockController
import com.learnlock.taskengine.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** Состояние экрана задачи ([com.learnlock.app.ui.screens.LockTaskScreen]). */
data class LockUiState(
    val loading: Boolean = true,
    val profile: ChildProfileEntity = ChildProfileEntity(),
    val task: Task? = null,
    val feedback: String? = null,
    val hintVisible: Boolean = false,
    val hintUsed: Boolean = false,
    /** Пауза после ошибки, сек. */
    val cooldownSeconds: Int = 0,
    val showParentPin: Boolean = false,
    val unlocked: Boolean = false,
    val tasksSolved: Int = 0,
    val tasksRequired: Int = 1,
    /** Запуск с ярлыка: без лимита и без закрытия после серии. */
    val unlimited: Boolean = false,
    val sessionCorrect: Int = 0,
    val sessionWrong: Int = 0
)

/**
 * ViewModel экрана блокировки: загрузка задачи, проверка ответа,
 * подсказка и parent unlock.
 */
class LockViewModel(
    private val application: Application,
    private val repository: LearnLockRepository
) : ViewModel() {
    var uiState by mutableStateOf(LockUiState())
        private set

    private var answerInFlight = false

    /**
     * Загружает задачу.
     * @param unlimited запуск с ярлыка: лимит заданий не действует и телефон сам не разблокируется
     */
    fun load(unlimited: Boolean = false) {
        viewModelScope.launch {
            val profile = repository.getProfileOrDefault()
            val session = repository.observeSession().first()
            if (!unlimited && repository.isSessionActive()) {
                uiState = uiState.copy(loading = false, unlocked = true, profile = profile, unlimited = false)
                return@launch
            }
            val task = repository.nextTask()
            uiState = uiState.copy(
                loading = false,
                unlocked = false,
                unlimited = unlimited,
                profile = profile,
                tasksSolved = if (unlimited) 0 else session.tasksSolved,
                tasksRequired = profile.tasksToUnlock.coerceAtLeast(1),
                sessionCorrect = 0,
                sessionWrong = 0,
                task = task,
                feedback = null
            )
        }
    }

    /** Показывает подсказку один раз за задачу (если разрешено в профиле). */
    fun useHint() {
        val task = uiState.task ?: return
        if (!uiState.profile.hintsEnabled || uiState.hintUsed) return
        uiState = uiState.copy(
            hintUsed = true,
            hintVisible = true,
            feedback = if (task.hint == null) "Подумай ещё" else null
        )
    }

    /**
     * Проверяет ответ. Пока первая проверка не записана, повторный вызов игнорируется.
     * При наборе tasksToUnlock сначала пишет следующий запуск, затем ставит alarm.
     */
    fun submitAnswer(raw: String) {
        val task = uiState.task ?: return
        if (answerInFlight || uiState.cooldownSeconds > 0 || uiState.unlocked) return
        answerInFlight = true
        viewModelScope.launch {
            try {
                if (task.matches(raw)) {
                    if (uiState.unlimited) {
                        repository.recordPracticeAttempt(task, correct = true, usedHint = uiState.hintUsed)
                        val correct = uiState.sessionCorrect + 1
                        uiState = uiState.copy(
                            sessionCorrect = correct,
                            task = repository.nextTask(),
                            hintVisible = false,
                            hintUsed = false,
                            feedback = "Верно"
                        )
                    } else {
                        val solved = repository.onCorrectAnswer(task, uiState.hintUsed)
                        val completed = solved >= uiState.tasksRequired
                        if (completed) {
                            repository.scheduleNextLaunch(uiState.profile, resetProgress = true)
                            LockController.scheduleNextLaunch(application)
                            uiState = uiState.copy(unlocked = true, tasksSolved = solved, feedback = "Все задания решены. Телефон разблокирован.")
                        } else {
                            uiState = uiState.copy(tasksSolved = solved, task = repository.nextTask(), hintVisible = false, hintUsed = false, feedback = "Верно. Осталось заданий: ${uiState.tasksRequired - solved}")
                        }
                    }
                } else {
                    if (uiState.unlimited) {
                        repository.recordPracticeAttempt(task, correct = false, usedHint = uiState.hintUsed)
                        uiState = uiState.copy(sessionWrong = uiState.sessionWrong + 1, feedback = "Неверно. Попробуй ещё раз.")
                    } else {
                        repository.onWrongAnswer(task, uiState.hintUsed)
                        uiState = uiState.copy(feedback = "Неверно. Попробуй ещё раз.")
                    }
                    for (sec in 2 downTo 1) {
                        uiState = uiState.copy(cooldownSeconds = sec)
                        delay(1000)
                    }
                    val refreshed = repository.nextTask()
                    uiState = uiState.copy(
                        task = refreshed,
                        hintVisible = false,
                        cooldownSeconds = 0
                    )
                }
            } finally {
                answerInFlight = false
            }
        }
    }

    /** Emergency unlock родительским PIN. Срок уже записан в [LearnLockRepository.unlockWithParentPin]. */
    fun parentUnlock(pin: String) {
        viewModelScope.launch {
            val ok = repository.unlockWithParentPin(pin)
            if (ok) LockController.scheduleNextLaunch(application)
            uiState = if (ok) {
                uiState.copy(
                    unlocked = true,
                    feedback = "Разблокировано родителем",
                    showParentPin = false
                )
            } else {
                uiState.copy(feedback = "Неверный PIN")
            }
        }
    }

    fun toggleParentPin(show: Boolean) {
        uiState = uiState.copy(showParentPin = show)
    }
}

/** Фабрика [LockViewModel] с инъекцией репозитория. */
class LockViewModelFactory(
    private val application: Application,
    private val repository: LearnLockRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LockViewModel::class.java)) {
            return LockViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}

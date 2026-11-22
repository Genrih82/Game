package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Платформо-независимый оркестратор генерации задач.
 *
 * Чередует категории ([CategoryRotator]), держит общий [RepeatAvoidance]
 * и делегирует генерацию провайдеру. Подходит для Android и возможного KMP/iOS.
 *
 * @param providers список провайдеров по категориям
 * @param random общий источник случайности
 * @param adaptiveSelector модуль адаптации запроса по статистике
 * @param repeatAvoidance общий буфер недавних формулировок на всю сессию движка
 */
class TaskEngine(
    private val repeatAvoidance: RepeatAvoidance = RepeatAvoidance(),
    private val providers: List<TaskProvider> = defaultProviders(repeatAvoidance),
    private val random: Random = Random.Default,
    private val adaptiveSelector: AdaptiveSelector = AdaptiveSelector(random = random)
) {
    /**
     * Возвращает следующую задачу по запросу ученика.
     *
     * Категория выбирается чередованием среди включённых; [preferredSkill]
     * учитывается только если относится к этой категории.
     *
     * @param request параметры возраста, категорий и статистики
     * @return одна [Task] от выбранного провайдера
     * @throws IllegalArgumentException если не включена ни одна категория
     */
    fun nextTask(request: TaskRequest): Task {
        val enabled = providers.filter { it.category in request.enabledCategories }
        require(enabled.isNotEmpty()) { "At least one task category must be enabled" }

        val refined = adaptiveSelector.refine(request)
        val req = refined.request

        val category = CategoryRotator.next(req.enabledCategories, req.lastCategory)
        val provider = enabled.first { it.category == category }

        val preferredSkill = AgeCurriculum.clampSkill(
            ageYears = req.ageYears,
            skill = refined.preferredSkill,
            category = category
        )

        val profile = DifficultyProfile(
            ageYears = req.ageYears.coerceIn(5, 16),
            offset = req.difficultyOffset.coerceIn(-2, 2),
            forceEasier = req.forceEasier,
            recentAccuracy = req.recentAccuracy.coerceIn(0.0, 1.0)
        )

        var task = provider.generate(profile, random, preferredSkill)
        var attempts = 0
        while (repeatAvoidance.isRecent(task) && attempts < 16) {
            task = provider.generate(profile, random, preferredSkill)
            attempts++
        }
        repeatAvoidance.markSeen(task)
        return task
    }

    companion object {
        /** Стандартный набор провайдеров; антиповтор держит [TaskEngine]. */
        @Suppress("UNUSED_PARAMETER")
        fun defaultProviders(repeatAvoidance: RepeatAvoidance = RepeatAvoidance()): List<TaskProvider> = listOf(
            ContextualMathProvider(),
            VocabularyReadingProvider(),
            ExpandedLogicProvider(),
            EnglishTaskProvider()
        )
    }
}

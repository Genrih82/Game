package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Корректирует [TaskRequest] по локальной статистике ученика:
 * усиливает слабые навыки и категории, слегка снижает вес сильных,
 * с заданной вероятностью закрепляет приоритетный слабый или «застоявшийся» навык.
 *
 * Категорию серии выбирает [CategoryRotator], не этот класс: приоритетный навык
 * применяется только если совпадает с уже выбранной категорией.
 *
 * @param preferWeakChance вероятность (0..1) выбрать слабый навык, если он не задан явно
 * @param preferStaleChance вероятность выбрать давно не встречавшийся навык, если слабых нет
 * @param random источник случайности для выбора слабого навыка
 */
class AdaptiveSelector(
    private val preferWeakChance: Double = 0.4,
    private val preferStaleChance: Double = 0.35,
    private val random: Random = Random.Default
) {
    /**
     * Результат адаптации: обновлённый запрос и вынесенные веса/приоритеты для отладки и выбора провайдера.
     *
     * @property request скорректированный [TaskRequest]
     * @property categoryWeights итоговые веса категорий после адаптации
     * @property preferredSkill выбранный приоритетный навык (может отличаться от исходного запроса)
     * @property preferredCategory категория приоритетного навыка
     */
    data class RefinedRequest(
        val request: TaskRequest,
        val categoryWeights: Map<TaskCategory, Int>,
        val preferredSkill: SkillTag?,
        val preferredCategory: TaskCategory?
    )

    /**
     * Строит уточнённый запрос: пересчитывает веса категорий, смещение сложности и флаги «проще».
     *
     * Без [TaskRequest.learnerStats] возвращает исходные параметры без изменений.
     *
     * @param request исходный запрос на задачу
     * @return [RefinedRequest] с обновлённым запросом и метаданными выбора
     */
    fun refine(request: TaskRequest): RefinedRequest {
        val stats = request.learnerStats
        if (stats == null) {
            return RefinedRequest(
                request = request,
                categoryWeights = request.categoryWeights,
                preferredSkill = request.preferredSkill,
                preferredCategory = request.preferredCategory
            )
        }

        val enabled = request.enabledCategories
        val weak = stats.weakSkills().filter { it.category in enabled }
        val strong = stats.strongSkills().filter { it.category in enabled }

        val weights = request.categoryWeights.toMutableMap()
        enabled.forEach { cat ->
            val base = (weights[cat] ?: 1).coerceAtLeast(1)
            val catAcc = stats.categoryAccuracy[cat]
            var factor = 1.0
            if (catAcc != null) {
                if (catAcc < 0.55) factor += 0.8
                if (catAcc > 0.85) factor -= 0.25
            }
            if (weak.any { it.category == cat }) factor += 0.5
            if (strong.any { it.category == cat } && weak.none { it.category == cat }) {
                factor -= 0.2
            }
            weights[cat] = (base * factor).toInt().coerceIn(1, 10)
        }

        var preferredSkill = request.preferredSkill
        var preferredCategory = request.preferredCategory
        if (preferredSkill == null && weak.isNotEmpty() && random.nextDouble() < preferWeakChance) {
            preferredSkill = weak.first()
            preferredCategory = preferredSkill.category
        }
        if (preferredSkill == null) {
            val stalePool = SkillTag.entries.filter { it.category in enabled }
            val stale = stats.staleSkills(stalePool)
            if (stale.isNotEmpty() && random.nextDouble() < preferStaleChance) {
                preferredSkill = stale.first()
                preferredCategory = preferredSkill.category
            }
        }

        var offset = request.difficultyOffset
        var forceEasier = request.forceEasier
        val accuracy = stats.overallAccuracy.takeIf { it > 0 } ?: request.recentAccuracy

        if (preferredSkill != null && preferredSkill in weak) {
            forceEasier = true
            offset = (offset - 1).coerceIn(-2, 2)
        } else if (stats.overallAccuracy > 0.85 && weak.isEmpty()) {
            offset = (offset + 1).coerceIn(-2, 2)
        }

        val refined = request.copy(
            categoryWeights = weights,
            preferredSkill = preferredSkill,
            preferredCategory = preferredCategory,
            difficultyOffset = offset,
            forceEasier = forceEasier,
            recentAccuracy = accuracy
        )
        return RefinedRequest(
            request = refined,
            categoryWeights = weights,
            preferredSkill = preferredSkill,
            preferredCategory = preferredCategory
        )
    }
}
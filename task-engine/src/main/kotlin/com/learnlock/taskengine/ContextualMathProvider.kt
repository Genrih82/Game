package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Генератор задач [TaskCategory.MATH]: шаблоны из [TemplateCatalog] и словарь предметов.
 *
 * Правильный ответ вычисляется детерминированно в [MathTemplateGenerator].
 * Повторы похожих условий ограничиваются через [RepeatAvoidance].
 */
class ContextualMathProvider(
    private val repeatAvoidance: RepeatAvoidance = RepeatAvoidance(),
    private val generator: MathTemplateGenerator = MathTemplateGenerator()
) : TaskProvider {
    override val category: TaskCategory = TaskCategory.MATH

    override fun generate(
        profile: DifficultyProfile,
        random: Random,
        preferredSkill: SkillTag?
    ): Task {
        var task = generateOnce(profile, random, preferredSkill)
        var attempts = 0
        while (repeatAvoidance.isRecent(task) && attempts < 16) {
            task = generateOnce(profile, random, preferredSkill)
            attempts++
        }
        repeatAvoidance.markSeen(task)
        return task
    }

    /** Одна попытка генерации без учёта буфера повторов. */
    private fun generateOnce(
        profile: DifficultyProfile,
        random: Random,
        preferredSkill: SkillTag?
    ): Task {
        val level = profile.level()
        val skill = preferredSkill?.takeIf { it.category == category } ?: skillByLevel(level, random)
        val pool = TemplateCatalog.mathFor(skill, level)
        if (pool.isEmpty()) {
            return fallbackAdd(level, random)
        }
        val entry = pool.random(random)
        return generator.generate(entry, profile, random)
    }

    /**
     * Выбор математического навыка по уровню сложности, если приоритет не задан.
     */
    private fun skillByLevel(level: Int, random: Random): SkillTag = when {
        level <= 2 -> SkillTag.MATH_ADD
        level == 3 -> if (random.nextBoolean()) SkillTag.MATH_ADD else SkillTag.MATH_SUB
        level == 4 -> listOf(SkillTag.MATH_ADD, SkillTag.MATH_SUB, SkillTag.MATH_COMPARE).random(random)
        level <= 6 -> listOf(SkillTag.MATH_SUB, SkillTag.MATH_MUL, SkillTag.MATH_COMPARE).random(random)
        level <= 8 -> listOf(SkillTag.MATH_MUL, SkillTag.MATH_COMPARE, SkillTag.MATH_MIX).random(random)
        else -> SkillTag.MATH_MIX
    }

    /** Запасной простой пример на сложение при пустом каталоге шаблонов. */
    private fun fallbackAdd(level: Int, random: Random): Task {
        val a = random.nextInt(1, 10)
        val b = random.nextInt(1, 10)
        return Task(
            id = "math-add-$a-$b",
            category = category,
            prompt = "Сколько будет $a + $b?",
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf((a + b).toString()),
            hint = "Сложи числа",
            difficultyLevel = level,
            skillTag = SkillTag.MATH_ADD
        )
    }
}
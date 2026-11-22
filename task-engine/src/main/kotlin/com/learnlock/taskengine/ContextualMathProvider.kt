package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Генератор задач [TaskCategory.MATH]: шаблоны из [TemplateCatalog] и словарь предметов.
 *
 * Правильный ответ вычисляется детерминированно в [MathTemplateGenerator].
 * Антиповтор формулировок выполняет [TaskEngine] через общий [RepeatAvoidance].
 */
class ContextualMathProvider(
    private val generator: MathTemplateGenerator = MathTemplateGenerator()
) : TaskProvider {
    override val category: TaskCategory = TaskCategory.MATH

    override fun generate(
        profile: DifficultyProfile,
        random: Random,
        preferredSkill: SkillTag?
    ): Task {
        val level = profile.level()
        val skill = AgeCurriculum.clampSkill(profile.ageYears, preferredSkill, category)
            ?: skillByAge(profile.ageYears, random)
        val pool = TemplateCatalog.mathFor(skill, level)
            .ifEmpty { TemplateCatalog.mathFor(skill, 10) }
        if (pool.isEmpty()) {
            return fallbackAdd(level, random)
        }
        val entry = pool.random(random)
        return generator.generate(entry, profile, random)
    }

    /**
     * Выбор навыка по возрасту (ФРП НОО): ×/÷ не раньше ориентира 2 класса.
     */
    private fun skillByAge(ageYears: Int, random: Random): SkillTag {
        val allowed = AgeCurriculum.allowedSkills(ageYears)
            .filter { it.category == category }
        require(allowed.isNotEmpty()) { "нет MATH-навыков для возраста $ageYears" }
        return allowed.random(random)
    }

    /** Запасной простой пример на сложение при пустом каталоге шаблонов. */
    private fun fallbackAdd(level: Int, random: Random): Task {
        val a = random.nextInt(1, 10)
        val b = random.nextInt(1, 10)
        val soft = "Посмотри на знаки в примере"
        val main = "Сложи числа"
        return Task(
            id = "math-add-$a-$b",
            category = category,
            prompt = "Сколько будет $a + $b?",
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf((a + b).toString()),
            hint = main,
            hints = Task.buildHints(soft, main),
            difficultyLevel = level,
            skillTag = SkillTag.MATH_ADD
        )
    }
}

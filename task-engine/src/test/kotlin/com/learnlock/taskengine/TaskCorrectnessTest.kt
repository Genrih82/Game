package com.learnlock.taskengine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Автотесты правильности сгенерированных заданий:
 * контракт [Task], однозначность ответа, арифметика по навыкам.
 */
class TaskCorrectnessTest {

    private val ages = listOf(5, 7, 9, 11, 14)
    private val samplesPerSkill = 25

    @Test
    fun everySkillProducesValidTasksAcrossAges() {
        val providers = mapOf(
            TaskCategory.MATH to ContextualMathProvider(),
            TaskCategory.READING to VocabularyReadingProvider(),
            TaskCategory.LOGIC to ExpandedLogicProvider(),
            TaskCategory.ENGLISH to EnglishTaskProvider()
        )
        SkillTag.entries.forEach { skill ->
            val provider = providers.getValue(skill.category)
            ages.forEach { age ->
                if (!AgeCurriculum.isSkillAllowed(age, skill)) return@forEach
                val profile = DifficultyProfile(
                    ageYears = age,
                    offset = 0,
                    forceEasier = false,
                    recentAccuracy = 0.7
                )
                repeat(samplesPerSkill) { i ->
                    val seed = age * 10_000 + skill.ordinal * 100 + i
                    val task = provider.generate(profile, Random(seed), skill)
                    assertTaskContract(task, expectedSkill = skill, seed = seed)
                }
            }
        }
    }

    @Test
    fun engineTasksAreAlwaysCorrectlyKeyed() {
        val engine = TaskEngine(random = Random(2026))
        var last: TaskCategory? = null
        repeat(120) { i ->
            val task = engine.nextTask(
                TaskRequest(
                    ageYears = 5 + (i % 10),
                    enabledCategories = TaskCategory.entries.toSet(),
                    lastCategory = last
                )
            )
            last = task.category
            assertTaskContract(task, expectedSkill = task.skillTag, seed = i)
        }
    }

    @Test
    fun choiceTasksHaveSingleCorrectOption() {
        val providers = listOf(
            ContextualMathProvider(),
            VocabularyReadingProvider(),
            ExpandedLogicProvider(),
            EnglishTaskProvider()
        )
        val profile = DifficultyProfile(10, 0, forceEasier = false, recentAccuracy = 0.75)
        providers.forEach { provider ->
            var choiceCount = 0
            repeat(80) { i ->
                val task = provider.generate(profile, Random(5000 + i * 17), null)
                if (task.inputType != AnswerInputType.CHOICE) return@repeat
                choiceCount++
                assertChoiceUniqueness(task)
            }
            assertTrue(choiceCount >= 10, "${provider::class.simpleName}: мало CHOICE задач ($choiceCount)")
        }
    }

    @Test
    fun numberTasksRejectNearbyWrongAnswers() {
        val provider = ContextualMathProvider()
        val profile = DifficultyProfile(9, 0, forceEasier = false, recentAccuracy = 0.7)
        listOf(
            SkillTag.MATH_ADD,
            SkillTag.MATH_SUB,
            SkillTag.MATH_MUL,
            SkillTag.MATH_DIV
        ).forEach { skill ->
            repeat(40) { i ->
                val task = provider.generate(profile, Random(9000 + skill.ordinal * 200 + i), skill)
                assertEquals(AnswerInputType.NUMBER, task.inputType, task.prompt)
                val correct = task.acceptedAnswers.first().toIntOrNull()
                    ?: fail("нечисловой ответ для $skill: ${task.acceptedAnswers} / ${task.prompt}")
                listOf(correct - 1, correct + 1, correct + 2, 0, -1).distinct()
                    .filter { it.toString() !in task.acceptedAnswers.map { a -> Task.normalize(a) } }
                    .forEach { wrong ->
                        assertFalse(
                            task.matches(wrong.toString()),
                            "ложное совпадение $wrong для «${task.prompt}», верно=$correct"
                        )
                    }
            }
        }
    }

    @Test
    fun simpleArithmeticTemplatesMatchComputedAnswer() {
        val generator = MathTemplateGenerator()
        val profile = DifficultyProfile(10, 0, forceEasier = false, recentAccuracy = 0.8)
        val checks = listOf(
            SkillTag.MATH_ADD to Regex("""(\d+)\s*\+\s*(\d+)"""),
            SkillTag.MATH_SUB to Regex("""(\d+)\s*[−\-]\s*(\d+)"""),
            SkillTag.MATH_MUL to Regex("""(\d+)\s*[×x*]\s*(\d+)""", RegexOption.IGNORE_CASE),
            SkillTag.MATH_DIV to Regex("""(\d+)\s*[÷:]\s*(\d+)""")
        )
        checks.forEach { (skill, pattern) ->
            val templates = TemplateCatalog.mathFor(skill, profile.level())
                .filter { pattern.containsMatchIn(it.template.replace("{a}", "1").replace("{b}", "2")) ||
                    pattern.containsMatchIn(
                        // грубая проверка: шаблон содержит оба операнда рядом с операцией
                        it.template
                    ) ||
                    "{a}" in it.template && "{b}" in it.template && (
                        "+" in it.template || "−" in it.template || "-" in it.template ||
                            "×" in it.template || "÷" in it.template || ":" in it.template ||
                            "плюс" in it.template || "минус" in it.template ||
                            "умножить" in it.template || "разделить" in it.template
                        )
                }
            assertTrue(templates.isNotEmpty(), "нет шаблонов для проверки $skill")
            var verified = 0
            templates.forEach { entry ->
                repeat(12) { i ->
                    val task = generator.generate(entry, profile, Random(skill.ordinal * 1000 + i))
                    val expected = when (skill) {
                        SkillTag.MATH_ADD, SkillTag.MATH_SUB, SkillTag.MATH_MUL, SkillTag.MATH_DIV -> {
                            val match = pattern.find(task.prompt) ?: return@repeat
                            val left = match.groupValues[1].toInt()
                            val right = match.groupValues[2].toInt()
                            when (skill) {
                                SkillTag.MATH_ADD -> left + right
                                SkillTag.MATH_SUB -> left - right
                                SkillTag.MATH_MUL -> left * right
                                SkillTag.MATH_DIV -> {
                                    assertEquals(0, left % right, "деление с остатком: ${task.prompt}")
                                    left / right
                                }
                                else -> return@repeat
                            }
                        }
                        else -> return@repeat
                    }
                    assertEquals(
                        expected.toString(),
                        task.acceptedAnswers.first(),
                        "неверный ответ для «${task.prompt}»"
                    )
                    verified++
                }
            }
            assertTrue(verified >= 8, "$skill: мало проверенных примеров ($verified)")
        }
    }

    @Test
    fun divisionTasksAlwaysExact() {
        val provider = ContextualMathProvider()
        val profile = DifficultyProfile(11, 0, forceEasier = false, recentAccuracy = 0.8)
        repeat(60) { i ->
            val task = provider.generate(profile, Random(12_000 + i), SkillTag.MATH_DIV)
            val answer = task.acceptedAnswers.first().toInt()
            assertTrue(answer > 0, task.prompt)
            val nums = Regex("""\d+""").findAll(task.prompt).map { it.value.toInt() }.toList()
            assertTrue(nums.size >= 2, "мало чисел в «${task.prompt}»")
            // Для шаблонов «a ÷ b» / «a : b» ответ = a/b; для «по b … Сколько коробок» ответ = a/b.
            val a = nums[0]
            val b = nums[1]
            if ("÷" in task.prompt || Regex("""\d+\s*:\s*\d+""").containsMatchIn(task.prompt) ||
                "разделить на" in task.prompt.lowercase()
            ) {
                assertEquals(0, a % b, "остаток в «${task.prompt}»")
                assertEquals(a / b, answer, task.prompt)
            } else {
                // контекстные формулировки: ответ должен делить одно из чисел на другое без остатка
                val divisible = nums.any { n -> n % answer == 0 && n / answer in nums }
                assertTrue(
                    divisible || nums.any { it % answer == 0 },
                    "деление не сходится: ${task.prompt} → $answer / nums=$nums"
                )
            }
        }
    }

    @Test
    fun analogyAnswersAreAmongChoicesAndUnique() {
        val provider = ExpandedLogicProvider()
        val profile = DifficultyProfile(8, 0, forceEasier = false, recentAccuracy = 0.7)
        repeat(40) { i ->
            val task = provider.generate(profile, Random(15_000 + i), SkillTag.LOGIC_ANALOGY)
            assertEquals(SkillTag.LOGIC_ANALOGY, task.skillTag)
            assertChoiceUniqueness(task)
        }
    }

    @Test
    fun catalogTemplatesHaveUniqueAcceptedComputationForOps() {
        val all = TemplateCatalog.mathTemplates()
        assertTrue(all.isNotEmpty())
        all.forEach { entry ->
            assertTrue(entry.template.isNotBlank(), "пустой шаблон $entry")
            assertTrue(entry.level in 1..10, "уровень вне диапазона: $entry")
            assertEquals(
                TaskCategory.MATH,
                entry.skillTag.category,
                "не MATH skill в math_templates: ${entry.skillTag}"
            )
        }
        assertTrue(TemplateCatalog.mathFor(SkillTag.MATH_DIV, 7).isNotEmpty())
    }

    private fun assertTaskContract(task: Task, expectedSkill: SkillTag, seed: Int) {
        val ctx = "skill=$expectedSkill seed=$seed prompt=${task.prompt}"
        assertEquals(expectedSkill, task.skillTag, ctx)
        assertEquals(expectedSkill.category, task.category, ctx)
        assertTrue(task.prompt.isNotBlank(), ctx)
        assertTrue(task.acceptedAnswers.isNotEmpty(), "нет ответа: $ctx")
        assertTrue(
            task.acceptedAnswers.map { Task.normalize(it) }.distinct().size == task.acceptedAnswers.size,
            "дубли в acceptedAnswers: $ctx / ${task.acceptedAnswers}"
        )
        assertTrue(task.difficultyLevel in 1..10, "difficulty=${task.difficultyLevel} $ctx")
        task.acceptedAnswers.forEach { answer ->
            assertTrue(task.matches(answer), "не матчится свой ответ «$answer»: $ctx")
            assertTrue(task.matches("  $answer  "), "trim/normalize сломан: $ctx")
        }
        when (task.inputType) {
            AnswerInputType.CHOICE -> assertChoiceUniqueness(task)
            AnswerInputType.NUMBER -> {
                task.acceptedAnswers.forEach { answer ->
                    assertTrue(
                        answer.toIntOrNull() != null || answer.toDoubleOrNull() != null,
                        "NUMBER без числа: $answer / $ctx"
                    )
                }
            }
            AnswerInputType.TEXT -> {
                assertTrue(task.acceptedAnswers.all { it.isNotBlank() }, ctx)
            }
        }
        val steps = task.hintSteps()
        assertTrue(steps.size == steps.distinct().size, "дубли подсказок: $steps / $ctx")
    }

    private fun assertChoiceUniqueness(task: Task) {
        assertEquals(AnswerInputType.CHOICE, task.inputType, task.prompt)
        val minChoices = when (task.skillTag) {
            SkillTag.LOGIC_SEQ, SkillTag.LOGIC_PATTERN, SkillTag.LOGIC_ANALOGY,
            SkillTag.READ_LETTER, SkillTag.READ_ODD, SkillTag.READ_SYNONYM,
            SkillTag.ENG_VOCAB, SkillTag.ENG_IRREGULAR, SkillTag.ENG_THREE_FORMS -> 4
            else -> 2
        }
        assertTrue(
            task.choices.size >= minChoices,
            "мало вариантов (${task.choices.size} < $minChoices): ${task.prompt} / ${task.choices}"
        )
        val labels = task.choices.map { Task.normalize(it.label) }
        assertEquals(labels.size, labels.distinct().size, "дубли вариантов: ${task.choices} / ${task.prompt}")
        val accepted = task.acceptedAnswers.map { Task.normalize(it) }
        assertTrue(
            accepted.any { it in labels },
            "верный ответ не среди вариантов: ${task.acceptedAnswers} / $labels / ${task.prompt}"
        )
        val correctLabels = labels.filter { it in accepted }
        assertEquals(
            1,
            correctLabels.size,
            "больше одного верного варианта: $labels / ${task.acceptedAnswers} / ${task.prompt}"
        )
        task.choices
            .map { it.label }
            .filter { Task.normalize(it) !in accepted }
            .forEach { wrong ->
                assertFalse(task.matches(wrong), "ложный вариант матчится: $wrong / ${task.prompt}")
            }
    }
}

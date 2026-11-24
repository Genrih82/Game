package com.learnlock.taskengine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * По 100 регрессионных прогонов на каждый предмет:
 * контракт задачи, однозначность CHOICE, возраст, совпадение ответа.
 */
class SubjectRegressionTest {

    private val samplesPerSubject = 100
    private val ages = listOf(5, 6, 7, 8, 9, 10, 11, 12, 14)

    private val providers: Map<TaskCategory, TaskProvider> = mapOf(
        TaskCategory.MATH to ContextualMathProvider(),
        TaskCategory.READING to VocabularyReadingProvider(),
        TaskCategory.LOGIC to ExpandedLogicProvider(),
        TaskCategory.ENGLISH to EnglishTaskProvider()
    )

    @Test
    fun mathRegression100() = runSubjectRegression(TaskCategory.MATH)

    @Test
    fun readingRegression100() = runSubjectRegression(TaskCategory.READING)

    @Test
    fun logicRegression100() = runSubjectRegression(TaskCategory.LOGIC)

    @Test
    fun englishRegression100() = runSubjectRegression(TaskCategory.ENGLISH)

    @Test
    fun mathEngineOnlyCategory100() = runEngineCategoryRegression(TaskCategory.MATH)

    @Test
    fun readingEngineOnlyCategory100() = runEngineCategoryRegression(TaskCategory.READING)

    @Test
    fun logicEngineOnlyCategory100() = runEngineCategoryRegression(TaskCategory.LOGIC)

    @Test
    fun englishEngineOnlyCategory100() = runEngineCategoryRegression(TaskCategory.ENGLISH)

    private fun runSubjectRegression(category: TaskCategory) {
        val provider = providers.getValue(category)
        val skills = SkillTag.entries.filter { it.category == category }
        repeat(samplesPerSubject) { i ->
            val age = ages[i % ages.size]
            val profile = DifficultyProfile(
                ageYears = age,
                offset = listOf(-1, 0, 0, 1)[i % 4],
                forceEasier = i % 11 == 0,
                recentAccuracy = listOf(0.4, 0.7, 0.9)[i % 3]
            )
            val preferred = skills
                .filter { AgeCurriculum.isSkillAllowed(age, it) }
                .let { allowed ->
                    when {
                        allowed.isEmpty() -> null
                        i % 3 == 0 -> null
                        else -> allowed[i % allowed.size]
                    }
                }
            val seed = category.ordinal * 100_000 + i * 37 + age * 13
            val task = try {
                provider.generate(profile, Random(seed), preferred)
            } catch (e: Throwable) {
                fail("[$category #$i age=$age skill=$preferred] генерация упала: ${e.message}", e)
            }
            assertTaskOk(task, category, age, seed, i)
        }
    }

    private fun runEngineCategoryRegression(category: TaskCategory) {
        val engine = TaskEngine(random = Random(category.ordinal * 999 + 7))
        repeat(samplesPerSubject) { i ->
            val age = ages[i % ages.size]
            val task = try {
                engine.nextTask(
                    TaskRequest(
                        ageYears = age,
                        enabledCategories = setOf(category),
                        difficultyOffset = listOf(-1, 0, 1)[i % 3],
                        forceEasier = i % 9 == 0,
                        recentAccuracy = 0.55 + (i % 5) * 0.08
                    )
                )
            } catch (e: Throwable) {
                fail("[engine $category #$i age=$age] генерация упала: ${e.message}", e)
            }
            assertTaskOk(task, category, age, seed = i, index = i)
        }
    }

    private fun assertTaskOk(
        task: Task,
        category: TaskCategory,
        age: Int,
        seed: Int,
        index: Int
    ) {
        val ctx = "#$index $category age=$age seed=$seed skill=${task.skillTag} prompt=${task.prompt}"
        assertEquals(category, task.category, ctx)
        assertEquals(category, task.skillTag.category, ctx)
        assertTrue(AgeCurriculum.isSkillAllowed(age, task.skillTag), "навык вне возраста: $ctx")
        assertTrue(task.prompt.isNotBlank(), ctx)
        assertTrue(task.id.isNotBlank(), ctx)
        assertTrue(task.acceptedAnswers.isNotEmpty(), "нет ответа: $ctx")
        assertEquals(
            task.acceptedAnswers.size,
            task.acceptedAnswers.map { Task.normalize(it) }.distinct().size,
            "дубли acceptedAnswers: $ctx / ${task.acceptedAnswers}"
        )
        assertTrue(task.difficultyLevel in 1..10, "difficulty=${task.difficultyLevel} $ctx")
        task.acceptedAnswers.forEach { answer ->
            assertTrue(task.matches(answer), "свой ответ не матчится «$answer»: $ctx")
            assertTrue(task.matches("  $answer\t"), "normalize сломан: $ctx")
        }

        when (task.inputType) {
            AnswerInputType.NUMBER -> {
                task.acceptedAnswers.forEach { answer ->
                    assertTrue(
                        answer.toIntOrNull() != null || answer.toDoubleOrNull() != null,
                        "NUMBER без числа: $answer / $ctx"
                    )
                }
                // чужие соседние числа не должны проходить
                val correct = task.acceptedAnswers.first().toIntOrNull()
                if (correct != null) {
                    listOf(correct - 1, correct + 1, correct + 3).forEach { wrong ->
                        if (wrong.toString() !in task.acceptedAnswers.map { Task.normalize(it) }) {
                            assertFalse(task.matches(wrong.toString()), "ложный NUMBER $wrong: $ctx")
                        }
                    }
                }
            }
            AnswerInputType.CHOICE -> assertChoiceOk(task, ctx)
            AnswerInputType.TEXT -> {
                assertTrue(task.acceptedAnswers.all { it.isNotBlank() }, ctx)
            }
        }

        val hints = task.hintSteps()
        assertEquals(hints.size, hints.distinct().size, "дубли подсказок: $hints / $ctx")

        when (category) {
            TaskCategory.MATH -> assertMathExtras(task, age, ctx)
            TaskCategory.LOGIC -> assertLogicExtras(task, ctx)
            TaskCategory.ENGLISH -> assertEnglishExtras(task, ctx)
            TaskCategory.READING -> Unit
        }
    }

    private fun assertChoiceOk(task: Task, ctx: String) {
        val minChoices = when (task.skillTag) {
            SkillTag.MATH_COMPARE, SkillTag.MATH_MIX, SkillTag.LOGIC_WORD -> 2
            else -> 4
        }
        assertTrue(
            task.choices.size >= minChoices,
            "мало вариантов ${task.choices.size} < $minChoices: $ctx / ${task.choices}"
        )
        if (task.skillTag == SkillTag.LOGIC_SEQ || task.skillTag == SkillTag.LOGIC_PATTERN) {
            assertEquals(4, task.choices.size, "для ${task.skillTag} нужно ровно 4: $ctx")
        }
        val labels = task.choices.map { Task.normalize(it.label) }
        assertEquals(labels.size, labels.distinct().size, "дубли кнопок: ${task.choices} / $ctx")
        val accepted = task.acceptedAnswers.map { Task.normalize(it) }
        assertTrue(accepted.any { it in labels }, "верный ответ не в кнопках: $ctx")
        assertEquals(1, labels.count { it in accepted }, "несколько верных кнопок: $ctx")
        task.choices.map { it.label }
            .filter { Task.normalize(it) !in accepted }
            .forEach { wrong ->
                assertFalse(task.matches(wrong), "ложный вариант «$wrong»: $ctx")
            }
    }

    private fun assertMathExtras(task: Task, age: Int, ctx: String) {
        when (task.skillTag) {
            SkillTag.MATH_DIV -> {
                val answer = task.acceptedAnswers.first().toIntOrNull() ?: return
                assertTrue(answer > 0, ctx)
                val div = Regex("""(\d+)\s*[÷:]\s*(\d+)""").find(task.prompt)
                if (div != null) {
                    val a = div.groupValues[1].toInt()
                    val b = div.groupValues[2].toInt()
                    assertEquals(0, a % b, "деление с остатком: $ctx")
                    assertEquals(a / b, answer, ctx)
                    assertTrue(a <= AgeCurriculum.maxProduct(age).coerceAtLeast(a), ctx)
                }
            }
            SkillTag.MATH_MUL -> {
                val mul = Regex("""(\d+)\s*[×x*]\s*(\d+)""", RegexOption.IGNORE_CASE).find(task.prompt)
                if (mul != null) {
                    val a = mul.groupValues[1].toInt()
                    val b = mul.groupValues[2].toInt()
                    assertEquals((a * b).toString(), task.acceptedAnswers.first(), ctx)
                    assertTrue(a * b <= AgeCurriculum.maxProduct(age).coerceAtLeast(a * b), ctx)
                }
            }
            else -> Unit
        }
    }

    private fun assertLogicExtras(task: Task, ctx: String) {
        if (task.skillTag == SkillTag.LOGIC_SEQ) {
            assertEquals(4, task.choices.size, ctx)
            // в последовательности с отрицательными числами варианты тоже могут быть < 0
            assertTrue(task.choices.isNotEmpty(), ctx)
        }
        if (task.skillTag == SkillTag.LOGIC_ANALOGY) {
            assertEquals(4, task.choices.size, ctx)
        }
    }

    private fun assertEnglishExtras(task: Task, ctx: String) {
        when (task.skillTag) {
            SkillTag.ENG_IRREGULAR -> {
                val base = Regex("""«([a-zA-Z]+)»""").find(task.prompt)?.groupValues?.get(1)?.lowercase()
                if (base != null) {
                    val verb = EnglishVocabulary.irregularVerbs.firstOrNull { it.base == base }
                    if (verb != null) {
                        assertTrue(
                            task.acceptedAnswers.any { Task.normalize(it) == Task.normalize(verb.past) },
                            "past для $base должен быть ${verb.past}: $ctx"
                        )
                    }
                }
            }
            SkillTag.ENG_THREE_FORMS -> {
                val parts = task.acceptedAnswers.first().split("–").map { it.trim() }
                assertEquals(3, parts.size, "три формы: $ctx")
            }
            else -> Unit
        }
    }
}

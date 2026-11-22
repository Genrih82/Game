package com.learnlock.taskengine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaskEngineTest {
    private val engine = TaskEngine(random = Random(42))

    @Test
    fun emptyCategoriesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            engine.nextTask(
                TaskRequest(
                    ageYears = 8,
                    enabledCategories = emptySet()
                )
            )
        }
    }

    @Test
    fun ageOutsideRangeMatchesClampedAge() {
        val request = { age: Int ->
            TaskRequest(
                ageYears = age,
                enabledCategories = setOf(TaskCategory.MATH),
                difficultyOffset = 0,
                recentAccuracy = 0.7
            )
        }
        val younger = TaskEngine(random = Random(3)).nextTask(request(1))
        val lowerBound = TaskEngine(random = Random(3)).nextTask(request(5))
        assertEquals(lowerBound.difficultyLevel, younger.difficultyLevel)
        assertEquals(lowerBound.prompt, younger.prompt)

        val older = TaskEngine(random = Random(3)).nextTask(request(30))
        val upperBound = TaskEngine(random = Random(3)).nextTask(request(16))
        assertEquals(upperBound.difficultyLevel, older.difficultyLevel)
        assertEquals(upperBound.prompt, older.prompt)
    }

    @Test
    fun generatesMathWithinEnabledCategories() {
        val task = engine.nextTask(
            TaskRequest(
                ageYears = 8,
                enabledCategories = setOf(TaskCategory.MATH),
                preferredCategory = TaskCategory.MATH
            )
        )
        assertEquals(TaskCategory.MATH, task.category)
        assertTrue(task.acceptedAnswers.isNotEmpty())
        assertTrue(task.matches(task.acceptedAnswers.first()))
        assertEquals(TaskCategory.MATH, task.skillTag.category)
    }

    @Test
    fun forceEasierLowersDifficultyBand() {
        val hard = DifficultyProfile(10, 1, forceEasier = false, recentAccuracy = 0.9).level()
        val easy = DifficultyProfile(10, 1, forceEasier = true, recentAccuracy = 0.9).level()
        assertTrue(easy < hard)
    }

    @Test
    fun readingAndLogicGenerateValidChoiceTasks() {
        val reading = engine.nextTask(
            TaskRequest(ageYears = 7, enabledCategories = setOf(TaskCategory.READING))
        )
        val logic = engine.nextTask(
            TaskRequest(ageYears = 9, enabledCategories = setOf(TaskCategory.LOGIC))
        )
        assertEquals(TaskCategory.READING, reading.category)
        assertEquals(TaskCategory.LOGIC, logic.category)
        assertTrue(reading.matches(reading.acceptedAnswers.first()))
        assertTrue(logic.matches(logic.acceptedAnswers.first()))
    }

    @Test
    fun normalizeAcceptsTrimAndCase() {
        val task = Task(
            id = "t",
            category = TaskCategory.READING,
            prompt = "x",
            inputType = AnswerInputType.TEXT,
            acceptedAnswers = listOf("Стол"),
            difficultyLevel = 1,
            skillTag = SkillTag.READ_ODD
        )
        assertTrue(task.matches("  стол "))
    }

    @Test
    fun preferredSkillMathMulIsHonored() {
        val task = engine.nextTask(
            TaskRequest(
                ageYears = 10,
                enabledCategories = setOf(TaskCategory.MATH),
                preferredSkill = SkillTag.MATH_MUL
            )
        )
        assertEquals(SkillTag.MATH_MUL, task.skillTag)
    }

    @Test
    fun weakMathMulBoostsMultiplicationTasks() {
        val attempts = List(5) {
            AttemptRecord(SkillTag.MATH_MUL, TaskCategory.MATH, correct = false)
        } + List(5) {
            AttemptRecord(SkillTag.MATH_ADD, TaskCategory.MATH, correct = true)
        }
        val stats = LearnerStats.fromAttempts(attempts)
        assertTrue(SkillTag.MATH_MUL in stats.weakSkills())

        val seeded = TaskEngine(random = Random(7))
        val mulCount = (1..40).count {
            val task = seeded.nextTask(
                TaskRequest(
                    ageYears = 10,
                    enabledCategories = setOf(TaskCategory.MATH),
                    learnerStats = stats
                )
            )
            task.skillTag == SkillTag.MATH_MUL
        }
        assertTrue(mulCount >= 8, "expected frequent MATH_MUL, got $mulCount")
    }

    @Test
    fun categoriesRotateAcrossCalls() {
        val seeded = TaskEngine(random = Random(11))
        val enabled = setOf(
            TaskCategory.MATH,
            TaskCategory.READING,
            TaskCategory.LOGIC,
            TaskCategory.ENGLISH
        )
        var last: TaskCategory? = null
        val seen = mutableListOf<TaskCategory>()
        repeat(8) {
            val task = seeded.nextTask(
                TaskRequest(ageYears = 9, enabledCategories = enabled, lastCategory = last)
            )
            seen += task.category
            last = task.category
        }
        assertEquals(
            listOf(
                TaskCategory.MATH,
                TaskCategory.READING,
                TaskCategory.LOGIC,
                TaskCategory.ENGLISH,
                TaskCategory.MATH,
                TaskCategory.READING,
                TaskCategory.LOGIC,
                TaskCategory.ENGLISH
            ),
            seen
        )
    }

    @Test
    fun hintStepsPreferMultiLevelList() {
        val task = Task(
            id = "h",
            category = TaskCategory.MATH,
            prompt = "1+1",
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf("2"),
            hint = "старая",
            hints = listOf("мягкая", "прямая"),
            difficultyLevel = 1,
            skillTag = SkillTag.MATH_ADD
        )
        assertEquals(listOf("мягкая", "прямая"), task.hintSteps())
    }

    @Test
    fun staleSkillPreferredWhenNoWeak() {
        val recent = listOf(
            SkillTag.MATH_ADD,
            SkillTag.MATH_SUB,
            SkillTag.MATH_DIV,
            SkillTag.MATH_COMPARE,
            SkillTag.MATH_MIX
        ).flatMap { skill ->
            List(2) { AttemptRecord(skill, TaskCategory.MATH, correct = true) }
        }
        val stats = LearnerStats.fromAttempts(recent)
        assertTrue(SkillTag.MATH_MUL in stats.staleSkills(listOf(SkillTag.MATH_MUL)))
        val selector = AdaptiveSelector(preferWeakChance = 0.0, preferStaleChance = 1.0, random = Random(2))
        val refined = selector.refine(
            TaskRequest(
                ageYears = 10,
                enabledCategories = setOf(TaskCategory.MATH),
                learnerStats = stats
            )
        )
        assertEquals(SkillTag.MATH_MUL, refined.preferredSkill)
    }

    @Test
    fun preferredSkillMathDivIsHonored() {
        val task = engine.nextTask(
            TaskRequest(
                ageYears = 11,
                enabledCategories = setOf(TaskCategory.MATH),
                preferredSkill = SkillTag.MATH_DIV
            )
        )
        assertEquals(SkillTag.MATH_DIV, task.skillTag)
        assertTrue(task.matches(task.acceptedAnswers.first()))
    }

    @Test
    fun weakSkillForcesEasierDifficulty() {
        val attempts = List(4) {
            AttemptRecord(SkillTag.MATH_ADD, TaskCategory.MATH, correct = false)
        }
        val stats = LearnerStats.fromAttempts(attempts)
        val selector = AdaptiveSelector(preferWeakChance = 1.0, random = Random(1))
        val refined = selector.refine(
            TaskRequest(
                ageYears = 10,
                difficultyOffset = 0,
                enabledCategories = setOf(TaskCategory.MATH),
                learnerStats = stats
            )
        )
        assertTrue(refined.request.forceEasier)
        assertEquals(SkillTag.MATH_ADD, refined.preferredSkill)

        val without = DifficultyProfile(10, 0, forceEasier = false, recentAccuracy = 0.0).level()
        val with = DifficultyProfile(
            10,
            refined.request.difficultyOffset,
            forceEasier = refined.request.forceEasier,
            recentAccuracy = refined.request.recentAccuracy
        ).level()
        assertTrue(with < without)
    }

    @Test
    fun withoutStatsKeepsBaseWeights() {
        val selector = AdaptiveSelector(random = Random(0))
        val request = TaskRequest(
            ageYears = 8,
            categoryWeights = mapOf(
                TaskCategory.MATH to 2,
                TaskCategory.READING to 1,
                TaskCategory.LOGIC to 1
            )
        )
        val refined = selector.refine(request)
        assertEquals(2, refined.categoryWeights[TaskCategory.MATH])
        assertEquals(null, refined.preferredSkill)
    }
}

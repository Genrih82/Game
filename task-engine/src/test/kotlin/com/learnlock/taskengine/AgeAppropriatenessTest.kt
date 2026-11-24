package com.learnlock.taskengine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Проверка соответствия заданий возрасту по ориентирам ФРП НОО
 * (Приказ Минпросвещения № 372, программа «Математика» 1–4).
 */
class AgeAppropriatenessTest {

    @Test
    fun gradeOneHasNoMultiplicationOrDivision() {
        // 1 класс / возраст ≤7: только + − и сравнение (ФРП: ×/÷ со 2 класса).
        listOf(5, 6, 7).forEach { age ->
            assertFalse(AgeCurriculum.isSkillAllowed(age, SkillTag.MATH_MUL), "age=$age MUL")
            assertFalse(AgeCurriculum.isSkillAllowed(age, SkillTag.MATH_DIV), "age=$age DIV")
            assertFalse(AgeCurriculum.isSkillAllowed(age, SkillTag.MATH_MIX), "age=$age MIX")
            assertTrue(AgeCurriculum.isSkillAllowed(age, SkillTag.MATH_ADD))
            assertTrue(AgeCurriculum.isSkillAllowed(age, SkillTag.MATH_SUB) || age <= 5)
        }
    }

    @Test
    fun gradeTwoIntroducesTableMultiplicationWithin50() {
        assertTrue(AgeCurriculum.isSkillAllowed(8, SkillTag.MATH_MUL))
        assertTrue(AgeCurriculum.isSkillAllowed(8, SkillTag.MATH_DIV))
        assertTrue(AgeCurriculum.maxProduct(8) <= 50)
    }

    @Test
    fun engineNeverEmitsForbiddenSkillsForYoungAges() {
        val engine = TaskEngine(random = Random(42))
        listOf(5, 6, 7).forEach { age ->
            var last: TaskCategory? = null
            repeat(60) {
                val task = engine.nextTask(
                    TaskRequest(
                        ageYears = age,
                        enabledCategories = TaskCategory.entries.toSet(),
                        lastCategory = last,
                        // даже при «слабом» MUL адаптация не должна пробить потолок возраста
                        preferredSkill = SkillTag.MATH_MUL,
                        learnerStats = LearnerStats.fromAttempts(
                            List(5) { AttemptRecord(SkillTag.MATH_MUL, TaskCategory.MATH, false) }
                        )
                    )
                )
                last = task.category
                assertTrue(
                    AgeCurriculum.isSkillAllowed(age, task.skillTag),
                    "age=$age skill=${task.skillTag} prompt=${task.prompt}"
                )
            }
        }
    }

    @Test
    fun mathOperandsStayWithinFgosBands() {
        val provider = ContextualMathProvider()
        data class Case(val age: Int, val skill: SkillTag, val maxOperand: Int, val maxProduct: Int?)
        val cases = listOf(
            Case(7, SkillTag.MATH_ADD, 20, null),
            Case(7, SkillTag.MATH_SUB, 20, null),
            Case(8, SkillTag.MATH_MUL, 10, 50),
            Case(8, SkillTag.MATH_DIV, 10, 50),
            Case(9, SkillTag.MATH_MUL, 12, 100)
        )
        cases.forEach { case ->
            val profile = DifficultyProfile(case.age, 0, forceEasier = false, recentAccuracy = 0.7)
            repeat(40) { i ->
                val task = provider.generate(profile, Random(case.age * 1000 + i), case.skill)
                assertTrue(task.skillTag == case.skill, task.prompt)
                val nums = Regex("""\d+""").findAll(task.prompt).map { it.value.toInt() }.toList()
                when (case.skill) {
                    SkillTag.MATH_ADD, SkillTag.MATH_SUB -> {
                        // в простом примере оба слагаемых / уменьшаемое в пределах band
                        val compact = task.prompt.replace(" ", "")
                        val opMatch = Regex("""(\d+)[+\-−](\d+)""").find(compact)
                        if (opMatch != null) {
                            val left = opMatch.groupValues[1].toInt()
                            val right = opMatch.groupValues[2].toInt()
                            assertTrue(left <= case.maxOperand + 5, "left=$left ${task.prompt}")
                            assertTrue(right <= case.maxOperand, "right=$right ${task.prompt}")
                        }
                    }
                    SkillTag.MATH_MUL -> {
                        val m = Regex("""(\d+)\s*[×x*]\s*(\d+)""", RegexOption.IGNORE_CASE).find(task.prompt)
                        if (m != null) {
                            val a = m.groupValues[1].toInt()
                            val b = m.groupValues[2].toInt()
                            assertTrue(a <= case.maxOperand, task.prompt)
                            assertTrue(b <= case.maxOperand, task.prompt)
                            assertTrue(a * b <= (case.maxProduct ?: Int.MAX_VALUE), task.prompt)
                        }
                    }
                    SkillTag.MATH_DIV -> {
                        val m = Regex("""(\d+)\s*[÷:]\s*(\d+)""").find(task.prompt)
                        if (m != null) {
                            val a = m.groupValues[1].toInt()
                            val b = m.groupValues[2].toInt()
                            assertTrue(b <= case.maxOperand, task.prompt)
                            assertTrue(a <= (case.maxProduct ?: Int.MAX_VALUE), task.prompt)
                            assertTrue(a % b == 0, task.prompt)
                        }
                    }
                    else -> fail("unexpected ${case.skill}")
                }
                nums.forEach { n ->
                    // отсекаем астрономические числа вне устного режима
                    assertTrue(n <= 1000, "слишком большое число $n в «${task.prompt}» для age=${case.age}")
                }
            }
        }
    }

    @Test
    fun englishIrregularsNotForFirstGrade() {
        assertFalse(AgeCurriculum.isSkillAllowed(7, SkillTag.ENG_IRREGULAR))
        assertTrue(AgeCurriculum.isSkillAllowed(7, SkillTag.ENG_VOCAB))
        assertTrue(AgeCurriculum.isSkillAllowed(8, SkillTag.ENG_IRREGULAR))
        assertTrue(AgeCurriculum.isSkillAllowed(10, SkillTag.ENG_THREE_FORMS))
    }

    @Test
    fun readingSynonymsNotForcedOnFirstGrade() {
        val provider = VocabularyReadingProvider()
        val profile = DifficultyProfile(7, 0, forceEasier = false, recentAccuracy = 0.7)
        repeat(30) { i ->
            val task = provider.generate(profile, Random(3000 + i), SkillTag.READ_SYNONYM)
            assertTrue(
                task.skillTag != SkillTag.READ_SYNONYM || AgeCurriculum.isSkillAllowed(7, SkillTag.READ_SYNONYM),
                task.prompt
            )
            // preferred synonym для 1 класса должен схлопнуться в допустимый навык
            assertTrue(AgeCurriculum.isSkillAllowed(7, task.skillTag), "${task.skillTag}")
        }
    }

    @Test
    fun randomGenerationDistributionRespectsAgeCaps() {
        listOf(5, 7, 8, 10).forEach { age ->
            val provider = ContextualMathProvider()
            val profile = DifficultyProfile(age, 0, forceEasier = false, recentAccuracy = 0.7)
            val forbidden = SkillTag.entries
                .filter { it.category == TaskCategory.MATH }
                .filterNot { AgeCurriculum.isSkillAllowed(age, it) }
                .toSet()
            repeat(50) { i ->
                val task = provider.generate(profile, Random(age * 500 + i), null)
                assertFalse(
                    task.skillTag in forbidden,
                    "age=$age получил запрещённый ${task.skillTag}: ${task.prompt}"
                )
            }
        }
    }
}

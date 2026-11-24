package com.learnlock.taskengine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Верный ответ не должен систематически оказываться в первой кнопке.
 */
class ChoiceShuffleTest {

    @Test
    fun correctAnswerNotStuckAtFirstButton() {
        val engine = TaskEngine(random = Random(2026))
        val firstSlotHits = IntArray(4)
        var choiceTasks = 0
        var last: TaskCategory? = null
        repeat(400) { i ->
            val task = engine.nextTask(
                TaskRequest(
                    ageYears = 9 + (i % 4),
                    lastCategory = last
                )
            )
            last = task.category
            if (task.inputType != AnswerInputType.CHOICE || task.choices.size < 2) return@repeat
            choiceTasks++
            val idx = task.choices.indexOfFirst { choice ->
                task.acceptedAnswers.any { Task.normalize(it) == Task.normalize(choice.label) }
            }
            assertTrue(idx >= 0, task.prompt)
            firstSlotHits[idx.coerceAtMost(3)]++
        }
        assertTrue(choiceTasks >= 80, "мало CHOICE задач: $choiceTasks")
        val shareFirst = firstSlotHits[0].toDouble() / choiceTasks
        assertTrue(
            shareFirst < 0.45,
            "верный ответ слишком часто в первой кнопке: ${(shareFirst * 100).toInt()}% ($choiceTasks задач)"
        )
        // хотя бы два разных слота должны встречаться
        assertTrue(firstSlotHits.count { it > 0 } >= 2, firstSlotHits.contentToString())
    }

    @Test
    fun logicSequencesAlwaysHaveFourChoicesIncludingNegatives() {
        val provider = ExpandedLogicProvider()
        // Высокий level чаще даёт отрицательный шаг (2, 0, -2, -4, …)
        val profile = DifficultyProfile(14, 0, forceEasier = false, recentAccuracy = 0.9)
        repeat(120) { i ->
            val task = provider.generate(profile, Random(10_000 + i), SkillTag.LOGIC_SEQ)
            assertTrue(
                task.choices.size == 4,
                "ожидали 4 варианта, есть ${task.choices.size}: ${task.prompt} / ${task.choices}"
            )
            assertTrue(
                task.choices.map { Task.normalize(it.label) }.distinct().size == 4,
                "дубли вариантов: ${task.choices} / ${task.prompt}"
            )
            assertTrue(
                task.acceptedAnswers.any { ans ->
                    task.choices.any { Task.normalize(it.label) == Task.normalize(ans) }
                },
                task.prompt
            )
        }
    }

    @Test
    fun synonymAnswerIsShuffled() {
        val provider = VocabularyReadingProvider()
        val profile = DifficultyProfile(12, 0, forceEasier = false, recentAccuracy = 0.8)
        var first = 0
        repeat(80) { i ->
            val task = provider.generate(profile, Random(i * 17 + 3), SkillTag.READ_SYNONYM)
            val idx = task.choices.indexOfFirst {
                Task.normalize(it.label) == Task.normalize(task.acceptedAnswers.first())
            }
            if (idx == 0) first++
        }
        assertTrue(first in 5..55, "синоним: first=$first/80")
    }
}

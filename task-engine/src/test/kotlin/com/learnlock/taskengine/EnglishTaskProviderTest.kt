package com.learnlock.taskengine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnglishTaskProviderTest {
    private val provider = EnglishTaskProvider()

    @Test
    fun dictionariesLoad() {
        assertTrue(EnglishVocabulary.irregularVerbs.size >= 40)
        assertTrue(EnglishVocabulary.vocab.size >= 20)
        assertTrue(EnglishVocabulary.irregularVerbs.any { it.base == "go" && it.past == "went" })
    }

    @Test
    fun irregularPastAnswerIsCorrectForm() {
        val profile = DifficultyProfile(10, 0, forceEasier = false, recentAccuracy = 0.7)
        repeat(40) { i ->
            val task = provider.generate(profile, Random(2000 + i), SkillTag.ENG_IRREGULAR)
            assertEquals(SkillTag.ENG_IRREGULAR, task.skillTag)
            assertTrue(task.matches(task.acceptedAnswers.first()), task.prompt)
            val base = Regex("""«([a-z]+)»""").find(task.prompt.lowercase())?.groupValues?.get(1)
            if (base != null) {
                val verb = EnglishVocabulary.irregularVerbs.first { it.base == base }
                assertTrue(
                    task.acceptedAnswers.any { Task.normalize(it) == Task.normalize(verb.past) },
                    "ожидали ${verb.past} для $base: ${task.acceptedAnswers}"
                )
            }
            val labels = task.choices.map { Task.normalize(it.label) }
            assertEquals(labels.size, labels.distinct().size, task.prompt)
        }
    }

    @Test
    fun youngAgeGetsOnlyVocab() {
        val profile = DifficultyProfile(7, 0, forceEasier = false, recentAccuracy = 0.7)
        repeat(20) { i ->
            val task = provider.generate(profile, Random(4000 + i), SkillTag.ENG_IRREGULAR)
            assertEquals(SkillTag.ENG_VOCAB, task.skillTag, "1 класс не должен получать irregular")
        }
    }

    @Test
    fun threeFormsContainBasePastParticiple() {
        val profile = DifficultyProfile(11, 0, forceEasier = false, recentAccuracy = 0.8)
        repeat(25) { i ->
            val task = provider.generate(profile, Random(6000 + i), SkillTag.ENG_THREE_FORMS)
            assertEquals(SkillTag.ENG_THREE_FORMS, task.skillTag)
            val answer = task.acceptedAnswers.first()
            val parts = answer.split("–").map { it.trim() }
            assertEquals(3, parts.size, answer)
            val verb = EnglishVocabulary.irregularVerbs.first { it.base == parts[0].lowercase() }
            assertEquals(verb.past, parts[1].lowercase())
            assertEquals(verb.participle, parts[2].lowercase())
        }
    }
}

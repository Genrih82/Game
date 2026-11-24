package com.learnlock.taskengine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerationInfraTest {

    @Test
    fun templateEngineRendersVariables() {
        val out = TemplateEngine.render(
            "У {name} {count} {item}",
            mapOf("name" to "Миши", "count" to "5", "item" to "яблок")
        )
        assertEquals("У Миши 5 яблок", out)
        assertEquals(setOf("name", "count", "item"), TemplateEngine.variablesOf("У {name} {count} {item}"))
    }

    @Test
    fun templateCatalogLoadsMathTemplates() {
        val all = TemplateCatalog.mathTemplates()
        assertTrue(all.isNotEmpty(), "math_templates.txt должен загрузиться")
        assertTrue(TemplateCatalog.mathFor(SkillTag.MATH_ADD, 3).isNotEmpty())
        assertTrue(TemplateCatalog.mathFor(SkillTag.MATH_MIX, 10).isNotEmpty())
    }

    @Test
    fun vocabularyLoadsWords() {
        val vocab = VocabularyDatabase()
        assertTrue(vocab.words(VocabularyDatabase.Level.EASY, "animal").isNotEmpty())
        assertTrue(vocab.words(VocabularyDatabase.Level.MEDIUM, "food").isNotEmpty())
        assertTrue(vocab.words(VocabularyDatabase.Level.HARD, "science").isNotEmpty())
        assertTrue(vocab.categories(VocabularyDatabase.Level.EASY).size >= 6)
    }

    @Test
    fun mathProviderProducesVariedTasks() {
        val provider = ContextualMathProvider()
        val profile = DifficultyProfile(10, 0, forceEasier = false, recentAccuracy = 0.8)
        val prompts = (1..40).map {
            provider.generate(profile, Random(it), null).prompt
        }
        assertTrue(prompts.distinct().size >= 10, "ожидалось разнообразие, got ${prompts.distinct().size}")
    }

    @Test
    fun allProvidersProduceMatchingAnswers() {
        val profile = DifficultyProfile(9, 0, forceEasier = false, recentAccuracy = 0.7)
        val providers = listOf(
            ContextualMathProvider(),
            VocabularyReadingProvider(),
            ExpandedLogicProvider()
        )
        providers.forEach { provider ->
            repeat(30) { i ->
                val task = provider.generate(profile, Random(i * 7 + 1), null)
                assertTrue(task.acceptedAnswers.isNotEmpty(), "empty answers in ${provider::class.simpleName}")
                assertTrue(task.matches(task.acceptedAnswers.first()), "answer mismatch: ${task.prompt}")
                if (task.inputType == AnswerInputType.CHOICE) {
                    val labels = task.choices.map { Task.normalize(it.label) }
                    assertTrue(
                        task.acceptedAnswers.any { Task.normalize(it) in labels },
                        "choice answer not in options: ${task.prompt} / ${task.acceptedAnswers} / $labels"
                    )
                }
            }
        }
    }

    @Test
    fun repeatAvoidanceTracksRecentFingerprints() {
        val buffer = RepeatAvoidance(capacity = 3)
        buffer.markSeen("a")
        buffer.markSeen("b")
        buffer.markSeen("c")
        buffer.markSeen("d")
        assertTrue(!buffer.isRecent("a"))
        assertTrue(buffer.isRecent("d"))

        val task = Task(
            id = "x",
            category = TaskCategory.MATH,
            prompt = "Сколько будет 2 + 2?",
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf("4"),
            difficultyLevel = 1,
            skillTag = SkillTag.MATH_ADD
        )
        buffer.clear()
        buffer.markSeen(task)
        assertTrue(buffer.isRecent(task))
        assertEquals(
            RepeatAvoidance.fingerprint(task),
            "${SkillTag.MATH_ADD}|сколько будет # + #?"
        )
    }

    @Test
    fun logicProviderCoversAllSkills() {
        val provider = ExpandedLogicProvider()
        val profile = DifficultyProfile(12, 0, forceEasier = false, recentAccuracy = 0.9)
        val skills = SkillTag.entries.filter { it.category == TaskCategory.LOGIC }
        skills.forEach { skill ->
            val task = provider.generate(profile, Random(3), skill)
            assertEquals(skill, task.skillTag)
        }
    }

    @Test
    fun mathChoiceTaskAnswerIsAmongChoices() {
        val provider = ContextualMathProvider()
        val profile = DifficultyProfile(12, 0, forceEasier = false, recentAccuracy = 0.9)
        var found = false
        repeat(80) { i ->
            val task = provider.generate(profile, Random(i), SkillTag.MATH_MIX)
            if (task.inputType == AnswerInputType.CHOICE) {
                found = true
                assertTrue(
                    task.choices.any {
                        Task.normalize(it.label) == Task.normalize(task.acceptedAnswers.first())
                    },
                    "choice mismatch: ${task.prompt}"
                )
            }
        }
        assertTrue(found, "ожидалась хотя бы одна CHOICE задача MATH_MIX")
    }

    @Test
    fun mathTemplatesProduceDeterministicAnswers() {
        val provider = ContextualMathProvider()
        // возраст 11 ≈ 4+ класс: все MATH-навыки из AgeCurriculum допустимы
        val profile = DifficultyProfile(11, 0, forceEasier = false, recentAccuracy = 0.7)
        SkillTag.entries
            .filter { it.category == TaskCategory.MATH }
            .filter { AgeCurriculum.isSkillAllowed(profile.ageYears, it) }
            .forEach { skill ->
                repeat(15) { i ->
                    val task = provider.generate(profile, Random(100 + i * 13 + skill.ordinal), skill)
                    assertEquals(skill, task.skillTag)
                    assertTrue(task.matches(task.acceptedAnswers.first()), task.prompt)
                }
            }
    }

    @Test
    fun engineGeneratesAllCategories() {
        val engine = TaskEngine(random = Random(5))
        var last: TaskCategory? = null
        val seen = (1..8).map {
            val task = engine.nextTask(TaskRequest(ageYears = 9, lastCategory = last))
            last = task.category
            task.category
        }.toSet()
        assertEquals(
            setOf(
                TaskCategory.MATH,
                TaskCategory.READING,
                TaskCategory.LOGIC,
                TaskCategory.ENGLISH
            ),
            seen
        )
    }
}

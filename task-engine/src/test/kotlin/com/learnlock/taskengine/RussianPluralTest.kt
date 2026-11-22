package com.learnlock.taskengine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RussianPluralTest {

    private val apple = CountedNoun("яблоко", "яблока", "яблок")
    private val pencil = CountedNoun("карандаш", "карандаша", "карандашей")

    @Test
    fun formUsesRussianCountRules() {
        assertEquals("яблоко", RussianPlural.form(1, apple))
        assertEquals("яблока", RussianPlural.form(2, apple))
        assertEquals("яблока", RussianPlural.form(3, apple))
        assertEquals("яблока", RussianPlural.form(4, apple))
        assertEquals("яблок", RussianPlural.form(5, apple))
        assertEquals("яблок", RussianPlural.form(11, apple))
        assertEquals("яблок", RussianPlural.form(14, apple))
        assertEquals("яблоко", RussianPlural.form(21, apple))
        assertEquals("яблока", RussianPlural.form(22, apple))
        assertEquals("яблок", RussianPlural.form(25, apple))
        assertEquals("карандашей", RussianPlural.form(0, pencil))
        assertEquals("карандаш", RussianPlural.form(31, pencil))
        assertEquals("человек", RussianPlural.form(1, TaskLexicon.person))
        assertEquals("человека", RussianPlural.form(2, TaskLexicon.person))
        assertEquals("человек", RussianPlural.form(5, TaskLexicon.person))
        assertEquals("рубль", RussianPlural.form(1, TaskLexicon.ruble))
        assertEquals("рубля", RussianPlural.form(2, TaskLexicon.ruble))
        assertEquals("рублей", RussianPlural.form(5, TaskLexicon.ruble))
    }

    @Test
    fun mathPromptsAvoidBrokenPluralEndings() {
        val provider = ContextualMathProvider()
        val profile = DifficultyProfile(8, 0, forceEasier = false, recentAccuracy = 0.7)
        val broken = listOf(
            Regex("""(?<!\d)1 яблок(?!\p{L})"""),
            Regex("""(?<!\d)1 карандашей(?!\p{L})"""),
            Regex("""(?<!\d)1 конфет(?!\p{L})"""),
            Regex("""(?<!\d)2 яблок(?!\p{L})"""),
            Regex("""(?<!\d)2 карандашей(?!\p{L})"""),
            Regex("""(?<!\d)21 яблок(?!\p{L})"""),
            Regex("""(?<!\d)22 яблок(?!\p{L})""")
        )
        repeat(120) { i ->
            val task = provider.generate(profile, Random(i * 17 + 3), null)
            broken.forEach { pattern ->
                assertFalse(
                    pattern.containsMatchIn(task.prompt),
                    "плохая форма в: ${task.prompt}"
                )
            }
        }
    }

    @Test
    fun femaleNamesGetFeminineVerbs() {
        val anya = TaskLexicon.people.first { it.nominative == "Аня" }
        assertEquals("она", anya.pronounHeShe)
        assertEquals("купила", anya.bought)
        assertEquals("отдала", anya.gave)
        val misha = TaskLexicon.people.first { it.nominative == "Миша" }
        assertEquals("он", misha.pronounHeShe)
        assertEquals("купил", misha.bought)
        assertEquals("отдал", misha.gave)
    }

    @Test
    fun logicWordProblemsUseCountableForms() {
        val provider = ExpandedLogicProvider()
        val profile = DifficultyProfile(10, 0, forceEasier = false, recentAccuracy = 0.8)
        repeat(80) { i ->
            val task = provider.generate(profile, Random(i * 11 + 2), SkillTag.LOGIC_WORD)
            assertFalse(Regex("""(?<!\d)1 яблок(?!\p{L})""").containsMatchIn(task.prompt), task.prompt)
            assertFalse(Regex("""(?<!\d)1 птиц(?!\p{L})""").containsMatchIn(task.prompt), task.prompt)
            assertFalse(Regex("""(?<!\d)2 яблок(?!\p{L})""").containsMatchIn(task.prompt), task.prompt)
            if ("старше" in task.prompt) {
                assertTrue(
                    task.choices.any { it.label == task.acceptedAnswers.first() },
                    task.prompt
                )
                assertTrue(
                    task.acceptedAnswers.first() in TaskLexicon.logicPeople.map { it.nominative },
                    "ожидалось имя в именительном: ${task.acceptedAnswers} / ${task.prompt}"
                )
            }
        }
    }

    @Test
    fun countedWithAdjectiveAgrees() {
        val ball = TaskLexicon.ball
        assertEquals("1 красный шар", RussianPlural.countedWithAdjective(1, "красный", "красных", ball))
        assertEquals("2 красных шара", RussianPlural.countedWithAdjective(2, "красный", "красных", ball))
        assertEquals("5 красных шаров", RussianPlural.countedWithAdjective(5, "красный", "красных", ball))
        assertEquals("11 красных шаров", RussianPlural.countedWithAdjective(11, "красный", "красных", ball))
    }

    @Test
    fun templateRendersInflectedItemForms() {
        val out = TemplateEngine.render(
            "У {name} {count} {item_a}. Сколько всего {item}?",
            mapOf(
                "name" to "Миши",
                "count" to "1",
                "item_a" to RussianPlural.form(1, apple),
                "item" to RussianPlural.howMany(apple)
            )
        )
        assertEquals("У Миши 1 яблоко. Сколько всего яблок?", out)
    }
}

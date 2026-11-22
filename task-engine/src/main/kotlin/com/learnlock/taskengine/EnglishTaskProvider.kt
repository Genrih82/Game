package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Генератор заданий [TaskCategory.ENGLISH]:
 * словарь EN↔RU, Past Simple неправильных глаголов, три формы.
 */
class EnglishTaskProvider : TaskProvider {
    override val category: TaskCategory = TaskCategory.ENGLISH

    override fun generate(
        profile: DifficultyProfile,
        random: Random,
        preferredSkill: SkillTag?
    ): Task {
        val skill = AgeCurriculum.clampSkill(profile.ageYears, preferredSkill, category)
            ?: pickSkill(profile.ageYears, random)
        return when (skill) {
            SkillTag.ENG_VOCAB -> vocabTask(profile.ageYears, profile.level(), random)
            SkillTag.ENG_IRREGULAR -> irregularPast(profile.ageYears, profile.level(), random)
            SkillTag.ENG_THREE_FORMS -> threeForms(profile.ageYears, profile.level(), random)
            else -> vocabTask(profile.ageYears, profile.level(), random)
        }
    }

    private fun pickSkill(ageYears: Int, random: Random): SkillTag {
        val allowed = AgeCurriculum.allowedSkills(ageYears)
            .filter { it.category == category }
        return allowed.random(random)
    }

    private fun vocabTask(ageYears: Int, level: Int, random: Random): Task {
        val pool = EnglishVocabulary.vocabForAge(ageYears)
            .ifEmpty { EnglishVocabulary.vocab }
        val entry = pool.random(random)
        val toRussian = random.nextBoolean()
        val prompt = if (toRussian) {
            "Как переводится «${entry.en}»?"
        } else {
            "Как по-английски: «${entry.ru}»?"
        }
        val answer = if (toRussian) entry.ru else entry.en
        val distractorPool = pool.filter { it.en != entry.en }
            .shuffled(random)
            .take(8)
        val wrong = if (toRussian) {
            distractorPool.map { it.ru }.distinct().filter { Task.normalize(it) != Task.normalize(answer) }
        } else {
            distractorPool.map { it.en }.distinct().filter { Task.normalize(it) != Task.normalize(answer) }
        }.take(3)
        val options = ensureStringChoices(answer, wrong, random)
        val soft = "Вспомни знакомое слово"
        val main = if (toRussian) "Выбери русский перевод" else "Выбери английское слово"
        return Task(
            id = "eng-vocab-${entry.en}-$toRussian",
            category = category,
            prompt = prompt,
            inputType = AnswerInputType.CHOICE,
            choices = options.mapIndexed { i, label -> TaskChoice(i.toString(), label) },
            acceptedAnswers = listOf(answer),
            hint = main,
            hints = Task.buildHints(soft, main),
            difficultyLevel = level,
            skillTag = SkillTag.ENG_VOCAB
        )
    }

    private fun irregularPast(ageYears: Int, level: Int, random: Random): Task {
        val pool = EnglishVocabulary.irregularForAge(ageYears)
            .ifEmpty { EnglishVocabulary.irregularVerbs.filter { it.band == 1 } }
        val verb = pool.random(random)
        val askRu = random.nextBoolean()
        val prompt = if (askRu) {
            "Past Simple глагола «${verb.base}» (${verb.ru})?"
        } else {
            "Какая форма Past Simple у глагола «${verb.base}»?"
        }
        val answer = verb.past
        val wrong = pool
            .filter { it.base != verb.base }
            .map { it.past }
            .distinct()
            .filter { Task.normalize(it) != Task.normalize(answer) }
            .shuffled(random)
            .take(3)
        val options = ensureStringChoices(answer, wrong, random)
        val soft = "Это неправильный глагол — форма не на -ed"
        val main = "Вспомни: ${verb.base} → ${verb.past}"
        return Task(
            id = "eng-irr-past-${verb.base}",
            category = category,
            prompt = prompt,
            inputType = AnswerInputType.CHOICE,
            choices = options.mapIndexed { i, label -> TaskChoice(i.toString(), label) },
            acceptedAnswers = listOf(answer),
            hint = soft,
            hints = Task.buildHints(soft, "Форма прошедшего времени: ${verb.past}"),
            difficultyLevel = level,
            skillTag = SkillTag.ENG_IRREGULAR
        )
    }

    private fun threeForms(ageYears: Int, level: Int, random: Random): Task {
        val pool = EnglishVocabulary.irregularForAge(ageYears)
            .ifEmpty { EnglishVocabulary.irregularVerbs.filter { it.band <= 2 } }
        val verb = pool.random(random)
        val correct = "${verb.base} – ${verb.past} – ${verb.participle}"
        val distractors = pool.filter { it.base != verb.base }.shuffled(random).take(6).map { other ->
            // типичные ошибки: перепутать past/pp или взять чужой past
            when (random.nextInt(3)) {
                0 -> "${verb.base} – ${other.past} – ${verb.participle}"
                1 -> "${verb.base} – ${verb.past} – ${other.participle}"
                else -> "${verb.base} – ${verb.participle} – ${verb.past}"
            }
        }.filter { Task.normalize(it) != Task.normalize(correct) }.distinct().take(3)
        val options = ensureStringChoices(correct, distractors, random)
        val soft = "Нужны три формы: Infinitive / Past / Past Participle"
        val main = "Правильный ряд: ${verb.base}, ${verb.past}, ${verb.participle}"
        return Task(
            id = "eng-3forms-${verb.base}",
            category = category,
            prompt = "Выбери верные три формы глагола «${verb.base}» (${verb.ru})",
            inputType = AnswerInputType.CHOICE,
            choices = options.mapIndexed { i, label -> TaskChoice(i.toString(), label) },
            acceptedAnswers = listOf(correct),
            hint = soft,
            hints = Task.buildHints(soft, main),
            difficultyLevel = level,
            skillTag = SkillTag.ENG_THREE_FORMS
        )
    }

    private fun ensureStringChoices(answer: String, wrong: List<String>, random: Random): List<String> {
        val fallback = listOf("went", "seen", "made", "took", "came", "said", "got", "had", "did", "was")
        val pool = wrong
            .filter { Task.normalize(it) != Task.normalize(answer) }
            .distinct()
            .toMutableList()
        for (f in fallback.shuffled(random)) {
            if (pool.size >= 3) break
            if (Task.normalize(f) != Task.normalize(answer) && f !in pool) pool.add(f)
        }
        return (pool.take(3) + answer).distinct().shuffled(random)
    }
}

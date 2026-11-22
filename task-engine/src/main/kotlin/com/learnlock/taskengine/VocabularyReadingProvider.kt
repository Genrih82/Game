package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Генератор задач [TaskCategory.READING]:
 * пропущенная буква ([SchoolVocabulary]), «лишнее слово» из [VocabularyDatabase], синонимы.
 *
 * Использует [RepeatAvoidance] для снижения повторяющихся формулировок.
 */
class VocabularyReadingProvider(
    private val vocab: VocabularyDatabase = VocabularyDatabase(),
    private val repeatAvoidance: RepeatAvoidance = RepeatAvoidance()
) : TaskProvider {
    override val category: TaskCategory = TaskCategory.READING

    private val synonyms = listOf(
        Triple("большой", listOf("огромный", "маленький", "тихий", "новый"), "огромный"),
        Triple("грустный", listOf("печальный", "весёлый", "громкий", "острый"), "печальный"),
        Triple("быстрый", listOf("скорый", "медленный", "мягкий", "длинный"), "скорый"),
        Triple("умный", listOf("разумный", "глупый", "сырой", "узкий"), "разумный"),
        Triple("храбрый", listOf("смелый", "трусливый", "круглый", "кислый"), "смелый"),
        Triple("аккуратно", listOf("тщательно", "небрежно", "громко", "поздно"), "тщательно"),
        Triple("интересный", listOf("занимательный", "скучный", "мокрый", "острый"), "занимательный"),
        Triple("прекрасный", listOf("замечательный", "плохой", "тяжёлый", "пустой"), "замечательный"),
        Triple("медленно", listOf("неторопливо", "быстро", "ярко", "сухо"), "неторопливо"),
        Triple("приветливо", listOf("дружелюбно", "злобно", "тихо", "узко"), "дружелюбно"),
        Triple("смелый", listOf("храбрый", "робкий", "тихий", "узкий"), "храбрый"),
        Triple("красивый", listOf("прекрасный", "уродливый", "громкий", "острый"), "прекрасный")
    )

    private val oddHintByCategory = mapOf(
        "animal" to "Три слова — животные",
        "food" to "Три слова — еда",
        "school" to "Три слова — школа/учёба",
        "place" to "Три слова — места",
        "nature" to "Три слова — природа",
        "clothes" to "Три слова — одежда",
        "family" to "Три слова — семья",
        "color" to "Три слова — цвета",
        "sport" to "Три слова — спорт",
        "transport" to "Три слова — транспорт",
        "abstract" to "Три слова — отвлечённые понятия",
        "science" to "Три слова — наука",
        "logic" to "Три слова — логика",
        "tech" to "Три слова — технологии",
        "society" to "Три слова — общество"
    )

    override fun generate(
        profile: DifficultyProfile,
        random: Random,
        preferredSkill: SkillTag?
    ): Task {
        var task = generateOnce(profile, random, preferredSkill)
        var attempts = 0
        while (repeatAvoidance.isRecent(task) && attempts < 16) {
            task = generateOnce(profile, random, preferredSkill)
            attempts++
        }
        repeatAvoidance.markSeen(task)
        return task
    }

    /** Одна генерация без ретраев по буферу повторов. */
    private fun generateOnce(
        profile: DifficultyProfile,
        random: Random,
        preferredSkill: SkillTag?
    ): Task {
        val level = profile.level()
        val skill = preferredSkill?.takeIf { it.category == category }
        return if (skill != null) {
            when (skill) {
                SkillTag.READ_LETTER -> missingLetter(profile, level, random)
                SkillTag.READ_ODD -> oddOne(level, random)
                SkillTag.READ_SYNONYM -> synonym(level, random)
                else -> byLevel(profile, level, random)
            }
        } else byLevel(profile, level, random)
    }

    /** Выбор типа задания по уровню, если навык не зафиксирован. */
    private fun byLevel(profile: DifficultyProfile, level: Int, random: Random): Task = when {
        level <= 3 -> missingLetter(profile, level, random)
        level <= 6 -> oddOne(level, random)
        else -> synonym(level, random)
    }

    /** Задание: угадать пропущенную букву в словарном слове. */
    private fun missingLetter(profile: DifficultyProfile, level: Int, random: Random): Task {
        val pool = SchoolVocabulary.wordItemsForAge(profile.ageYears)
            .ifEmpty { SchoolVocabulary.wordItemsForAge(8) }
        val item = pool.random(random)
        val chars = item.word.toCharArray()
        val missing = chars[item.missingIndex]
        chars[item.missingIndex] = '_'
        val promptWord = chars.concatToString()
        val distractors = ('А'..'Я').filter { it != missing }.shuffled(random).take(3)
        val options = (distractors + missing).map { it.toString() }.shuffled(random)
        return Task(
            id = "read-miss-${item.word}",
            category = category,
            prompt = TextVariator(random).pick(listOf(
                "Какая буква пропущена: $promptWord?",
                "Вставь пропущенную букву: $promptWord",
                "Какая буква стоит на месте пропуска: $promptWord?"
            )),
            inputType = AnswerInputType.CHOICE,
            choices = options.mapIndexed { index, label -> TaskChoice(index.toString(), label) },
            acceptedAnswers = listOf(missing.toString()),
            hint = "Это словарное слово — произнеси и вспомни написание",
            difficultyLevel = level,
            skillTag = SkillTag.READ_LETTER
        )
    }

    /** Соответствие числового уровня файлу словаря [VocabularyDatabase]. */
    private fun levelForVocab(level: Int): VocabularyDatabase.Level = when {
        level <= 3 -> VocabularyDatabase.Level.EASY
        level <= 6 -> VocabularyDatabase.Level.MEDIUM
        else -> VocabularyDatabase.Level.HARD
    }

    /**
     * «Лишнее слово»: три слова одной категории из БД и одно из другой.
     */
    private fun oddOne(level: Int, random: Random): Task {
        val vLevel = levelForVocab(level)
        val cats = vocab.categories(vLevel).toList()
            .filter { vocab.words(vLevel, it).size >= 4 }
        if (cats.isEmpty()) return oddOneFallback(level, random)

        val baseCat = cats.random(random)
        val oddCat = cats.filter { it != baseCat }.random(random)
        val baseWords = vocab.pickDistinct(vLevel, baseCat, 3, random)
        val oddWord = vocab.randomWord(vLevel, oddCat, random) ?: return oddOneFallback(level, random)

        val words = (baseWords + oddWord).shuffled(random)
        return Task(
            id = "read-odd-$baseCat-$oddCat-$oddWord",
            category = category,
            prompt = "${TextVariator(random).askOdd()}?\n${words.joinToString(", ")}",
            inputType = AnswerInputType.CHOICE,
            choices = words.mapIndexed { index, label -> TaskChoice(index.toString(), label) },
            acceptedAnswers = listOf(oddWord),
            hint = oddHintByCategory[baseCat] ?: "Три слова из одной категории, одно — нет",
            difficultyLevel = level,
            skillTag = SkillTag.READ_ODD
        )
    }

    /** Резервный набор «лишнее слово», если словарь недоступен или пуст. */
    private fun oddOneFallback(level: Int, random: Random): Task {
        val groups = listOf(
            Triple(listOf("яблоко", "ягода", "малина"), "топор", "Три слова — плоды/ягоды"),
            Triple(listOf("собака", "корова", "лягушка"), "тетрадь", "Три слова — животные"),
            Triple(listOf("тетрадь", "пенал", "карандаш"), "морковь", "Три слова — школьные предметы"),
            Triple(listOf("январь", "февраль", "сентябрь"), "улица", "Три слова — месяцы"),
            Triple(listOf("понедельник", "вторник", "пятница"), "магазин", "Три слова — дни недели")
        )
        val (base, odd, hint) = groups.random(random)
        val words = (base + odd).shuffled(random)
        return Task(
            id = "read-odd-fb-$odd",
            category = category,
            prompt = "${TextVariator(random).askOdd()}?\n${words.joinToString(", ")}",
            inputType = AnswerInputType.CHOICE,
            choices = words.mapIndexed { index, label -> TaskChoice(index.toString(), label) },
            acceptedAnswers = listOf(odd),
            hint = hint,
            difficultyLevel = level,
            skillTag = SkillTag.READ_ODD
        )
    }

    /** Выбор синонима из заранее заданной тройки слов. */
    private fun synonym(level: Int, random: Random): Task {
        val (word, options, answer) = synonyms.random(random)
        return Task(
            id = "read-syn-$word",
            category = category,
            prompt = "Выбери синоним к слову «$word»",
            inputType = AnswerInputType.CHOICE,
            choices = options.mapIndexed { index, label -> TaskChoice(index.toString(), label) },
            acceptedAnswers = listOf(answer),
            hint = "Синоним — слово с похожим смыслом",
            difficultyLevel = level,
            skillTag = SkillTag.READ_SYNONYM
        )
    }
}
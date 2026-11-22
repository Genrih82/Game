package com.learnlock.taskengine

/** Категория образовательной задачи. */
enum class TaskCategory {
    MATH,
    READING,
    LOGIC,
    ENGLISH;

    /** Название категории для UI. */
    fun displayNameRu(): String = when (this) {
        MATH -> "Математика"
        READING -> "Чтение"
        LOGIC -> "Логика"
        ENGLISH -> "Английский"
    }
}

/**
 * Конкретный навык внутри категории (тип задания для статистики и шаблонов).
 */
enum class SkillTag {
    MATH_ADD,
    MATH_SUB,
    MATH_MUL,
    MATH_DIV,
    MATH_COMPARE,
    MATH_MIX,
    READ_LETTER,
    READ_ODD,
    READ_SYNONYM,
    LOGIC_SEQ,
    LOGIC_PATTERN,
    LOGIC_WORD,
    LOGIC_ANALOGY,
    ENG_VOCAB,
    ENG_IRREGULAR,
    ENG_THREE_FORMS;

    /** Категория, к которой относится навык. */
    val category: TaskCategory
        get() = when (this) {
            MATH_ADD, MATH_SUB, MATH_MUL, MATH_DIV, MATH_COMPARE, MATH_MIX -> TaskCategory.MATH
            READ_LETTER, READ_ODD, READ_SYNONYM -> TaskCategory.READING
            LOGIC_SEQ, LOGIC_PATTERN, LOGIC_WORD, LOGIC_ANALOGY -> TaskCategory.LOGIC
            ENG_VOCAB, ENG_IRREGULAR, ENG_THREE_FORMS -> TaskCategory.ENGLISH
        }

    /** Локализованное краткое название навыка для UI. */
    fun displayNameRu(): String = when (this) {
        MATH_ADD -> "Сложение"
        MATH_SUB -> "Вычитание"
        MATH_MUL -> "Умножение"
        MATH_DIV -> "Деление"
        MATH_COMPARE -> "Сравнение чисел"
        MATH_MIX -> "Смешанные примеры"
        READ_LETTER -> "Пропущенная буква"
        READ_ODD -> "Лишнее слово"
        READ_SYNONYM -> "Синонимы"
        LOGIC_SEQ -> "Последовательности"
        LOGIC_PATTERN -> "Узоры"
        LOGIC_WORD -> "Текстовые задачи"
        LOGIC_ANALOGY -> "Аналогии"
        ENG_VOCAB -> "Словарь EN"
        ENG_IRREGULAR -> "Неправильные глаголы"
        ENG_THREE_FORMS -> "Три формы глагола"
    }
}

/** Способ ввода ответа пользователем. */
enum class AnswerInputType {
    NUMBER,
    CHOICE,
    TEXT
}

/**
 * Вариант ответа для заданий с выбором.
 *
 * @property id стабильный идентификатор варианта
 * @property label текст на кнопке/в списке
 */
data class TaskChoice(
    val id: String,
    val label: String
)

/**
 * Одна сгенерированная задача.
 *
 * @property acceptedAnswers допустимые ответы после нормализации; для NUMBER/TEXT — числа/строки
 * @property hint краткая подсказка (совместимость); при пустом [hints] используется как единственный шаг
 * @property hints ступени подсказки: от мягкой к более прямой
 */
data class Task(
    val id: String,
    val category: TaskCategory,
    val prompt: String,
    val inputType: AnswerInputType,
    val choices: List<TaskChoice> = emptyList(),
    val acceptedAnswers: List<String>,
    val hint: String? = null,
    val hints: List<String> = emptyList(),
    val difficultyLevel: Int,
    val skillTag: SkillTag
) {
    /**
     * Ступени подсказки для UI: [hints], иначе одна [hint], иначе пусто.
     */
    fun hintSteps(): List<String> = hints.filter { it.isNotBlank() }
        .ifEmpty { listOfNotNull(hint?.takeIf { it.isNotBlank() }) }

    /**
     * Проверяет ответ пользователя без учёта регистра и с заменой запятой на точку.
     *
     * @param rawAnswer введённый или выбранный ответ
     * @return `true`, если ответ совпадает с одним из [acceptedAnswers] после нормализации
     */
    fun matches(rawAnswer: String): Boolean {
        val normalized = normalize(rawAnswer)
        return acceptedAnswers.any { normalize(it) == normalized }
    }

    companion object {
        /** Общая нормализация строковых ответов для сравнения. */
        fun normalize(value: String): String =
            value.trim().lowercase().replace(',', '.')

        /**
         * Собирает ступени подсказки: мягкая → основная → при необходимости усиленная.
         * Пустые строки отбрасываются; дубликаты подряд схлопываются.
         */
        fun buildHints(soft: String?, main: String?, strong: String? = null): List<String> {
            val steps = listOfNotNull(
                soft?.takeIf { it.isNotBlank() },
                main?.takeIf { it.isNotBlank() },
                strong?.takeIf { it.isNotBlank() }
            )
            if (steps.isEmpty()) return emptyList()
            return steps.fold(mutableListOf()) { acc, step ->
                if (acc.lastOrNull() != step) acc.add(step)
                acc
            }
        }
    }
}

/**
 * Одна попытка решения задачи для агрегации статистики.
 *
 * @property correct признак верного ответа
 */
data class AttemptRecord(
    val skillTag: SkillTag,
    val category: TaskCategory,
    val correct: Boolean
)

/**
 * Агрегированная статистика ученика по попыткам.
 *
 * @property overallAccuracy доля верных ответов по всем попыткам (0..1)
 * @property categoryAccuracy точность по категориям
 * @property skillAccuracy точность по навыкам
 * @property skillAttempts число попыток по каждому навыку
 * @property skillRecency сколько попыток назад навык встречался (0 — только что; нет в истории — отсутствует в карте)
 */
data class LearnerStats(
    val overallAccuracy: Double = 0.7,
    val categoryAccuracy: Map<TaskCategory, Double> = emptyMap(),
    val skillAccuracy: Map<SkillTag, Double> = emptyMap(),
    val skillAttempts: Map<SkillTag, Int> = emptyMap(),
    val skillRecency: Map<SkillTag, Int> = emptyMap()
) {
    /**
     * Навыки с достаточным числом попыток и точностью ниже порога.
     *
     * @param minAttempts минимум попыток для учёта навыка
     * @param threshold верхняя граница «слабости» по точности
     */
    fun weakSkills(minAttempts: Int = 3, threshold: Double = 0.55): List<SkillTag> =
        skillAccuracy
            .filter { (skill, acc) -> (skillAttempts[skill] ?: 0) >= minAttempts && acc < threshold }
            .entries
            .sortedBy { it.value }
            .map { it.key }

    /**
     * Навыки с достаточным числом попыток и точностью не ниже порога.
     */
    fun strongSkills(minAttempts: Int = 3, threshold: Double = 0.85): List<SkillTag> =
        skillAccuracy
            .filter { (skill, acc) -> (skillAttempts[skill] ?: 0) >= minAttempts && acc >= threshold }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

    /**
     * Навыки из [candidates], которые давно не встречались в недавней истории.
     *
     * @param window порог «давности» в числе попыток
     */
    fun staleSkills(candidates: Collection<SkillTag>, window: Int = 8): List<SkillTag> =
        candidates
            .filter { skill -> (skillRecency[skill] ?: Int.MAX_VALUE) >= window }
            .sortedByDescending { skillRecency[it] ?: Int.MAX_VALUE }

    companion object {
        /**
         * Собирает [LearnerStats] из списка попыток.
         * Порядок [attempts]: от более новых к более старым (как в DAO recent*).
         *
         * @param attempts история попыток; пустой список даёт значения по умолчанию
         */
        fun fromAttempts(attempts: List<AttemptRecord>): LearnerStats {
            if (attempts.isEmpty()) return LearnerStats()
            val overall = attempts.count { it.correct }.toDouble() / attempts.size
            val categoryAccuracy = attempts
                .groupBy { it.category }
                .mapValues { (_, list) -> list.count { it.correct }.toDouble() / list.size }
            val bySkill = attempts.groupBy { it.skillTag }
            val skillAccuracy = bySkill.mapValues { (_, list) ->
                list.count { it.correct }.toDouble() / list.size
            }
            val skillAttempts = bySkill.mapValues { it.value.size }
            val skillRecency = linkedMapOf<SkillTag, Int>()
            attempts.forEachIndexed { index, record ->
                skillRecency.putIfAbsent(record.skillTag, index)
            }
            return LearnerStats(
                overallAccuracy = overall,
                categoryAccuracy = categoryAccuracy,
                skillAccuracy = skillAccuracy,
                skillAttempts = skillAttempts,
                skillRecency = skillRecency
            )
        }
    }
}

/**
 * Входные параметры для выдачи одной задачи.
 *
 * @property learnerStats опциональная статистика для [AdaptiveSelector]; `null` — без адаптации
 */
data class TaskRequest(
    val ageYears: Int,
    val difficultyOffset: Int = 0,
    val enabledCategories: Set<TaskCategory> = TaskCategory.entries.toSet(),
    val categoryWeights: Map<TaskCategory, Int> = mapOf(
        TaskCategory.MATH to 1,
        TaskCategory.READING to 1,
        TaskCategory.LOGIC to 1,
        TaskCategory.ENGLISH to 1
    ),
    val recentAccuracy: Double = 0.7,
    val forceEasier: Boolean = false,
    val preferredCategory: TaskCategory? = null,
    val preferredSkill: SkillTag? = null,
    val learnerStats: LearnerStats? = null,
    /** Категория предыдущей задачи в серии — для [CategoryRotator]. */
    val lastCategory: TaskCategory? = null
)

/**
 * Параметры расчёта уровня сложности (полоса 1..10).
 *
 * @property offset ручное смещение от базового уровня по возрасту
 * @property forceEasier при `true` сдвигает уровень вниз
 */
data class DifficultyProfile(
    val ageYears: Int,
    val offset: Int,
    val forceEasier: Boolean,
    val recentAccuracy: Double
) {
    /**
     * Эффективный уровень сложности с учётом возраста, точности и флагов.
     *
     * @return целое значение в диапазоне 1..10
     */
    fun level(): Int {
        // Более плавная шкала: младшие — сложение/простые тексты; старше — mix и умножение.
        val base = when {
            ageYears <= 5 -> 1
            ageYears == 6 -> 2
            ageYears == 7 -> 3
            ageYears == 8 -> 4
            ageYears == 9 -> 5
            ageYears == 10 -> 6
            ageYears == 11 -> 7
            ageYears == 12 -> 8
            ageYears == 13 -> 9
            else -> 10
        }
        val accuracyAdjust = when {
            recentAccuracy < 0.4 -> -1
            recentAccuracy > 0.85 -> 1
            else -> 0
        }
        val easier = if (forceEasier) -2 else 0
        return (base + offset + accuracyAdjust + easier).coerceIn(1, 10)
    }
}
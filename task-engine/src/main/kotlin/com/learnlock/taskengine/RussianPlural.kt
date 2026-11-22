package com.learnlock.taskengine

/**
 * Согласование существительного с числительным в русском
 * (1 яблоко / 2 яблока / 5 яблок, 11–14 → мн.).
 */
object RussianPlural {
    /**
     * Выбирает форму по числу.
     *
     * @param n неотрицательное целое
     * @param one именительный ед. ч. (1, 21, …)
     * @param few родительный ед. ч. (2–4, 22–24, …)
     * @param many родительный мн. ч. (0, 5–20, 11–14, …)
     */
    fun form(n: Int, one: String, few: String, many: String): String {
        val abs = kotlin.math.abs(n)
        val mod100 = abs % 100
        if (mod100 in 11..14) return many
        return when (mod100 % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }

    fun form(n: Int, noun: CountedNoun): String = form(n, noun.one, noun.few, noun.many)

    /** Форма для «сколько …?» — обычно родительный мн. */
    fun howMany(noun: CountedNoun): String = noun.many

    /**
     * Фраза «N + прилагательное + существительное» для цветов и т.п.
     * Пример: 1 красный шар / 2 красных шара / 5 красных шаров.
     */
    fun countedWithAdjective(n: Int, adjOne: String, adjMany: String, noun: CountedNoun): String {
        val adj = form(n, adjOne, adjMany, adjMany)
        return "$n $adj ${form(n, noun)}"
    }
}

/**
 * Три счётные формы существительного.
 *
 * @property one 1, 21, 31…
 * @property few 2–4, 22–24…
 * @property many 0, 5–20, 11–14…
 */
data class CountedNoun(
    val one: String,
    val few: String,
    val many: String
)

/** Имя персонажа с родом для согласования глаголов. */
data class PersonName(
    val nominative: String,
    val genitive: String,
    val female: Boolean
) {
    val pronounHeShe: String get() = if (female) "она" else "он"
    val pronounHeSheCap: String get() = if (female) "Она" else "Он"
    val bought: String get() = if (female) "купила" else "купил"
    val gave: String get() = if (female) "отдала" else "отдал"
}

/** Общий словарь предметов и имён для текстовых задач. */
object TaskLexicon {
    val items: List<CountedNoun> = listOf(
        CountedNoun("яблоко", "яблока", "яблок"),
        CountedNoun("груша", "груши", "груш"),
        CountedNoun("конфета", "конфеты", "конфет"),
        CountedNoun("карандаш", "карандаша", "карандашей"),
        CountedNoun("наклейка", "наклейки", "наклеек"),
        CountedNoun("шар", "шара", "шаров"),
        CountedNoun("машинка", "машинки", "машинок"),
        CountedNoun("книга", "книги", "книг"),
        CountedNoun("мяч", "мяча", "мячей"),
        CountedNoun("печенье", "печенья", "печений"),
        CountedNoun("марка", "марки", "марок"),
        CountedNoun("открытка", "открытки", "открыток")
    )

    val logicItems: List<CountedNoun> = listOf(
        CountedNoun("яблоко", "яблока", "яблок"),
        CountedNoun("карандаш", "карандаша", "карандашей"),
        CountedNoun("наклейка", "наклейки", "наклеек"),
        CountedNoun("конфета", "конфеты", "конфет"),
        CountedNoun("марка", "марки", "марок"),
        CountedNoun("открытка", "открытки", "открыток")
    )

    val bird = CountedNoun("птица", "птицы", "птиц")
    val pupil = CountedNoun("ученик", "ученика", "учеников")
    val friend = CountedNoun("друг", "друга", "друзей")
    val pack = CountedNoun("пачка", "пачки", "пачек")
    val row = CountedNoun("ряд", "ряда", "рядов")
    val book = CountedNoun("книга", "книги", "книг")
    val person = CountedNoun("человек", "человека", "человек")
    val ruble = CountedNoun("рубль", "рубля", "рублей")
    val ball = CountedNoun("шар", "шара", "шаров")

    val people: List<PersonName> = listOf(
        PersonName("Миша", "Миши", false),
        PersonName("Петя", "Пети", false),
        PersonName("Аня", "Ани", true),
        PersonName("Игорь", "Игоря", false),
        PersonName("Серёжа", "Серёжи", false),
        PersonName("Оля", "Оли", true),
        PersonName("Катя", "Кати", true),
        PersonName("Дима", "Димы", false),
        PersonName("Лена", "Лены", true),
        PersonName("Витя", "Вити", false),
        PersonName("Саша", "Саши", false),
        PersonName("Настя", "Насти", true)
    )

    val logicPeople: List<PersonName> = listOf(
        PersonName("Маша", "Маши", true),
        PersonName("Петя", "Пети", false),
        PersonName("Аня", "Ани", true),
        PersonName("Игорь", "Игоря", false),
        PersonName("Серёжа", "Серёжи", false),
        PersonName("Оля", "Оли", true),
        PersonName("Катя", "Кати", true),
        PersonName("Дима", "Димы", false)
    )
}

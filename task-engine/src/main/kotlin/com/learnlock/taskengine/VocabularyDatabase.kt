package com.learnlock.taskengine

/**
 * Загружает тематический словарь из ресурсов `vocabulary/words_{level}.txt`.
 *
 * Формат строки: `category|word1|word2|...`
 * Данные кэшируются по уровню — типичный объём в памяти менее 1 МБ.
 */
class VocabularyDatabase {

    /** Уровень лексической сложности файла словаря. */
    enum class Level { EASY, MEDIUM, HARD }

    private val cache = mutableMapOf<Level, Map<String, List<String>>>()

    /**
     * Слова категории для уровня; отсутствующая категория — пустой список.
     *
     * @param level уровень словаря
     * @param category ключ категории (например, `animal`)
     */
    fun words(level: Level, category: String): List<String> =
        data(level)[category].orEmpty()

    /** Уникальные слова категории (порядок из файла сохраняется по первому вхождению). */
    fun distinctWords(level: Level, category: String): List<String> =
        words(level, category).distinct()

    /** Множество имён категорий, присутствующих в файле уровня. */
    fun categories(level: Level): Set<String> = data(level).keys

    /**
     * Случайное слово из категории или `null`, если список пуст.
     */
    fun randomWord(level: Level, category: String, random: kotlin.random.Random): String? =
        distinctWords(level, category).randomOrNull(random)

    /**
     * До [count] различных слов из категории (перемешивание без повторов в выборке).
     */
    fun pickDistinct(level: Level, category: String, count: Int, random: kotlin.random.Random): List<String> =
        distinctWords(level, category).shuffled(random).take(count)

    /** Ленивая загрузка и кэширование карты категория → слова. */
    private fun data(level: Level): Map<String, List<String>> =
        cache.getOrPut(level) { load(level) }

    /** Парсинг текстового ресурса уровня в карту категорий. */
    private fun load(level: Level): Map<String, List<String>> {
        val path = "/vocabulary/words_${level.name.lowercase()}.txt"
        val stream = VocabularyDatabase::class.java.getResourceAsStream(path)
            ?: return emptyMap()
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.split('|')
                    if (parts.size < 2) return@mapNotNull null
                    parts[0].trim() to parts.drop(1).map { it.trim() }.filter { it.isNotEmpty() }
                }
                .toMap()
        }
    }
}
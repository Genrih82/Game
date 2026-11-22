package com.learnlock.taskengine

import java.util.ArrayDeque

/**
 * Кольцевой буфер недавно выданных задач по отпечатку содержания
 * (навык + нормализованный фрагмент prompt), а не по случайному [Task.id].
 *
 * @param capacity максимальное число хранимых отпечатков
 */
class RepeatAvoidance(private val capacity: Int = 80) {

    private val recent = ArrayDeque<String>(capacity)

    /**
     * Проверяет, встречался ли отпечаток в недавней истории.
     *
     * @param key строка отпечатка
     */
    @Synchronized
    fun isRecent(key: String): Boolean = key in recent

    /** То же для задачи: [fingerprint] вычисляется автоматически. */
    @Synchronized
    fun isRecent(task: Task): Boolean = isRecent(fingerprint(task))

    /**
     * Добавляет отпечаток в конец очереди; при переполнении удаляет самый старый.
     */
    @Synchronized
    fun markSeen(key: String) {
        recent.remove(key)
        recent.addLast(key)
        while (recent.size > capacity) recent.removeFirst()
    }

    /** Регистрирует задачу по её отпечатку. */
    @Synchronized
    fun markSeen(task: Task) = markSeen(fingerprint(task))

    /** Очищает историю повторов. */
    @Synchronized
    fun clear() = recent.clear()

    companion object {
        /**
         * Строит компактный ключ: навык и до 120 символов нормализованного текста условия.
         *
         * @param task задача для учёта в буфере
         */
        fun fingerprint(task: Task): String {
            // Цифры и имена схлопываем — «5 яблок у Миши» ≈ «7 яблок у Ани».
            val compact = Task.normalize(task.prompt)
                .replace(Regex("\\d+"), "#")
                .replace(
                    Regex(
                        "\\b(миши|пети|ани|игоря|серёжи|оли|кати|димы|лены|вити|саши|насти|маши)\\b"
                    ),
                    "#"
                )
                .replace(Regex("\\s+"), " ")
                .take(100)
            return "${task.skillTag}|$compact"
        }
    }
}
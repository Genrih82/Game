package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Сборка вариантов ответа с обязательным перемешиванием.
 * Верный ответ не должен систематически оказываться в первой кнопке.
 */
object TaskChoices {
    /**
     * Перемешивает подписи и нумерует id по новому порядку.
     */
    fun shuffled(labels: List<String>, random: Random): List<TaskChoice> =
        labels
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { Task.normalize(it) }
            .shuffled(random)
            .mapIndexed { index, label -> TaskChoice(index.toString(), label) }

    /**
     * Верный ответ + дистракторы; итог перемешан.
     * По умолчанию собирает ровно [total] кнопок (обычно 4).
     */
    fun withAnswer(
        answer: String,
        wrong: List<String>,
        random: Random,
        total: Int = 4
    ): List<TaskChoice> {
        require(total >= 2) { "total должен быть >= 2" }
        val normalizedAnswer = Task.normalize(answer)
        val pool = linkedSetOf<String>()
        wrong.map { it.trim() }
            .filter { it.isNotEmpty() && Task.normalize(it) != normalizedAnswer }
            .forEach { pool.add(it) }

        var n = 1
        val answerInt = answer.toIntOrNull()
        while (pool.size < total - 1) {
            val filler = if (answerInt != null) {
                (answerInt + n).toString()
            } else {
                "${answer}_$n"
            }
            if (Task.normalize(filler) != normalizedAnswer) pool.add(filler)
            n++
            if (n > 200) break
        }

        val distractors = pool.toList().shuffled(random).take(total - 1)
        val labels = distractors + answer
        check(labels.distinctBy { Task.normalize(it) }.size == total) {
            "ожидали $total вариантов, собрали ${labels.distinctBy { Task.normalize(it) }} (answer=$answer)"
        }
        return shuffled(labels, random)
    }

    /** Ровно [count] вариантов или ошибка — для числовых последовательностей. */
    fun exactly(labels: List<String>, random: Random, count: Int = 4): List<TaskChoice> {
        val unique = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { Task.normalize(it) }
        check(unique.size == count) { "нужно ровно $count вариантов, есть ${unique.size}: $unique" }
        return shuffled(unique, random)
    }
}

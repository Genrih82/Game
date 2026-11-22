package com.learnlock.taskengine

/**
 * Жёсткое чередование категорий среди включённых.
 * Порядок: математика → чтение → логика → английский → снова математика.
 */
object CategoryRotator {
    val order: List<TaskCategory> = listOf(
        TaskCategory.MATH,
        TaskCategory.READING,
        TaskCategory.LOGIC,
        TaskCategory.ENGLISH
    )

    /**
     * Следующая категория после [last] среди [enabled].
     * Если [last] неизвестна или выключена — берёт первую из круга.
     */
    fun next(enabled: Set<TaskCategory>, last: TaskCategory?): TaskCategory {
        val cycle = order.filter { it in enabled }
        require(cycle.isNotEmpty()) { "At least one task category must be enabled" }
        if (last == null) return cycle.first()
        val idx = cycle.indexOf(last)
        if (idx < 0) return cycle.first()
        return cycle[(idx + 1) % cycle.size]
    }
}

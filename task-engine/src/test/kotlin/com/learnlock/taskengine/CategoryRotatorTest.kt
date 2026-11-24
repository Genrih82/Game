package com.learnlock.taskengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CategoryRotatorTest {
    @Test
    fun startsFromFirstEnabled() {
        assertEquals(
            TaskCategory.MATH,
            CategoryRotator.next(
                setOf(TaskCategory.MATH, TaskCategory.READING, TaskCategory.LOGIC),
                last = null
            )
        )
        assertEquals(
            TaskCategory.READING,
            CategoryRotator.next(setOf(TaskCategory.READING, TaskCategory.LOGIC), last = null)
        )
    }

    @Test
    fun advancesRoundRobin() {
        val enabled = setOf(TaskCategory.MATH, TaskCategory.LOGIC)
        assertEquals(TaskCategory.LOGIC, CategoryRotator.next(enabled, TaskCategory.MATH))
        assertEquals(TaskCategory.MATH, CategoryRotator.next(enabled, TaskCategory.LOGIC))
    }

    @Test
    fun skipsDisabledLast() {
        assertEquals(
            TaskCategory.READING,
            CategoryRotator.next(
                setOf(TaskCategory.READING, TaskCategory.LOGIC),
                last = TaskCategory.MATH
            )
        )
    }

    @Test
    fun emptyEnabledFails() {
        assertFailsWith<IllegalArgumentException> {
            CategoryRotator.next(emptySet(), null)
        }
    }
}

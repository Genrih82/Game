package com.learnlock.taskengine

/**
 * Возрастные рамки заданий по ориентирам Минпросвещения РФ.
 *
 * Опора:
 * - Приказ Минпросвещения России от 18.05.2023 № 372
 *   (федеральная образовательная программа НОО);
 * - Федеральная рабочая программа «Математика» 1–4 классы
 *   (содержание по классам: пределы чисел, введение ×/÷).
 *
 * Соответствие возраста классу — как в [SchoolVocabulary.gradeForAge]
 * (типичный возраст на конец/середину учебного года):
 * до 7 → 1 класс, 8 → 2, 9 → 3, 10+ → 4 и далее.
 *
 * Это практический потолок для генератора LearnLock, а не полный
 * учебный план: приложение выдаёт короткие устные задания.
 */
object AgeCurriculum {

    /** Школьный класс-ориентир 0 (дошколка) … 4+. */
    fun gradeBand(ageYears: Int): Int = when {
        ageYears <= 5 -> 0
        ageYears <= 7 -> 1
        ageYears == 8 -> 2
        ageYears == 9 -> 3
        else -> 4
    }

    /**
     * Навыки, допустимые для возраста без ручного preferredSkill.
     */
    fun allowedSkills(ageYears: Int): Set<SkillTag> {
        val grade = gradeBand(ageYears)
        val math = when (grade) {
            0 -> setOf(SkillTag.MATH_ADD, SkillTag.MATH_COMPARE)
            1 -> setOf(SkillTag.MATH_ADD, SkillTag.MATH_SUB, SkillTag.MATH_COMPARE)
            2 -> setOf(
                SkillTag.MATH_ADD,
                SkillTag.MATH_SUB,
                SkillTag.MATH_COMPARE,
                SkillTag.MATH_MUL,
                SkillTag.MATH_DIV
            )
            else -> setOf(
                SkillTag.MATH_ADD,
                SkillTag.MATH_SUB,
                SkillTag.MATH_COMPARE,
                SkillTag.MATH_MUL,
                SkillTag.MATH_DIV,
                SkillTag.MATH_MIX
            )
        }
        val reading = when (grade) {
            0, 1 -> setOf(SkillTag.READ_LETTER, SkillTag.READ_ODD)
            2 -> setOf(SkillTag.READ_LETTER, SkillTag.READ_ODD, SkillTag.READ_SYNONYM)
            else -> setOf(SkillTag.READ_LETTER, SkillTag.READ_ODD, SkillTag.READ_SYNONYM)
        }
        val logic = when (grade) {
            0 -> setOf(SkillTag.LOGIC_SEQ, SkillTag.LOGIC_ANALOGY)
            1 -> setOf(SkillTag.LOGIC_SEQ, SkillTag.LOGIC_ANALOGY, SkillTag.LOGIC_PATTERN)
            2 -> setOf(
                SkillTag.LOGIC_SEQ,
                SkillTag.LOGIC_ANALOGY,
                SkillTag.LOGIC_PATTERN,
                SkillTag.LOGIC_WORD
            )
            else -> SkillTag.entries.filter { it.category == TaskCategory.LOGIC }.toSet()
        }
        // Английский в НОО обычно со 2 класса; неправильные глаголы — постепенно.
        val english = when (grade) {
            0, 1 -> setOf(SkillTag.ENG_VOCAB)
            2 -> setOf(SkillTag.ENG_VOCAB, SkillTag.ENG_IRREGULAR)
            else -> setOf(SkillTag.ENG_VOCAB, SkillTag.ENG_IRREGULAR, SkillTag.ENG_THREE_FORMS)
        }
        return math + reading + logic + english
    }

    fun isSkillAllowed(ageYears: Int, skill: SkillTag): Boolean =
        skill in allowedSkills(ageYears)

    /**
     * Верхняя граница операнда для устных примеров.
     *
     * 1 класс: сложение/вычитание в пределах 20 (ФРП).
     * 2 класс: действия в пределах 100; табличное ×/÷ в пределах 50.
     * 3–4: шире, но для устного режима держим умеренный потолок.
     */
    fun maxOperand(ageYears: Int, skill: SkillTag): Int {
        val grade = gradeBand(ageYears)
        return when (skill) {
            SkillTag.MATH_ADD, SkillTag.MATH_SUB, SkillTag.MATH_COMPARE -> when (grade) {
                0 -> 10
                1 -> 20
                2 -> 100
                3 -> 200
                else -> 500
            }
            SkillTag.MATH_MUL, SkillTag.MATH_DIV -> when (grade) {
                0, 1 -> 1 // навык запрещён; значение не используется
                2 -> 10 // таблица в пределах ~50: множители до 10
                3 -> 12
                else -> 15
            }
            SkillTag.MATH_MIX -> when (grade) {
                0, 1 -> 20
                2 -> 50
                3 -> 100
                else -> 200
            }
            else -> 20
        }
    }

    /** Максимальный результат умножения (ориентир «в пределах 50» для 2 класса). */
    fun maxProduct(ageYears: Int): Int = when (gradeBand(ageYears)) {
        0, 1 -> 0
        2 -> 50
        3 -> 100
        else -> 144
    }

    fun clampSkill(ageYears: Int, skill: SkillTag?, category: TaskCategory): SkillTag? {
        if (skill == null) return null
        if (skill.category != category) return null
        if (isSkillAllowed(ageYears, skill)) return skill
        return null
    }
}

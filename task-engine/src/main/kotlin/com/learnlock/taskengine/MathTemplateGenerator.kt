package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Сборка математической [Task] из [TemplateEntry]: случайные операнды,
 * подстановка в шаблон и расчёт принимаемого ответа по типу навыка.
 */
class MathTemplateGenerator {
    /**
     * Генерирует задачу по записи каталога и профилю сложности.
     *
     * Диапазоны `a`, `b`, `c` и контекстные переменные подбираются по [DifficultyProfile.level]
     * и [TemplateEntry.skillTag].
     */
    fun generate(entry: TemplateEntry, profile: DifficultyProfile, random: Random): Task {
        val level = profile.level()
        val age = profile.ageYears
        val bound = AgeCurriculum.maxOperand(age, entry.skillTag).coerceAtLeast(2)

        var a = random.nextInt(1, bound + 1)
        var b = random.nextInt(1, bound + 1)
        var c = random.nextInt(1, (bound / 2).coerceAtLeast(2) + 1)

        when (entry.skillTag) {
            SkillTag.MATH_SUB, SkillTag.MATH_MIX -> {
                if (b >= a) {
                    val t = a
                    a = (b + random.nextInt(1, 5)).coerceAtMost(bound + 5)
                    b = t.coerceAtMost(a - 1).coerceAtLeast(1)
                }
            }
            SkillTag.MATH_MUL -> {
                val maxFactor = AgeCurriculum.maxOperand(age, SkillTag.MATH_MUL).coerceAtLeast(2)
                val maxProduct = AgeCurriculum.maxProduct(age).coerceAtLeast(4)
                a = random.nextInt(2, maxFactor + 1)
                val maxB = (maxProduct / a).coerceAtLeast(2).coerceAtMost(maxFactor)
                b = random.nextInt(2, maxB + 1)
            }
            SkillTag.MATH_DIV -> {
                val maxFactor = AgeCurriculum.maxOperand(age, SkillTag.MATH_DIV).coerceAtLeast(2)
                val maxProduct = AgeCurriculum.maxProduct(age).coerceAtLeast(4)
                b = random.nextInt(2, maxFactor + 1)
                val maxQ = (maxProduct / b).coerceAtLeast(2).coerceAtMost(maxFactor)
                val quotient = random.nextInt(2, maxQ + 1)
                a = b * quotient
            }
            SkillTag.MATH_COMPARE -> {
                if (a == b) {
                    b = (b + random.nextInt(1, 5)).coerceAtMost(bound + 5)
                    if (a == b) b = a + 1
                }
            }
            else -> Unit
        }

        val price = random.nextInt(2, 20) * 50
        val money = random.nextInt(1, 25) * 50
        val personA = TaskLexicon.people.random(random)
        val personB = TaskLexicon.people.filter { it != personA }.random(random)
        val noun = TaskLexicon.items.random(random)

        val vars = mutableMapOf(
            "a" to a.toString(),
            "b" to b.toString(),
            "c" to c.toString(),
            "sum" to (a + b).toString(),
            "diff" to (a - b).toString(),
            "prod" to (a * b).toString(),
            "a_name" to personA.genitive,
            "b_name" to personB.genitive,
            "item" to RussianPlural.howMany(noun),
            "item_a" to RussianPlural.form(a, noun),
            "item_b" to RussianPlural.form(b, noun),
            "item_c" to RussianPlural.form(c, noun),
            "pupil_a" to RussianPlural.form(a, TaskLexicon.pupil),
            "friend_a" to RussianPlural.form(a, TaskLexicon.friend),
            "pack_b" to RussianPlural.form(b, TaskLexicon.pack),
            "row_a" to RussianPlural.form(a, TaskLexicon.row),
            "book_b" to RussianPlural.form(b, TaskLexicon.book),
            "person_a" to RussianPlural.form(a, TaskLexicon.person),
            "bird_a" to RussianPlural.form(a, TaskLexicon.bird),
            "bird" to RussianPlural.howMany(TaskLexicon.bird),
            "price" to price.toString(),
            "price_rub" to RussianPlural.form(price, TaskLexicon.ruble),
            "money" to money.toString(),
            "money_rub" to RussianPlural.form(money, TaskLexicon.ruble),
            "op" to "?",
            "count" to a.toString(),
            "name" to personA.genitive,
            "pronoun" to personA.pronounHeShe,
            "Pronoun" to personA.pronounHeSheCap,
            "bought" to personA.bought,
            "gave" to personA.gave
        )
        entry.variables.forEach { key ->
            if (key !in vars) vars[key] = a.toString()
        }

        val prompt = TemplateEngine.render(entry.template, vars)
        return buildTask(
            entry = entry,
            prompt = prompt,
            a = a,
            b = b,
            c = c,
            price = price,
            money = money,
            nameA = personA.nominative,
            nameB = personB.nominative,
            level = level
        )
    }

    /** Маршрутизация к типу задачи (число, сравнение, смешанная) по [entry.skillTag]. */
    private fun buildTask(
        entry: TemplateEntry,
        prompt: String,
        a: Int,
        b: Int,
        c: Int,
        price: Int,
        money: Int,
        nameA: String,
        nameB: String,
        level: Int
    ): Task {
        val lower = prompt.lowercase()
        return when (entry.skillTag) {
            SkillTag.MATH_ADD -> numberTask(entry, prompt, a + b, level)
            SkillTag.MATH_SUB -> numberTask(entry, prompt, a - b, level)
            SkillTag.MATH_MUL -> numberTask(entry, prompt, a * b, level)
            SkillTag.MATH_DIV -> numberTask(entry, prompt, a / b, level)
            SkillTag.MATH_COMPARE -> compareTask(entry, prompt, lower, a, b, nameA, nameB, level)
            SkillTag.MATH_MIX -> mixTask(entry, prompt, lower, a, b, c, price, money, level)
            else -> numberTask(entry, prompt, a + b, level)
        }
    }

    /**
     * Задание на сравнение: кто больше, какое число больше или знак неравенства.
     * Ветвление по ключевым фразам в [lower].
     */
    private fun compareTask(
        entry: TemplateEntry,
        prompt: String,
        lower: String,
        a: Int,
        b: Int,
        nameA: String,
        nameB: String,
        level: Int
    ): Task {
        if ("у кого" in lower) {
            val winner = when {
                a > b -> nameA
                a < b -> nameB
                else -> nameA
            }
            val options = listOf(nameA, nameB).distinct()
            return Task(
                id = "math-cmp-who-$a-$b",
                category = TaskCategory.MATH,
                prompt = prompt,
                inputType = AnswerInputType.CHOICE,
                choices = options.mapIndexed { i, l -> TaskChoice(i.toString(), l) },
                acceptedAnswers = listOf(winner),
                hint = entry.hint,
                hints = Task.buildHints("Сравни два количества", entry.hint),
                difficultyLevel = level,
                skillTag = SkillTag.MATH_COMPARE
            )
        }
        if ("что больше" in lower || "какое число больше" in lower ||
            "какое число меньше" in lower || "или" in lower || "какое больше" in lower
        ) {
            val left = a
            val right = if (b == a) a + 1 else b
            val pickSmaller = "меньше" in lower
            val answer = (if (pickSmaller) minOf(left, right) else maxOf(left, right)).toString()
            return Task(
                id = "math-cmp-num-$left-$right",
                category = TaskCategory.MATH,
                prompt = prompt,
                inputType = AnswerInputType.CHOICE,
                choices = listOf(left, right).mapIndexed { i, n -> TaskChoice(i.toString(), n.toString()) },
                acceptedAnswers = listOf(answer),
                hint = entry.hint,
                hints = Task.buildHints("Сравни два числа", entry.hint),
                difficultyLevel = level,
                skillTag = SkillTag.MATH_COMPARE
            )
        }
        val op = when {
            a > b -> ">"
            a < b -> "<"
            else -> "="
        }
        return Task(
            id = "math-cmp-$a-$b",
            category = TaskCategory.MATH,
            prompt = prompt,
            inputType = AnswerInputType.CHOICE,
            choices = listOf(
                TaskChoice("gt", ">"),
                TaskChoice("lt", "<"),
                TaskChoice("eq", "=")
            ),
            acceptedAnswers = listOf(op),
            hint = entry.hint,
            hints = Task.buildHints("Выбери знак сравнения", entry.hint),
            difficultyLevel = level,
            skillTag = SkillTag.MATH_COMPARE
        )
    }

    /**
     * Смешанные контекстные задачи: «хватит ли денег» или числовой ответ по эвристикам текста.
     */
    private fun mixTask(
        entry: TemplateEntry,
        prompt: String,
        lower: String,
        a: Int,
        b: Int,
        c: Int,
        price: Int,
        money: Int,
        level: Int
    ): Task {
        if ("хватит" in lower) {
            val answer = if (money >= price) "да" else "нет"
            return Task(
                id = "math-money-$price-$money",
                category = TaskCategory.MATH,
                prompt = prompt,
                inputType = AnswerInputType.CHOICE,
                choices = listOf(TaskChoice("y", "Да"), TaskChoice("n", "Нет")),
                acceptedAnswers = listOf(answer),
                hint = entry.hint,
                hints = Task.buildHints("Сравни деньги и цену", entry.hint),
                difficultyLevel = level,
                skillTag = SkillTag.MATH_MIX
            )
        }
        val result = when {
            "×" in prompt || "*" in prompt -> a * b + c
            "вышли" in lower -> a - b + c
            "прилетело" in lower || "улетело" in lower -> a + b - c
            "купил" in lower || "купила" in lower || "отдал" in lower || "отдала" in lower -> a + b - c
            else -> a + b - c
        }
        return numberTask(entry, prompt, result, level)
    }

    /** Задача с числовым вводом и единственным целочисленным ответом. */
    private fun numberTask(entry: TemplateEntry, prompt: String, answer: Int, level: Int): Task =
        Task(
            id = "math-${entry.skillTag.name.lowercase()}-$answer-${prompt.hashCode()}",
            category = TaskCategory.MATH,
            prompt = prompt,
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf(answer.toString()),
            hint = entry.hint,
            hints = Task.buildHints(
                soft = "Определи, какое действие нужно выполнить",
                main = entry.hint,
                strong = "Перепроверь вычисление ещё раз"
            ),
            difficultyLevel = level,
            skillTag = entry.skillTag
        )
}

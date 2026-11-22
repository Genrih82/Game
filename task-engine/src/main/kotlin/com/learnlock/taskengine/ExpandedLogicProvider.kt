package com.learnlock.taskengine

import kotlin.random.Random

/**
 * Генератор задач [TaskCategory.LOGIC]: числовые последовательности
 * (арифметическая, геометрическая, «фибоначчиевидная», с постоянной второй разностью),
 * узоры из символов, аналогии и короткие текстовые задачи.
 *
 * Антиповтор формулировок выполняет [TaskEngine].
 */
class ExpandedLogicProvider : TaskProvider {
    override val category: TaskCategory = TaskCategory.LOGIC

    private val people = TaskLexicon.logicPeople
    private val items = TaskLexicon.logicItems

    override fun generate(
        profile: DifficultyProfile,
        random: Random,
        preferredSkill: SkillTag?
    ): Task {
        val level = profile.level()
        val skill = AgeCurriculum.clampSkill(profile.ageYears, preferredSkill, category)
            ?: pickSkill(profile.ageYears, random)
        return when (skill) {
            SkillTag.LOGIC_SEQ -> sequence(level, random)
            SkillTag.LOGIC_PATTERN -> pattern(level, random)
            SkillTag.LOGIC_WORD -> wordProblem(level, random)
            SkillTag.LOGIC_ANALOGY -> analogy(level, random)
            else -> sequence(level, random)
        }
    }

    /** Выбор логического навыка по возрасту (текстовые задачи — с ~2 класса). */
    private fun pickSkill(ageYears: Int, random: Random): SkillTag {
        val allowed = AgeCurriculum.allowedSkills(ageYears)
            .filter { it.category == category }
        return allowed.random(random)
    }

    /** Аналогия вида «A → B, как C → ?». */
    private fun analogy(level: Int, random: Random): Task {
        val pairs = listOf(
            Analogy("кот", "котёнок", "собака", "щенок", listOf("щенок", "кот", "мяч", "лес")),
            Analogy("день", "ночь", "свет", "тьма", listOf("тьма", "утро", "солнце", "книга")),
            Analogy("учитель", "школа", "врач", "больница", listOf("больница", "урок", "парта", "пенал")),
            Analogy("птица", "гнездо", "пчела", "улей", listOf("улей", "крыло", "небо", "мёд")),
            Analogy("рука", "перчатка", "нога", "ботинок", listOf("ботинок", "палец", "шапка", "нос")),
            Analogy("зима", "снег", "осень", "листва", listOf("листва", "жара", "лыжи", "каток"))
        )
        val item = pairs.random(random)
        val options = item.options.shuffled(random)
        val soft = "Найди такую же связь между словами"
        val main = "«${item.a}» относится к «${item.b}» так же, как «${item.c}» к ответу"
        return Task(
            id = "logic-analogy-${item.a}-${item.c}",
            category = category,
            prompt = "${item.a} → ${item.b}, как ${item.c} → ?",
            inputType = AnswerInputType.CHOICE,
            choices = options.mapIndexed { index, label -> TaskChoice(index.toString(), label) },
            acceptedAnswers = listOf(item.answer),
            hint = main,
            hints = Task.buildHints(soft, main),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_ANALOGY
        )
    }

    private data class Analogy(
        val a: String,
        val b: String,
        val c: String,
        val answer: String,
        val options: List<String>
    )

    /** Не меньше 4 вариантов ответа, включая верный; без дублей и отрицательных. */
    private fun ensureIntChoices(answer: Int, candidates: List<Int>, random: Random): List<Int> {
        val pool = candidates.filter { it != answer && it >= 0 }.distinct().toMutableList()
        var delta = 1
        while (pool.size < 3) {
            val up = answer + delta
            val down = answer - delta
            if (up !in pool && up != answer) pool.add(up)
            if (pool.size < 3 && down >= 0 && down !in pool && down != answer) pool.add(down)
            delta++
            if (delta > 50) break
        }
        return (pool.shuffled(random).take(3) + answer).distinct().shuffled(random)
    }

    /** Выбирает подтип числовой последовательности. */
    private fun sequence(level: Int, random: Random): Task {
        return when (random.nextInt(if (level <= 3) 2 else 4)) {
            0 -> arithmetic(level, random)
            1 -> geometric(level, random)
            2 -> fibonacciish(level, random)
            else -> secondDiff(level, random)
        }
    }

    /** Арифметическая прогрессия: постоянный шаг между соседними числами. */
    private fun arithmetic(level: Int, random: Random): Task {
        val step = when {
            level <= 3 -> 1
            level <= 5 -> random.nextInt(2, 5)
            else -> random.nextInt(-4, 7).let { if (it == 0) 3 else it }
        }
        val start = random.nextInt(1, if (level <= 4) 12 else 21)
        val seq = List(4) { start + it * step }
        val answer = start + 4 * step
        val distractors = listOf(
            answer + step,
            answer - 1,
            answer + 2,
            answer - step,
            answer + if (step > 0) -step * 2 else step * 2
        )
        val options = ensureIntChoices(answer, distractors, random)
        return Task(
            id = "logic-seq-$start-$step",
            category = category,
            prompt = "${TextVariator(random).askNext()}?\n${seq.joinToString(", ")}, ?",
            inputType = AnswerInputType.CHOICE,
            choices = options.mapIndexed { index, value ->
                TaskChoice(index.toString(), value.toString())
            },
            acceptedAnswers = listOf(answer.toString()),
            hint = "Найди шаг последовательности: прибавляется или вычитается одно и то же число",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Найди шаг последовательности: прибавляется или вычитается одно и то же число"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_SEQ
        )
    }

    /** Геометрическая прогрессия: умножение на постоянное отношение. */
    private fun geometric(level: Int, random: Random): Task {
        val ratio = if (level <= 5) 2 else 3
        val start = random.nextInt(1, if (level <= 4) 6 else 12)
        val seq = List(4) { start * Math.pow(ratio.toDouble(), it.toDouble()).toInt() }
        val answer = seq.last() * ratio
        val ensured = ensureIntChoices(
            answer,
            listOf(answer + ratio, answer - ratio, answer * 2 - ratio, answer + 1, answer + ratio * 2),
            random
        )
        return Task(
            id = "logic-geo-$start-$ratio",
            category = category,
            prompt = "${TextVariator(random).askNext()}?\n${seq.joinToString(", ")}, ?",
            inputType = AnswerInputType.CHOICE,
            choices = ensured.mapIndexed { index, value ->
                TaskChoice(index.toString(), value.toString())
            },
            acceptedAnswers = listOf(answer.toString()),
            hint = "Каждое следующее число получается умножением на $ratio",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Каждое следующее число получается умножением на $ratio"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_SEQ
        )
    }

    /** Последовательность, где каждый член — сумма двух предыдущих. */
    private fun fibonacciish(level: Int, random: Random): Task {
        val a = random.nextInt(1, 6)
        val b = random.nextInt(1, 6)
        val c = a + b
        val d = b + c
        val e = c + d
        val seq = listOf(a, b, c, d)
        val answer = e
        val options = (listOf(answer + 1, answer - 1, answer + 2, answer + a) + answer)
            .distinct().filter { it > 0 }.shuffled(random).take(4)
        val ensured = if (answer in options) options else (options.dropLast(1) + answer).shuffled(random)
        return Task(
            id = "logic-fib-$a-$b",
            category = category,
            prompt = "${TextVariator(random).askNext()}?\n${seq.joinToString(", ")}, ?",
            inputType = AnswerInputType.CHOICE,
            choices = ensured.mapIndexed { index, value ->
                TaskChoice(index.toString(), value.toString())
            },
            acceptedAnswers = listOf(answer.toString()),
            hint = "Каждое число — сумма двух предыдущих",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Каждое число — сумма двух предыдущих"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_SEQ
        )
    }

    /** Квадратичная последовательность с постоянной второй разностью между шагами. */
    private fun secondDiff(level: Int, random: Random): Task {
        val d1 = random.nextInt(1, 4)
        val d2 = random.nextInt(1, 3)
        val start = random.nextInt(1, 8)
        val seq = List(4) { i ->
            var v = start
            var diff = d1
            repeat(i) { v += diff; diff += d2 }
            v
        }
        var next = seq.last()
        var diff = d1 + 3 * d2 // d1,d1+d2,d1+2d2,d1+3d2
        next += diff
        val answer = next
        val options = (listOf(answer + d2, answer - d2, answer + 1, answer + d1) + answer)
            .distinct().filter { it > 0 }.shuffled(random).take(4)
        val ensured = if (answer in options) options else (options.dropLast(1) + answer).shuffled(random)
        return Task(
            id = "logic-seq2-$start-$d1-$d2",
            category = category,
            prompt = "${TextVariator(random).askNext()}?\n${seq.joinToString(", ")}, ?",
            inputType = AnswerInputType.CHOICE,
            choices = ensured.mapIndexed { index, value ->
                TaskChoice(index.toString(), value.toString())
            },
            acceptedAnswers = listOf(answer.toString()),
            hint = "Разности между соседними числами растут на одно и то же число",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Разности между соседними числами растут на одно и то же число"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_SEQ
        )
    }

    /** Задание на узор из Unicode-символов (чередование, рост групп, тройка). */
    private fun pattern(level: Int, random: Random): Task {
        val shapes = listOf("●", "■", "▲", "◆", "★", "♥")
        return when (random.nextInt(if (level <= 4) 2 else 3)) {
            0 -> alternating(shapes, random, level)
            1 -> growing(shapes, random, level)
            else -> triple(shapes, random, level)
        }
    }

    /** Узор ABAB… — следующий символ противоположной фигуры. */
    private fun alternating(shapes: List<String>, random: Random, level: Int): Task {
        val a = shapes.random(random)
        val b = shapes.filter { it != a }.random(random)
        val pattern = listOf(a, b, a, b, a)
        val answer = b
        val options = shapes.shuffled(random).take(4)
        val ensured = if (answer in options) options else (options.dropLast(1) + answer).shuffled(random)
        return Task(
            id = "logic-shape-$a-$b",
            category = category,
            prompt = "${TextVariator(random).askPattern()}?\n${pattern.joinToString(" ")} ?",
            inputType = AnswerInputType.CHOICE,
            choices = ensured.mapIndexed { index, label -> TaskChoice(index.toString(), label) },
            acceptedAnswers = listOf(answer),
            hint = "Чередование двух фигур",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Чередование двух фигур"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_PATTERN
        )
    }

    /** Группы длины 1, 2, 3 из разных фигур; далее начало группы из четырёх. */
    private fun growing(shapes: List<String>, random: Random, level: Int): Task {
        val picks = shapes.shuffled(random).take(4)
        val a = picks[0]
        val b = picks[1]
        val c = picks[2]
        val d = picks[3]
        val pattern = listOf(a, b, b, c, c, c)
        val answer = d
        val options = shapes.shuffled(random).take(4)
        val ensured = if (answer in options) options else (options.dropLast(1) + answer).shuffled(random)
        return Task(
            id = "logic-grow-$a-$b-$c-$d",
            category = category,
            prompt = "${TextVariator(random).askPattern()}?\n${pattern.joinToString(" ")} ?",
            inputType = AnswerInputType.CHOICE,
            choices = ensured.mapIndexed { index, label -> TaskChoice(index.toString(), label) },
            acceptedAnswers = listOf(answer),
            hint = "Группы растут: 1, 2, 3 — дальше новая фигура (начало группы из 4)",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Группы растут: 1, 2, 3 — дальше новая фигура (начало группы из 4)"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_PATTERN
        )
    }

    /** Повтор цикла из трёх фигур: a, b, c, a, b, … */
    private fun triple(shapes: List<String>, random: Random, level: Int): Task {
        val trio = shapes.shuffled(random).take(3)
        val (a, b, c) = trio
        val pattern = listOf(a, b, c, a, b)
        val answer = c
        val options = shapes.shuffled(random).take(4)
        val ensured = if (answer in options) options else (options.dropLast(1) + answer).shuffled(random)
        return Task(
            id = "logic-tri-$a-$b-$c",
            category = category,
            prompt = "${TextVariator(random).askPattern()}?\n${pattern.joinToString(" ")} ?",
            inputType = AnswerInputType.CHOICE,
            choices = ensured.mapIndexed { index, label -> TaskChoice(index.toString(), label) },
            acceptedAnswers = listOf(answer),
            hint = "Паттерн из трёх фигур повторяется",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Паттерн из трёх фигур повторяется"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_PATTERN
        )
    }

    /** Случайная текстовая логическая задача по уровню. */
    private fun wordProblem(level: Int, random: Random): Task {
        return when (random.nextInt(if (level <= 4) 3 else 5)) {
            0 -> compareProblem(level, random)
            1 -> remainingProblem(level, random)
            2 -> sumColors(level, random)
            3 -> ageOrder(level, random)
            else -> countProblem(level, random)
        }
    }

    /** Сравнение количеств у двух персонажей («на N больше»). */
    private fun compareProblem(level: Int, random: Random): Task {
        val p1 = people.random(random)
        val p2 = people.filter { it != p1 }.random(random)
        val noun = items.random(random)
        val a = random.nextInt(3, 15)
        val delta = random.nextInt(1, 6)
        val b = a + delta
        return Task(
            id = "logic-cmpw-${p1.nominative}-$a-$b-${noun.many}",
            category = category,
            prompt = "У ${p1.genitive} $a ${RussianPlural.form(a, noun)}, у ${p2.genitive} на $delta больше. Сколько ${RussianPlural.howMany(noun)} у ${p2.genitive}?",
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf(b.toString()),
            hint = "Прибавь разницу к числу ${p1.genitive}",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Прибавь разницу к числу ${p1.genitive}"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_WORD
        )
    }

    /** Остаток после передачи части предметов другу. */
    private fun remainingProblem(level: Int, random: Random): Task {
        val p1 = people.random(random)
        val p2 = people.filter { it != p1 }.random(random)
        val noun = items.random(random)
        val a = random.nextInt(8, 30)
        val gave = random.nextInt(1, a)
        return Task(
            id = "logic-rem-${p1.nominative}-$a-$gave-${noun.many}",
            category = category,
            prompt = "У ${p1.genitive} было $a ${RussianPlural.form(a, noun)}. ${p1.pronounHeSheCap} ${p1.gave} $gave ${RussianPlural.form(gave, noun)} другу ${p2.genitive}. Сколько ${RussianPlural.howMany(noun)} осталось у ${p1.genitive}?",
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf((a - gave).toString()),
            hint = "Отними отданное от начального количества",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Отними отданное от начального количества"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_WORD
        )
    }

    /** Сумма шаров разных «цветов» в коробке. */
    private fun sumColors(level: Int, random: Random): Task {
        val a = random.nextInt(1, 12)
        val b = random.nextInt(1, 12)
        val c = if (level <= 4) 0 else random.nextInt(1, 8)
        val total = a + b + c
        val ball = TaskLexicon.ball
        val red = RussianPlural.countedWithAdjective(a, "красный", "красных", ball)
        val blue = RussianPlural.countedWithAdjective(b, "синий", "синих", ball)
        val colors = if (c > 0) {
            val green = RussianPlural.countedWithAdjective(c, "зелёный", "зелёных", ball)
            "$red, $blue и $green"
        } else {
            "$red и $blue"
        }
        return Task(
            id = "logic-col-$a-$b-$c",
            category = category,
            prompt = "В коробке $colors. Сколько всего ${RussianPlural.howMany(ball)}?",
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf(total.toString()),
            hint = "Сложи шары всех цветов",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Сложи шары всех цветов"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_WORD
        )
    }

    /** Цепочка сравнения возраста — кто самый старший. */
    private fun ageOrder(level: Int, random: Random): Task {
        val p1 = people.random(random)
        val rest = people.filter { it != p1 }.shuffled(random)
        val p2 = rest[0]
        val p3 = rest[1]
        return Task(
            id = "logic-age-${p1.nominative}-${p2.nominative}-${p3.nominative}",
            category = category,
            prompt = "${p1.nominative} старше ${p2.genitive}, а ${p2.nominative} старше ${p3.genitive}. Кто самый старший?",
            inputType = AnswerInputType.CHOICE,
            choices = listOf(p1, p2, p3).mapIndexed { index, person ->
                TaskChoice(index.toString(), person.nominative)
            },
            acceptedAnswers = listOf(p1.nominative),
            hint = "Сравни цепочку: кто старше всех?",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Сравни цепочку: кто старше всех?"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_WORD
        )
    }

    /** Подсчёт птиц: прилетели и улетели. */
    private fun countProblem(level: Int, random: Random): Task {
        val a = random.nextInt(3, 15)
        val came = random.nextInt(1, 8)
        val left = random.nextInt(1, 8)
        val total = a + came - left
        val bird = TaskLexicon.bird
        return Task(
            id = "logic-cnt-$a-$came-$left",
            category = category,
            prompt = "На дереве сидело $a ${RussianPlural.form(a, bird)}. Прилетело ещё $came, потом улетели $left. Сколько ${RussianPlural.howMany(bird)} стало?",
            inputType = AnswerInputType.NUMBER,
            acceptedAnswers = listOf(total.toString()),
            hint = "Сначала прибавь прилетевших, потом вычти улетевших",
            hints = Task.buildHints("Подумай, какое правило здесь действует", "Сначала прибавь прилетевших, потом вычти улетевших"),
            difficultyLevel = level,
            skillTag = SkillTag.LOGIC_WORD
        )
    }
}

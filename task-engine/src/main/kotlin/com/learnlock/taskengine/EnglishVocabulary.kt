package com.learnlock.taskengine

/**
 * Словари английского: неправильные глаголы и базовая лексика.
 * Данные из classpath `vocabulary/english_*.txt`.
 */
object EnglishVocabulary {

    data class IrregularVerb(
        val base: String,
        val past: String,
        val participle: String,
        val ru: String,
        val band: Int
    )

    data class VocabEntry(
        val en: String,
        val ru: String,
        val band: Int
    )

    val irregularVerbs: List<IrregularVerb> by lazy { loadIrregular() }
    val vocab: List<VocabEntry> by lazy { loadVocab() }

    fun irregularForAge(ageYears: Int): List<IrregularVerb> {
        val maxBand = when (AgeCurriculum.gradeBand(ageYears)) {
            0, 1 -> 0
            2 -> 1
            3 -> 2
            else -> 3
        }
        return irregularVerbs.filter { it.band <= maxBand }
    }

    fun vocabForAge(ageYears: Int): List<VocabEntry> {
        val maxBand = when (AgeCurriculum.gradeBand(ageYears)) {
            0, 1 -> 1
            2 -> 2
            else -> 3
        }
        return vocab.filter { it.band <= maxBand }
    }

    private fun loadIrregular(): List<IrregularVerb> {
        val stream = EnglishVocabulary::class.java.getResourceAsStream("/vocabulary/english_irregular.txt")
            ?: return emptyList()
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val p = line.split('|')
                    if (p.size < 5) return@mapNotNull null
                    IrregularVerb(
                        base = p[0].trim().lowercase(),
                        past = p[1].trim().lowercase(),
                        participle = p[2].trim().lowercase(),
                        ru = p[3].trim(),
                        band = p[4].trim().toIntOrNull()?.coerceIn(1, 3) ?: 1
                    )
                }
                .toList()
        }
    }

    private fun loadVocab(): List<VocabEntry> {
        val stream = EnglishVocabulary::class.java.getResourceAsStream("/vocabulary/english_vocab.txt")
            ?: return emptyList()
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val p = line.split('|')
                    if (p.size < 3) return@mapNotNull null
                    VocabEntry(
                        en = p[0].trim().lowercase(),
                        ru = p[1].trim(),
                        band = p[2].trim().toIntOrNull()?.coerceIn(1, 3) ?: 1
                    )
                }
                .toList()
        }
    }
}

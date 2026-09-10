package org.stypox.dicio.util

/**
 * Вспомогательные утилиты нечёткого сравнения строк для детерминированного роутера интентов.
 *
 * Аналог `Similarity` из проекта-предшественника (voice-loop): используется в `IntentRouter`
 * для точного и нечёткого (Jaro–Winkler) сопоставления распознанной фразы с ключевыми шаблонами
 * интентов. Не зависит от Android, поэтому покрывается обычными юнит-тестами.
 */
object Similarity {

    /**
     * Нормализация фразы для сопоставления: lowercase, схлопнуть пробелы, убрать пунктуацию.
     */
    fun norm(value: String): String {
        val b = StringBuilder(value.length)
        for (c in value) {
            when {
                c.isLetterOrDigit() -> b.append(c.lowercaseChar())
                c == ' ' -> b.append(' ')
                // любой другой символ (пунктуация и пр.) игнорируем
                else -> {}
            }
        }
        // схлопываем повторяющиеся пробелы и обрезаем
        val out = b.toString().replace(Regex("\\s+"), " ").trim()
        return out
    }

    /**
     * Содержится ли в [text] одна из [needles] (после нормализации).
     */
    fun anyContains(text: String, needles: List<String>): Boolean {
        for (needle in needles) {
            if (text.contains(needle)) return true
        }
        return false
    }

    /**
     * Простейший Jaro–Winkler-подобный коэффициент похожести двух строк (0.0..1.0).
     *
     * Реализация без внешних зависимостей: computes Jaro similarity with a light Winkler boost.
     * Используется в роутере как fuzzy-fallback, когда фраза распознавалась не точно.
     */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val ad = norm(a)
        val bd = norm(b)
        if (ad.isEmpty() && bd.isEmpty()) return 1.0
        if (ad == bd) return 1.0
        if (ad.isEmpty() || bd.isEmpty()) return 0.0
        return jaroWinkler(ad.toCharArray(), bd.toCharArray())
    }

    private fun jaroWinkler(a: CharArray, b: CharArray): Double {
        val matchDistance = (maxOf(a.size, b.size) / 2 - 1).coerceAtLeast(0)
        val aMatches = BooleanArray(a.size)
        val bMatches = BooleanArray(b.size)
        var matches = 0
        for (i in a.indices) {
            val start = (i - matchDistance).coerceAtLeast(0)
            val end = (i + matchDistance + 1).coerceAtMost(b.size)
            for (j in start until end) {
                if (bMatches[j]) continue
                if (a[i] != b[j]) continue
                aMatches[i] = true
                bMatches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0

        // транспозиция
        var t = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatches[i]) continue
            while (!bMatches[k]) k++
            if (a[i] != b[k]) t++
            k++
        }
        t /= 2

        val m = matches.toDouble()
        val jaro = ((m / a.size) + (m / b.size) + ((m - t) / m)) / 3.0

        // Winkler boost по общему префиксу (первой части 4 символа)
        var prefix = 0
        val maxPrefix = minOf(4, a.size, b.size)
        while (prefix < maxPrefix && a[prefix] == b[prefix]) prefix++
        val prefixScale = 0.1 * prefix
        return (jaro + prefixScale * (1.0 - jaro)).coerceIn(0.0, 1.0)
    }
}
package org.stypox.dicio.skills.timer

import org.stypox.dicio.util.Similarity
import java.time.Duration

/**
 * Парсер русских длительностей («поставь таймер на пять минут», «на 2 часа») без зависимости
 * от dicio-numbers, который русский язык не поддерживает (только it/en) — из-за этого штатный
 * TimerSkill не строится (parserFormatter == null, см. ADR-8 в docs/architecture.md).
 *
 * Чистый Kotlin без Android-зависимостей — покрывается юнит-тестами.
 */
object RuNumbers {

    /** Числа-слова: единицы, тин, декады (составные «двадцать пять» собираются при парсинге). */
    private val numberWords: Map<String, Int> = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "одиннадцать" to 11,
        "два" to 2, "две" to 2, "двенадцать" to 12, "двадцать" to 20,
        "три" to 3, "тринадцать" to 13, "тридцать" to 30,
        "четыре" to 4, "четырнадцать" to 14, "сорок" to 40,
        "пять" to 5, "пятнадцать" to 15, "пятьдесят" to 50,
        "шесть" to 6, "шестнадцать" to 16, "шестьдесят" to 60,
        "семь" to 7, "семнадцать" to 17, "семьдесят" to 70,
        "восемь" to 8, "восемнадцать" to 18, "восемьдесят" to 80,
        "девять" to 9, "девятнадцать" to 19, "девяносто" to 90,
        "десять" to 10,
    )

    /** Юниты длительности: слово → множитель в секундах. */
    private val unitWords: Map<String, Long> = mapOf(
        "секунд" to 1L, "секунда" to 1L, "секунду" to 1L, "секунды" to 1L, "сек" to 1L,
        "минут" to 60L, "минута" to 60L, "минуту" to 60L, "минуты" to 60L, "мин" to 60L,
        "час" to 3600L, "часа" to 3600L, "часов" to 3600L, "часу" to 3600L, "часик" to 3600L,
    )

    /**
     * Извлечь длительность из русской фразы. Поддерживает слова-числа, цифры и единицы
     * (секунды/минуты/часы), в том числе перечисления: «на час десять минут».
     *
     * @return [Duration] если найдена положительная длительность, иначе `null`.
     */
    fun parseDuration(text: String): Duration? {
        val t = Similarity.norm(text)
        if (t.isEmpty()) return null

        val tokens = t.split(" ")
        var totalSeconds = 0L
        var current: Int? = null
        var lastWasDecade = false

        for (token in tokens) {
            val number = token.toIntOrNull() ?: numberWords[token]
            if (number != null) {
                current = if (lastWasDecade && number in 1..9 && current != null && current in 20..90) {
                    // составное число: «двадцать пять» = 25
                    current + number
                } else {
                    number
                }
                lastWasDecade = current != null && current % 10 == 0 && current in 20..90
                continue
            }

            val unit = unitWords[token]
            if (unit != null) {
                // единица без числа означает 1 («поставь таймер на час»)
                totalSeconds += (current ?: 1) * unit
                current = null
                lastWasDecade = false
                continue
            }

            // прочие слова (предлоги, «и», глаголы) не влияют на накопленное значение
            if (token == "и") continue
            if (token.length > 2) {
                // новое «не-числовое» слово сбрасывает накопитель только если юнит уже был:
                // «на пять минут и потом» — накопитель уже обнулён юнитом
                current = null
                lastWasDecade = false
            }
        }

        return if (totalSeconds > 0) Duration.ofSeconds(totalSeconds) else null
    }
}
package org.stypox.dicio.skills.alarm

import org.stypox.dicio.util.Similarity

/**
 * Парсер времени будильника («на шесть утра», «в 7:30 вечера», «в шесть тридцать утра»)
 * — чистый Kotlin без Android-зависимостей, по образцу RuNumbers (ADR-8).
 */
object RuTimeParser {

    /** Время будильника: час [0..23], минута [0..59]. */
    data class TimeOfDay(val hour: Int, val minute: Int)

    /**
     * Извлечь время из фразы. Поддерживает:
     *  - цифры: «в 6:30 утра», «на 7.30 вечера»
     *  - слова: «на шесть утра», «в шесть тридцать утра», «семь часов вечера»
     *  - формы суток: утра/дня/вечера/ночи
     *
     * @return [TimeOfDay] или null, если времени в фразе нет.
     */
    fun parseAlarmTime(text: String): TimeOfDay? {
        val t = Similarity.norm(text)
        if (t.isEmpty()) return null

        // 1) Цифровой формат «6:30» / «6.30»
        val digits = Regex("(\\d{1,2})[:.]\\s*(\\d{2})").find(t)
        if (digits != null) {
            val h = digits.groupValues[1].toInt()
            val m = digits.groupValues[2].toInt()
            val period = detectDayPeriod(t)
            if (h in 0..23 && m in 0..59) {
                return TimeOfDay(applyDayPeriod(h, period), m)
            }
        }

        // 2) Словесный формат: час-слово (1..12) + опционально минуты-слово (1..59)
        val tokens = t.split(" ")
        var hourWord: Int? = null
        var minuteWord: Int? = null
        for (token in tokens) {
            val n = token.toIntOrNull() ?: Numbers.numberWords[token] ?: continue
            if (n in 1..12 && hourWord == null) {
                hourWord = n
            } else if (n in 1..59 && hourWord != null && minuteWord == null) {
                minuteWord = n
            }
        }
        if (hourWord == null) return null

        val period = detectDayPeriod(t)
        val hour = applyDayPeriod(hourWord, period)
        return TimeOfDay(hour, minuteWord ?: 0)
    }

    /** «утра»/«дня»/«вечера»/«ночи» — простая маркировка суток. */
    private fun detectDayPeriod(t: String): String? = when {
        t.contains("утра") || t.contains("утро") -> "утро"
        t.contains("вечера") || t.contains("вечер") -> "вечер"
        t.contains("ночи") || t.contains("ночь") -> "ночь"
        t.contains("дня") || t.contains("день") -> "день"
        else -> null
    }

    /** Приведение 1..12 + форма суток к 24-часовому времени. */
    private fun applyDayPeriod(hour: Int, period: String?): Int {
        if (hour < 0 || hour > 23) return hour
        if (hour > 12) return hour // уже 24-часовое («в 19:30»)
        return when (period) {
            "вечер" -> if (hour == 12) 12 else hour + 12   // 6 вечера = 18, 12 вечера = 12
            "день" -> if (hour == 12) 12 else hour + 12    // 6 дня = 18 (после полудня)
            "ночь" -> if (hour == 12) 0 else hour          // 12 ночи = 0, 2 ночи = 2
            "утро" -> if (hour == 12) 0 else hour          // 12 утра = 0, 6 утра = 6
            else -> hour                                   // нет формы — считаем как есть
        }
    }

    /**
     * Дни недели для повтора будильника.
     * @return список Calendar-дней (SUNDAY=1..SATURDAY=7) или null (одноразовый).
     */
    fun parseDays(text: String): List<Int>? {
        val t = Similarity.norm(text)
        val everyDay = t.contains("каждое утро") || t.contains("каждый день") ||
            t.contains("каждодневно") || t.contains("каждый день недели")
        val weekdays = t.contains("в будни") || t.contains("по будням") || t.contains("будни")
        val weekends = t.contains("по выходным") || t.contains("выходным")

        return when {
            everyDay -> listOf(
                java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY,
                java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY,
                java.util.Calendar.FRIDAY, java.util.Calendar.SATURDAY,
                java.util.Calendar.SUNDAY,
            )
            weekdays -> listOf(
                java.util.Calendar.MONDAY, java.util.Calendar.TUESDAY,
                java.util.Calendar.WEDNESDAY, java.util.Calendar.THURSDAY,
                java.util.Calendar.FRIDAY,
            )
            weekends -> listOf(
                java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY,
            )
            else -> null
        }
    }

    /** Мини-доступ к числам-словам (переиспользование таблицы RuNumbers без зависимостей). */
    private object Numbers {
        val numberWords: Map<String, Int> = mapOf(
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
    }
}
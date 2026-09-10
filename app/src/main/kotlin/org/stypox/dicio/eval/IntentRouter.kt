package org.stypox.dicio.eval

import org.stypox.dicio.util.Similarity
import org.stypox.dicio.util.StringUtils

/**
 * Детерминированный роутер интентов (Вариант C, fuzzy-first).
 *
 * Работает ПОВЕРХ свободного распознавания (Vosk/вход). Берёт распознанную фразу и сопоставляет
 * с таблицей «сквозных» интентов (время, дата, приветствие, заряд, громкость, «повтори», ...).
 * Сначала пытается точное совпадение по ключевым словам, при неполном совпадении — нечёткий поиск
 * (Jaro–Winkler) по ключевым фразам. Если ничего не совпало достаточно уверенно — возвращает null,
 * и управление уходит к свободному SkillRanker'у (остальные навыки).
 *
 * Чистый Kotlin без Android-зависимостей — покрыт юнит-тестами в CI.
 */
class IntentRouter(
    private val fuzzyThreshold: Double = DEFAULT_FUZZY_THRESHOLD,
) {

    /** Решение роутера: какой интент выбран и готовая реплика (для статичных). */
    data class Decision(
        val intent: String,
        val reply: String? = null,
        val matchedInput: String = "",
        val matchType: String = "exact", // "exact" | "fuzzy"
        val score: Double = 1.0,
    )

    private data class Tpl(val keywords: List<String>, val intent: String, val reply: String? = null)

    /** Ключевые слова для точного сопоставления (по образцу voice-loop Router). */
    private val templates: List<Tpl> = listOf(
        Tpl(listOf("врем", "который час", "во сколько", "сколько сейчас", "сколько времени", "час"), "TIME"),
        Tpl(listOf("какое число", "сегодня число", "какой день недели", "день недели", "какой год", "время года"), "DATE"),
        Tpl(listOf("погод", "градус", "на улице", "холодн", "тепл", "как одеться"), "WEATHER"),
        Tpl(listOf("привет", "здравствуйте", "добрый день", "доброе утро", "добрый вечер", "доброй ночи"), "GREETING", "Здравствуйте!"),
        Tpl(listOf("пока", "до свидания", "всего доброго"), "BYE", "До свидания! Хорошего дня."),
        Tpl(listOf("как тебя зовут", "твоё имя"), "NAME", "Меня зовут Dicio."),
        Tpl(listOf("кто ты", "ты кто", "что ты такое"), "WHO", "Я ваш домашний голосовой ассистент."),
        Tpl(listOf("спасибо", "благодарю"), "THANKS", "Пожалуйста! Рад помочь."),
        Tpl(listOf("заряд батареи", "сколько заряда", "уровень заряда"), "BATTERY"),
        Tpl(listOf("сделай потише", "стань тише", "говори тише"), "VOLUME_DOWN", "Снижаю громкость."),
        Tpl(listOf("сделай погромче", "стань громче", "говори громче"), "VOLUME_UP", "Увеличиваю громкость."),
        Tpl(listOf("выключи звук", "замолчи", "умолкни", "тихо"), "MUTE", "Умолкаю."),
        Tpl(listOf("повтори", "повтори ещё раз", "повтори повтори"), "REPEAT"),
        Tpl(listOf("включи фонарик", "включи свет"), "FLASHLIGHT_ON"),
        Tpl(listOf("выключи фонарик", "выключи свет"), "FLASHLIGHT_OFF"),
        Tpl(listOf("спокойной ночи", "иди спать", "выключись", "сон"), "SLEEP", "Хорошо, ухожу в сон."),
    )

    /**
     * Классифицировать распознанную фразу в интент или вернуть null, если интента нет.
     *
     * @param rawText распознанный текст (как пришёл из STT/ввода).
     */
    fun classify(rawText: String): Decision? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        val t = Similarity.norm(text)

        // 1) точный match: есть ли ключевое слово целиком в фразе
        for (template in templates) {
            if (Similarity.anyContains(t, template.keywords)) {
                return Decision(template.intent, template.reply, rawText, "exact", 1.0)
            }
        }

        // 2) нечётный match: ищем похожее слово из ключевых фраз (ловит оговорки)
        var bestScore = 0.0
        var bestTemplate: Tpl? = null
        for (template in templates) {
            for (keyword in template.keywords) {
                if (keyword.length < 3) continue
                val score = fuzzyByWord(t, Similarity.norm(keyword))
                if (score > bestScore) {
                    bestScore = score
                    bestTemplate = template
                }
            }
        }

        if (bestTemplate != null && bestScore >= fuzzyThreshold) {
            return Decision(bestTemplate.intent, bestTemplate.reply, rawText, "fuzzy", bestScore)
        }

        return null
    }

    /**
     * Нечёткое совпадение «по близкому слову»: true, если какое-то слово входа отличается от
     * какого-то слова ключа ровно на одну правку (оговорка «сечас»→«сейчас»). Это надёжно фильтрует
     * случайные фразы и не даёт ложных интентов.
     */
    private fun fuzzyByWord(inputN: String, keywordNorm: String): Double {
        if (keywordNorm.isEmpty()) return 0.0
        if (inputN.contains(keywordNorm)) return 1.0
        for (kwWord in keywordNorm.split(" ")) {
            if (kwWord.length < 4) continue
            for (inWord in inputN.split(" ")) {
                if (inWord.length < 4) continue
                if (StringUtils.levenshteinDistance(inWord, kwWord) <= 1) {
                    return 1.0
                }
            }
        }
        return 0.0
    }

    companion object {
        /** Порог уверенности для нечётного совпадения по слову (0..1), ниже — считаем «не нашлось». */
        const val DEFAULT_FUZZY_THRESHOLD = 0.80
    }
}
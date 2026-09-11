package org.stypox.dicio.eval

import org.stypox.dicio.skills.timer.RuNumbers
import org.stypox.dicio.util.Similarity

/**
 * Детерминированный роутер интентов (Вариант C, refined).
 *
 * Работает ПОВЕРХ свободного распознавания (Vosk/вход). Берёт распознанную фразу и сопоставляет
 * с таблицей «сквозных» интентов.
 *
 * Правила (выработаны по боевым логам, чтобы исключить ложные срабатывания):
 *  1. Оговорки STT («сечас», «пагода») чинятся словарём [sttCorrections] до классификации.
 *  2. Отрицание/недовольство/уточнение («не», «нет», «почему», «что-то», «не спрашивал»,
 *     «не понял») включают negative-guard: статичный интент НЕ выдаётся — управление уходит дальше,
 *     чтобы жалобу не «съел» случайный интент.
 *  3. Точное совпадение ключевых слов — единственный источник статичных интентов (без агрессивного
 *     fuzzy по словам, который давал ложные срабатывания: «утра»→GREETING, «контакт»→NAME и т.п.).
 *
 * Если ничего не совпало — возвращает null, и управление уходит к свободному SkillRanker'у.
 */
class IntentRouter {

    /** Решение роутера: какой интент выбран и готовая реплика (для статичных). */
    data class Decision(
        val intent: String,
        val reply: String? = null,
        val matchedInput: String = "",
        val matchType: String = "exact",
        val score: Double = 1.0,
    )

    private data class Tpl(val keywords: List<String>, val intent: String, val reply: String? = null)

    /** Междометия-подтверждения (короткий шум из боевых логов). */
    private val JustAcknowledgements: List<String> =
        listOf("ага", "ок", "да", "понятно", "угу", "ясно", "хорошо")

    /** Служебные слова-обрывки, которые не являются командами (UNKNOWN_SHORT). */
    private val ShortNoiseWords: List<String> = listOf("мне", "ну", "так", "это", "мда", "ммм")

    /** Типичные оговорки STT: «сечас»→«сейчас» и т.п. */
    private val sttCorrections: Map<String, String> = mapOf(
        "сечас" to "сейчас",
        "пагода" to "погода",
    )

    /** Триггеры недовольства/отрицания/уточнения — «как слово» (word-boundary): при их наличии
     *  статичный интент не выдаём. «не»/«нет» должны быть отдельными словами, чтобы не ловить
     *  подстроки в обычных словах («напомни»→«не», «мне», «день»). */
    private val negativeWordGuard: List<String> = listOf("не", "нет")

    /** Триггеры недовольства/уточнения — «как фраза/подстрока с пробелом между частями». */
    private val negativePhraseGuard: List<String> = listOf(
        "не понял", "не спрашивал", "не спросил", "не надо", "не хочу",
        "почему", "что-то", "зачем", "стоп", "отмена", "не это", "не тот", "постой",
    )

    /** Ключевые слова для точного сопоставления (по образцу voice-loop Router + расширено). */
    private val templates: List<Tpl> = listOf(
        Tpl(listOf("врем", "который час", "во сколько", "сколько сейчас", "сколько времени", "час"), "TIME"),
        Tpl(listOf("какое число", "сегодня число", "какой день недели", "день недели", "какой год", "время года"), "DATE"),
        Tpl(listOf("погод", "градус", "на улице", "холодн", "тепл", "как одеться", "одеться", "одеваться", "одеть", "надеть", "надевать", "что надеть", "как мне одеваться"), "WEATHER"),
        Tpl(listOf("привет", "здравствуйте", "добрый день", "доброе утро", "добрый вечер", "доброй ночи"), "GREETING", "Здравствуйте!"),
        Tpl(listOf("пока", "до свидания", "всего доброго"), "BYE", "До свидания! Хорошего дня."),
        Tpl(listOf("как тебя зовут", "твоё имя"), "NAME", "Меня зовут Dicio."),
        Tpl(listOf("кто ты", "ты кто", "что ты такое"), "WHO", "Я ваш домашний голосовой ассистент."),
        Tpl(listOf("что ты умеешь", "что ты можешь", "твои возможности", "что ты ещё умеешь"), "SKILLS", "Я умею сообщать время и погоду, отвечать на вопросы, считать, ставить таймеры и многое другое. Скажите команду — помогу."),
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

    /** Фразы с «будильник/напоминание» без длительности -> честный отказ (UNSUPPORTED).
     *  С длительностью («поставь будильник на пять минут») это по смыслу таймер — пропускаем в навыки. */
    private val unsupportedIntent: Tpl =
        Tpl(listOf("будильник", "напоминание", "напомни", "поставь будильник"), "UNSUPPORTED",
            "Я пока не умею ставить будильники и напоминания.")

    fun classify(rawText: String): Decision? {
        val text = rawText.trim()
        if (text.isEmpty()) return null

        // Сначала чиним известные оговорки STT, потом нормализуем и смотрим отрицание.
        val corrected = applySttCorrections(text)
        val t = Similarity.norm(corrected)
        if (t.isEmpty()) return null

        // 0) Короткий шум / междометия.
        if (t.split(" ").size <= 2) {
            if (JustAcknowledgements.any { t == it }) {
                return Decision("ACK", "Понял.", rawText, "exact", 1.0)
            }
            if (t.length < 3 || ShortNoiseWords.any { t == it }) {
                return Decision("UNKNOWN_SHORT", "Извините, не расслышала.", rawText, "exact", 1.0)
            }
        }

        // 1) Negative-guard: недовольство/отрицание не должны давать статичный интент.
        val words = t.split(" ")
        val hasNegationWord = words.any { w -> negativeWordGuard.contains(w) }
        val hasNegationPhrase = negativePhraseGuard.any { t.contains(it) }
        if (hasNegationWord || hasNegationPhrase) {
            return null
        }

        // 2) Точное совпадение ключевых слов (единственный источник статичных интентов).
        for (template in templates) {
            if (Similarity.anyContains(t, template.keywords)) {
                return Decision(template.intent, template.reply, rawText, "exact", 1.0)
            }
        }

        // 3) «Будильник/напоминание» — честный отказ, НО только если фраза без длительности:
        // «поставь будильник на пять минут» по смыслу — таймер, уходим в навыки таймера.
        if (Similarity.anyContains(t, unsupportedIntent.keywords)
            && RuNumbers.parseDuration(t) == null
        ) {
            return Decision(
                unsupportedIntent.intent, unsupportedIntent.reply, rawText, "exact", 1.0
            )
        }

        return null
    }

    /** Применяет словарь оговорок к фразе (подстрока → исправление). */
    private fun applySttCorrections(text: String): String {
        var result = text
        for ((from, to) in sttCorrections) {
            if (from.length < 3) continue
            if (result.contains(from)) {
                result = result.replace(from, to)
            }
        }
        return result
    }
}
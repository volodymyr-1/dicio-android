package org.stypox.dicio.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class IntentRouterTest : StringSpec({

    val router = IntentRouter()

    "exact: точное совпадение по ключевым словам" {
        router.classify("какой сейчас час")!!.intent shouldBe "TIME"
        router.classify("сколько времени")!!.intent shouldBe "TIME"
        router.classify("какое сегодня число")!!.intent shouldBe "DATE"
        router.classify("привет")!!.intent shouldBe "GREETING"
        router.classify("спасибо большое")!!.intent shouldBe "THANKS"
        router.classify("сколько заряда батареи")!!.intent shouldBe "BATTERY"
    }

    "static: реплики для статичных интентов" {
        router.classify("привет")!!.reply shouldBe "Здравствуйте!"
        router.classify("пока")!!.reply shouldBe "До свидания! Хорошего дня."
        router.classify("сделай погромче")!!.intent shouldBe "VOLUME_UP"
        router.classify("сделай погромче")!!.reply shouldBe "Увеличиваю громкость."
    }

    "skills/ack/noise: новые интенты из боевых логов" {
        router.classify("что ты умеешь")!!.intent shouldBe "SKILLS"
        router.classify("что ты ещё умеешь делать")!!.intent shouldBe "SKILLS"
        router.classify("ага")!!.intent shouldBe "ACK"
        router.classify("ага")!!.reply shouldBe "Понял."
        router.classify("мне")!!.intent shouldBe "UNKNOWN_SHORT"
        router.classify("мм")!!.intent shouldBe "UNKNOWN_SHORT"
    }

    "fuzzy: нечётное совпадение распознаётся" {
        // распознанная с искажением фраза должна всё равно дать интент
        router.classify("сколько сечас")!!.intent shouldBe "TIME"
        router.classify("сколько сечас времени")!!.intent shouldBe "TIME"
        router.classify("спасибо")!!.intent shouldBe "THANKS"
    }

    "unknown: посторонняя фраза не даёт интента" {
        router.classify("абракадабра").shouldBeNull()
        router.classify("квантитативный анализ данных").shouldBeNull()
    }

    "negative-guard: недовольство/отрицание не даёт ложный статичный интент" {
        router.classify("я тебе не спрашивал про интернет поиск где сказал контакт").shouldBeNull()
        router.classify("я что-то не понял говорить ты умеешь почему ты молчишь").shouldBeNull()
    }

    "нереализованные команды -> честный отказ, а не «Можете повторить?»" {
        router.classify("поставь будильник на завтра на десять утра")!!.intent shouldBe "UNSUPPORTED"
        router.classify("поставь будильник")!!.intent shouldBe "UNSUPPORTED"
        router.classify("напомни мне")!!.intent shouldBe "UNSUPPORTED"
    }

    "оговорки STT чинятся словарём" {
        router.classify("какая пагода")!!.intent shouldBe "WEATHER"
        router.classify("сколько сечас")!!.intent shouldBe "TIME"
    }
})
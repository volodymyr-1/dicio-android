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
})
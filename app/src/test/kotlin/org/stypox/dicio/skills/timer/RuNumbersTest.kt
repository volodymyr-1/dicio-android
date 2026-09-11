package org.stypox.dicio.skills.timer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration

class RuNumbersTest : StringSpec({
    "пять минут -> 5 минут" {
        RuNumbers.parseDuration("поставь таймер на пять минут") shouldBe Duration.ofMinutes(5)
    }

    "цифры и формы единиц" {
        RuNumbers.parseDuration("таймер на 5 минут") shouldBe Duration.ofMinutes(5)
        RuNumbers.parseDuration("поставь таймер на 1 минуту") shouldBe Duration.ofMinutes(1)
        RuNumbers.parseDuration("поставь таймер на десять минут") shouldBe Duration.ofMinutes(10)
        RuNumbers.parseDuration("таймер на пятнадцать минут") shouldBe Duration.ofMinutes(15)
    }

    "часы и составные" {
        RuNumbers.parseDuration("поставь таймер на час") shouldBe Duration.ofHours(1)
        RuNumbers.parseDuration("на два часа") shouldBe Duration.ofHours(2)
        RuNumbers.parseDuration("на час десять минут") shouldBe Duration.ofHours(1).plusMinutes(10)
        RuNumbers.parseDuration("на двадцать пять минут") shouldBe Duration.ofMinutes(25)
        RuNumbers.parseDuration("таймер на полминуты") shouldBe null // пол-единицы не поддержаны в v1
    }

    "нет длительности" {
        RuNumbers.parseDuration("поставь таймер").shouldBeNull()
        RuNumbers.parseDuration("какая погода").shouldBeNull()
        RuNumbers.parseDuration("").shouldBeNull()
    }
})

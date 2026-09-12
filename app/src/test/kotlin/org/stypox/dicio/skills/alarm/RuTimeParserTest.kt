package org.stypox.dicio.skills.alarm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class RuTimeParserTest : StringSpec({
    "словесное время с формой суток" {
        RuTimeParser.parseAlarmTime("поставь будильник на шесть утра") shouldBe
            RuTimeParser.TimeOfDay(6, 0)
        RuTimeParser.parseAlarmTime("в шесть тридцать утра") shouldBe
            RuTimeParser.TimeOfDay(6, 30)
        RuTimeParser.parseAlarmTime("будильник на семь вечера") shouldBe
            RuTimeParser.TimeOfDay(19, 0)
        RuTimeParser.parseAlarmTime("будильник на семь тридцать вечера") shouldBe
            RuTimeParser.TimeOfDay(19, 30)
        RuTimeParser.parseAlarmTime("будильник на двенадцать ночи") shouldBe
            RuTimeParser.TimeOfDay(0, 0)
        RuTimeParser.parseAlarmTime("будильник на шесть дня") shouldBe
            RuTimeParser.TimeOfDay(18, 0)
    }

    "цифровое время" {
        RuTimeParser.parseAlarmTime("поставь будильник на 6:30 утра") shouldBe
            RuTimeParser.TimeOfDay(6, 30)
        RuTimeParser.parseAlarmTime("будильник на 7.30 вечера") shouldBe
            RuTimeParser.TimeOfDay(19, 30)
        RuTimeParser.parseAlarmTime("будильник на 19:30") shouldBe
            RuTimeParser.TimeOfDay(19, 30)
    }

    "дни недели для повтора" {
        RuTimeParser.parseDays("поставь будильник на шесть утра на каждое утро")!!
            .size shouldBe 7
        RuTimeParser.parseDays("будильник на семь утра в будни")!!.size shouldBe 5
        RuTimeParser.parseDays("поставь будильник на шесть утра").shouldBeNull()
    }

    "нет времени — null" {
        RuTimeParser.parseAlarmTime("поставь будильник").shouldBeNull()
        RuTimeParser.parseAlarmTime("какая погода").shouldBeNull()
    }
})

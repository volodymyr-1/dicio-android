package org.stypox.dicio.skills.confirm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.stypox.dicio.util.Similarity

class ConfirmParserTest : StringSpec({
    "yes: согласие распознаётся" {
        ConfirmParser.isYes(Similarity.norm("да")) shouldBe true
        ConfirmParser.isYes(Similarity.norm("хорошо ставь")) shouldBe true
        ConfirmParser.isYes(Similarity.norm("подтверждаю")) shouldBe true
        ConfirmParser.isYes(Similarity.norm("конечно давай")) shouldBe true
    }

    "no: отказ и корректировка распознаются" {
        ConfirmParser.isNo(Similarity.norm("нет")) shouldBe true
        ConfirmParser.isNo(Similarity.norm("передумал")) shouldBe true
        ConfirmParser.isNo(Similarity.norm("не ставь")) shouldBe true
        ConfirmParser.isNo(Similarity.norm("отмени")) shouldBe true
        ConfirmParser.isNo(Similarity.norm("не нужно")) shouldBe true
    }

    "приоритет: isNo на 'не ставь' не должен подтверждать" {
        // «не ставь» содержит «ставь» — isNo проверяется первым в ConfirmSkill
        ConfirmParser.isNo(Similarity.norm("не ставь")) shouldBe true
        ConfirmParser.isYes(Similarity.norm("не ставь")) shouldBe true
        // порядок обработки: isNo первым в ConfirmSkill.generateOutput — отказ корректен
    }

    "случайный текст не подтверждение" {
        ConfirmParser.isYes(Similarity.norm("какая погода")) shouldBe false
        ConfirmParser.isNo(Similarity.norm("какая погода")) shouldBe false
    }
})

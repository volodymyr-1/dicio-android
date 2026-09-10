package org.stypox.dicio.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SimilarityTest : StringSpec({
    "norm: приводим к нижнему регистру и убираем пунктуацию" {
        Similarity.norm("Привет, как дела?") shouldBe "привет как дела"
        Similarity.norm("  СКОЛЬКО   СЕЙЧАС :)") shouldBe "сколько сейчас"
        Similarity.norm("") shouldBe ""
    }

    "anyContains: поиск ключевых слов" {
        Similarity.anyContains("который час сейчас", listOf("время", "час")) shouldBe true
        Similarity.anyContains("сколько времени", listOf("врем")) shouldBe true
        Similarity.anyContains("погода в киеве", listOf("дождь", "ветер")) shouldBe false
    }

    "similarity: одинаковые строки и похожие" {
        Similarity.similarity("привет", "привет") shouldBe 1.0
        Similarity.similarity("сколько сейчас", "сколько сейчас") shouldBe 1.0
        Similarity.similarity("", "") shouldBe 1.0
        (Similarity.similarity("сделай погромче", "сделай громче") > 0.6) shouldBe true
        (Similarity.similarity("a", "zzzzzz") < 0.5) shouldBe true
    }
})
package org.stypox.dicio.skills.confirm

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.AlwaysBestScore
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import org.stypox.dicio.util.Similarity

/**
 * Фреймворк подтверждения (итерация D, паттерн confirm-before-execute; архитектура —
 * по образцу штатного ConfirmCallOutput / TimerOutput.SetAskDuration).
 *
 * Навык формирует интерпретацию («Я поставлю таймер на одну минуту. Подтверждаете?»),
 * поддиалог ловит ответ пользователя:
 *  - «да/хорошо/ставь/подтверждаю» → исполняется [execute]
 *  - «нет/передумал/отмен*» → отмена
 *  - корректировка («нет, а не на 7:30, а на 7:00») → [onCorrection] пересобирает
 *    подтверждение → цикл продолжается до явного да/нет
 *
 * Контекст живёт через штатные Dicio-batches (StartSubInteraction), wake word в
 * поддиалоге не требуется.
 */
object ConfirmParser {

    private val yesWords = listOf(
        "да", "хорошо", "окей", "ок", "ставь", "подтверждаю", "давай",
        "исполняй", "точно", "верно", "согласен", "конечно",
    )
    private val noWords = listOf(
        "нет", "передумал", "передумала", "не надо", "отмен", "не ставь", "не хочу", "не нужно",
    )

    fun isYes(normalizedText: String): Boolean =
        yesWords.any { normalizedText.contains(it) }

    fun isNo(normalizedText: String): Boolean =
        noWords.any { normalizedText.contains(it) }
}

/** Подтверждающий навык поддиалога (создаётся через [ConfirmOutput]). */
class ConfirmSkill(
    correspondingSkillInfo: SkillInfo,
    val confirmText: String,
    val execute: suspend () -> String,
    val onCorrection: suspend (String) -> ConfirmOutput?,
    val finishText: String,
) : Skill<String>(correspondingSkillInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, String> {
        return Pair(AlwaysBestScore, input)
    }

    override suspend fun generateOutput(ctx: SkillContext, input: String): SkillOutput {
        val t = Similarity.norm(input)
        // isNo приоритетнее: «не ставь» — это отказ/корректировка, а не согласие
        return when {
            ConfirmParser.isNo(t) -> {
                val corrected = onCorrection(input)
                corrected ?: CancelledOutput()
            }
            ConfirmParser.isYes(t) -> ConfirmedOutput(execute() + " " + finishText)
            else -> {
                val corrected = onCorrection(input)
                corrected ?: RepeatConfirmOutput(confirmText, this)
            }
        }
    }
}

/** Подтверждающий вывод: интерпретация + вопрос. Открывает поддиалог подтверждения. */
class ConfirmOutput(
    private val confirmText: String,
    private val correspondingSkillInfo: SkillInfo,
    private val execute: suspend () -> String,
    private val onCorrection: suspend (String) -> ConfirmOutput?,
    private val finishText: String = "",
) : SkillOutput {

    override fun getSpeechOutput(ctx: SkillContext): String = confirmText

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan =
        InteractionPlan.StartSubInteraction(
            reopenMicrophone = true,
            nextSkills = listOf(
                ConfirmSkill(correspondingSkillInfo, confirmText, execute, onCorrection, finishText)
            ),
        )

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Text(
            text = confirmText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** Итог после исполнения: закрывает диалог подтверждения. */
class ConfirmedOutput(private val text: String) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = text

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** Отмена: закрывает диалог. */
class CancelledOutput : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = "Отменено."

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Text(
            text = "Отменено.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** Повтор вопроса подтверждения (не расслышали ответ) — поддиалог продолжается. */
class RepeatConfirmOutput(
    private val confirmText: String,
    private val owner: ConfirmSkill,
) : SkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String =
        "Не расслышала. Повторю вопрос: $confirmText"

    override fun getInteractionPlan(ctx: SkillContext): InteractionPlan =
        InteractionPlan.StartSubInteraction(
            reopenMicrophone = true,
            nextSkills = listOf(
                ConfirmSkill(
                    owner.correspondingSkillInfo, confirmText,
                    owner.execute, owner.onCorrection, owner.finishText,
                )
            ),
        )

    @Composable
    override fun GraphicalOutput(ctx: SkillContext) {
        Text(
            text = "Не расслышала. Повторю вопрос: $confirmText",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp),
        )
    }
}

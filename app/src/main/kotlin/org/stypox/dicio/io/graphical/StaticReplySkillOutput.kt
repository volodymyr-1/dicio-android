package org.stypox.dicio.io.graphical

import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput

/**
 * Простой [SkillOutput] с фиксированным текстом ответа (и голосовым, и графическим).
 *
 * Используется детерминированным [org.stypox.dicio.eval.IntentRouter] для «сквозных» интентов
 * (приветствие, прощание, благодарность, громкость, «повтори» и т.п.), когда не нужна
 * сложная логика навыка — достаточно мгновенно ответить готовой репликой.
 */
class StaticReplySkillOutput(
    private val reply: String,
) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = reply
}
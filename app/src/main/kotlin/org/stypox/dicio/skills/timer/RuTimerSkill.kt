package org.stypox.dicio.skills.timer

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.AlwaysBestScore
import org.dicio.skill.skill.AlwaysWorstScore
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.Similarity
import java.time.Duration

/**
 * Русский таймер (ADR-8): работает без dicio-numbers (который ru не поддерживает, из-за чего
 * штатный TimerSkill не строится). Переиспользует [SetTimer] и общий список [TimerSkill.SET_TIMERS],
 * длительность парсит [RuNumbers]. Русские строки захардкожены сознательно (принцип
 * «русский по умолчанию», docs/architecture.md).
 */
object RuTimerInfo : SkillInfo("timer_ru") {
    override fun name(context: Context) = TimerInfo.name(context)
    override fun sentenceExample(context: Context) = TimerInfo.sentenceExample(context)

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Default.Timer)

    override fun build(ctx: SkillContext): Skill<*>? {
        // навешиваемся только на русскую локаль; в остальных языках работает штатный TimerSkill
        return if (ctx.sentencesLanguage == "ru") RuTimerSkill(this) else null
    }
}

/** Команда, извлечённая из ввода. */
sealed interface RuTimerCmd {
    data class Set(val duration: Duration?) : RuTimerCmd
    data object Cancel : RuTimerCmd
    data object None : RuTimerCmd
}

class RuTimerSkill(correspondingSkillInfo: SkillInfo) :
    Skill<RuTimerCmd>(correspondingSkillInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, RuTimerCmd> {
        val t = Similarity.norm(input)
        val hasTimerWord = t.contains("таймер") || t.contains("отсчёт") || t.contains("отсчет")

        // «поставь будильник на пять минут» — в быту это таймер-отсчёт (есть длительность)
        val hasDuration = RuNumbers.parseDuration(input) != null
        val isAlarmAsTimer = t.contains("будильник") && hasDuration

        val isCancel = (hasTimerWord || isAlarmAsTimer) &&
            (t.contains("отмени") || t.contains("отменить") || t.contains("останови") ||
                t.contains("остановить") || t.contains("выключи"))

        return when {
            isCancel -> Pair(AlwaysBestScore, RuTimerCmd.Cancel)
            hasTimerWord || isAlarmAsTimer ->
                Pair(AlwaysBestScore, RuTimerCmd.Set(RuNumbers.parseDuration(input)))
            else -> Pair(AlwaysWorstScore, RuTimerCmd.None)
        }
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: RuTimerCmd): SkillOutput {
        return when (inputData) {
            is RuTimerCmd.Set -> setTimer(ctx, inputData.duration)
            RuTimerCmd.Cancel -> cancelTimers()
            RuTimerCmd.None -> RuTimerOutput("Уточните команду.")
        }
    }

    private suspend fun setTimer(ctx: SkillContext, duration: Duration?): SkillOutput {
        if (duration == null) {
            return RuTimerOutput(
                "Уточните длительность. Например: поставь таймер на пять минут."
            )
        }

        var ringtone: Ringtone? = null
        // SetTimer создаёт CountDownTimer, которому нужен Looper — создание только на Main
        val setTimer = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            SetTimer(
                duration = duration,
                name = null,
                onMillisTickCallback = { millis ->
                    if (millis < 0 && ringtone?.isPlaying == false) {
                        ringtone?.play()
                    }
                },
                onSecondsTickCallback = { seconds ->
                    // без dicio-numbers: озвучиваем последние секунды цифрами (без «ноль»)
                    if (seconds in 1..5) {
                        ctx.speechOutputDevice.speak(seconds.toString())
                    }
                },
                onExpiredCallback = { _ ->
                    ringtone = RingtoneManager.getActualDefaultRingtoneUri(
                        ctx.android, RingtoneManager.TYPE_ALARM
                    )
                        ?.let { RingtoneManager.getRingtone(ctx.android, it) }
                        ?.also {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                it.isLooping = true
                            }
                            it.play()
                        }
                    if (ringtone == null) {
                        ctx.speechOutputDevice.speak("Время таймера истекло")
                    }
                },
                onCancelCallback = { timerToCancel ->
                    ringtone?.stop()
                    ringtone = null
                    TimerSkill.SET_TIMERS.removeIf { setTimer -> setTimer === timerToCancel }
                },
            )
        }
        TimerSkill.SET_TIMERS.add(setTimer)

        return RuTimerOutput("Таймер запущен. Длительность: ${formatDurationRu(duration)}.")
    }

    private fun cancelTimers(): SkillOutput {
        if (TimerSkill.SET_TIMERS.isEmpty()) {
            return RuTimerOutput("Нет активных таймеров.")
        }
        for (setTimer in TimerSkill.SET_TIMERS.toList()) {
            setTimer.cancel()
        }
        TimerSkill.SET_TIMERS.clear()
        return RuTimerOutput("Таймер отменён.")
    }

    /** «5 минут» / «1 минута» / «2 часа» / «1 час 5 минут». */
    private fun formatDurationRu(duration: Duration): String {
        val totalMinutes = duration.toMinutes()
        val hours = (totalMinutes / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()
        val seconds = (duration.seconds % 60).toInt()

        val parts = mutableListOf<String>()
        if (hours > 0) {
            parts.add("$hours ${pluralRu(hours, "час", "часа", "часов")}")
        }
        if (minutes > 0) {
            parts.add("$minutes ${pluralRu(minutes, "минута", "минуты", "минут")}")
        }
        if (parts.isEmpty() && seconds > 0) {
            parts.add("$seconds ${pluralRu(seconds, "секунда", "секунды", "секунд")}")
        }
        return parts.joinToString(" ")
    }

    private fun pluralRu(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}

/** Простой вывод: одна строка — и голос, и заголовок на экране. */
class RuTimerOutput(private val text: String) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = text
}

package org.stypox.dicio.skills.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
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
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.skills.confirm.ConfirmOutput
import org.stypox.dicio.util.Similarity
import java.util.Calendar

/**
 * Русский будильник на определённое время (итерация A, docs/plan.md): ставит будильник в
 * **системный Часы** через стандартный AlarmClock intent (без велосипеда, без собственного UI).
 * Время парсит [RuTimeParser] (ADR-8: свои ru-парсеры, т.к. dicio-numbers без русского).
 * Русские строки захардкожены сознательно (принцип «русский по умолчанию», docs/architecture.md).
 */
object RuAlarmInfo : SkillInfo("alarm_ru") {
    override fun name(context: Context) = context.getString(R.string.skill_name_alarm_ru)

    override fun sentenceExample(context: Context) =
        context.getString(R.string.skill_sentence_example_alarm_ru)

    @Composable
    override fun icon() = rememberVectorPainter(Icons.Default.Alarm)

    override fun build(ctx: SkillContext): Skill<*>? {
        return if (ctx.sentencesLanguage == "ru") RuAlarmSkill(this) else null
    }
}

/** Команда, извлечённая из ввода. */
sealed interface RuAlarmCmd {
    data class Set(val hour: Int, val minute: Int, val days: List<Int>?) : RuAlarmCmd
    data object Dismiss : RuAlarmCmd
    data object Ask : RuAlarmCmd
    data object None : RuAlarmCmd
}

class RuAlarmSkill(correspondingSkillInfo: SkillInfo) :
    Skill<RuAlarmCmd>(correspondingSkillInfo, Specificity.HIGH) {

    override fun score(ctx: SkillContext, input: String): Pair<Score, RuAlarmCmd> {
        val t = Similarity.norm(input)
        val hasAlarmWord = t.contains("будильник") || t.contains("разбуди") || t.contains("буди ")

        val isDismiss = hasAlarmWord &&
            (t.contains("отмени") || t.contains("отменить") || t.contains("выключи") ||
                t.contains("останови") || t.contains("убери"))

        val time = RuTimeParser.parseAlarmTime(input)
        val hasTimeForm = t.contains("утра") || t.contains("вечера") ||
            t.contains("утро") || t.contains("вечер") || t.contains("ночи") || t.contains("дня")

        return when {
            isDismiss -> Pair(AlwaysBestScore, RuAlarmCmd.Dismiss)
            hasAlarmWord && time != null -> Pair(
                AlwaysBestScore,
                RuAlarmCmd.Set(time.hour, time.minute, RuTimeParser.parseDays(input))
            )
            // «поставь будильник» без конкретного времени — спрашиваем, а не отказываёмся
            hasAlarmWord && hasTimeForm -> Pair(AlwaysBestScore, RuAlarmCmd.Ask)
            else -> Pair(AlwaysWorstScore, RuAlarmCmd.None)
        }
    }

    override suspend fun generateOutput(ctx: SkillContext, inputData: RuAlarmCmd): SkillOutput {
        return when (inputData) {
            is RuAlarmCmd.Set -> setAlarm(ctx, inputData)
            RuAlarmCmd.Dismiss -> dismissAlarm(ctx)
            RuAlarmCmd.Ask -> RuAlarmOutput("Уточните время. Например: поставь будильник на шесть утра.")
            RuAlarmCmd.None -> RuAlarmOutput("Уточните команду.")
        }
    }

    private suspend fun setAlarm(ctx: SkillContext, cmd: RuAlarmCmd.Set): SkillOutput {
        val timeText = formatTime(cmd.hour, cmd.minute)
        val daysText = when {
            cmd.days != null && cmd.days.size == 7 -> " на каждое утро"
            cmd.days != null && cmd.days.size == 5 -> " на будни"
            cmd.days != null -> " по выбранным дням"
            else -> ""
        }
        val info = correspondingSkillInfo

        // Подтверждение перед исполнением (итерация D): ошибки STT ловятся диалогом.
        return ConfirmOutput(
            confirmText = "Я поставлю будильник на $timeText$daysText. Подтверждаете?",
            correspondingSkillInfo = info,
            finishText = "Будильник идёт.",
            execute = {
                startAlarmInternal(ctx, cmd)
                "Будильник установлен."
            },
            onCorrection = { phrase ->
                val newTime = RuTimeParser.parseAlarmTime(phrase)
                if (newTime != null) {
                    // корректировка: «а не на 7:30, а на 7:00» — берём ПОСЛЕДНЕЕ время в фразе
                    setAlarm(ctx, RuAlarmCmd.Set(newTime.hour, newTime.minute,
                        RuTimeParser.parseDays(phrase) ?: cmd.days))
                } else {
                    null
                }
            },
        )
    }

    private fun startAlarmInternal(ctx: SkillContext, cmd: RuAlarmCmd.Set) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, cmd.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, cmd.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (cmd.days != null) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(cmd.days))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.android.startActivity(intent)
    }

    private fun dismissAlarm(ctx: SkillContext): SkillOutput {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                putExtra(
                    AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                    AlarmClock.ALARM_SEARCH_MODE_TIME
                )
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.android.startActivity(intent)
            RuAlarmOutput("Будильник отменён.")
        } else {
            RuAlarmOutput("Отмена будильника поддерживается с Android 6.")
        }
    }

    private fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(java.util.Locale.US, hour, minute)
}

/** Простой вывод: одна строка — и голос, и заголовок на экране. */
class RuAlarmOutput(private val text: String) : HeadlineSpeechSkillOutput {
    override fun getSpeechOutput(ctx: SkillContext): String = text
}

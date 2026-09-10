package org.stypox.dicio.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Кольцевой буфер диагностических событий голосового цикла.
 *
 * Аналог on-device logging, введённого в проекте-предшественнике (voice-loop): каждая ключевая
 * точка цикла (STT-распознавание, выбор навыка, выполнение, озвучивание ответа, wake word,
 * ошибки) добавляет строку в кольцевой буфер и дублирует её в Logcat под общим тегом "Dicio".
 *
 * Это позволяет:
 *  - видеть поток последних событий прямо на устройстве (панель в UI);
 *  - читать полный поток из ПК через `adb logcat -s Dicio` без перезапуска диагностики.
 *
 * Реализация намеренно простая (объект, синхронизированный ArrayDeque) — она используется и из
 * фоновой службы (WakeService), и из UI, поэтому здесь нет DI/Activity-зависимостей.
 */
object DiagnosticsLog {

    /** Общий тег в Logcat для фильтрации потока диагностики. */
    const val TAG = "Dicio"

    /** Максимальное число хранимых записей в кольцевом буфере. */
    private const val MAX_ENTRIES = 250

    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val buffer = ArrayDeque<String>()

    /**
     * Добавить диагностическую запись.
     *
     * @param stage этап цикла (например "STT", "ROUTER", "SKILL", "TTS", "WAKE", "ERROR").
     * @param message содержимое записи.
     */
    fun log(stage: String, message: String) {
        val line = "${timestampFormat.format(Date())} [$stage] $message"
        synchronized(buffer) {
            buffer.addLast(line)
            if (buffer.size > MAX_ENTRIES) {
                buffer.pollFirst()
            }
        }
        // Дублируем в Logcat под узнаваемым тегом, чтобы легко читать поток с ПК.
        Log.i(TAG, line)
    }

    /**
     * Снимок текущего содержимого буфера (самые старые записи первыми).
     */
    fun snapshot(): List<String> = synchronized(buffer) {
        buffer.toList()
    }

    /** Очистить буфер (например, в начале сеанса диагностики). */
    fun clear() {
        synchronized(buffer) {
            buffer.clear()
        }
    }
}
package org.stypox.dicio.util

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Кольцевой буфер диагностических событий голосового цикла.
 *
 * Аналог on-device logging, введённого в проекте-предшественнике (voice-loop): каждая ключевая
 * точка цикла (STT-распознавание, выбор навыка, выполнение, озвучивание ответа, wake word,
 * ошибки) добавляет строку в кольцевой буфер и дублирует её в файл filesDir/diagnostics.log.
 *
 * Это позволяет:
 *  - видеть поток последних событий прямо на устройстве (панель в UI);
 *  - читать полный поток с ПК через `adb pull <filesDir>/diagnostics.log`, независимо от logcat.
 *
 * Реализация намеренно простая (объект, синхронизированный ArrayDeque) — она используется и из
 * фоновой службы (WakeService), и из UI, поэтому здесь нет DI/Activity-зависимостей.
 */
object DiagnosticsLog {

    /** Общий тег диагностики. */
    const val TAG = "Dicio"

    /** Максимальное число хранимых записей в кольцевом буфере. */
    private const val MAX_ENTRIES = 250

    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val buffer = ArrayDeque<String>()

    /** Файл, в который дублируются все записи (назначается из MainActivity.onCreate). */
    private var logFile: java.io.File? = null

    /**
     * Назначить файл, в который пишутся все записи (append). Вызывается один раз при старте.
     * Это даёт канал чтения логов с устройства через `adb` независимо от logcat.
     */
    fun initFile(file: java.io.File) {
        logFile = file
    }

    /**
     * Добавить диагностическую запись.
     *
     * @param stage этап цикла (например "STT", "ROUTER", "SKILL", "TTS", "WAKE", "ERROR").
     * @param message содержимое записи.
     */
    fun log(stage: String, message: String) {
        val line = "${timestampFormat.format(Date())} [$stage] $message"
        try {
            synchronized(buffer) {
                buffer.addLast(line)
                if (buffer.size > MAX_ENTRIES) {
                    buffer.pollFirst()
                }
            }
        } catch (t: Throwable) {
            // Не роняем цикл из-за буфера, но помечаем в файл.
            appendToFile("DiagnosticsLog buffer error: $t")
        }

        // Дублирование в файл (надёжный канал чтения с ПК).
        appendToFile(line)
    }

    private fun appendToFile(line: String) {
        val file = logFile ?: return
        try {
            val writer = java.io.FileWriter(file, true)
            writer.write(line + "\n")
            writer.close()
        } catch (t: Throwable) {
            // ignore: логирование не должно ломать работу приложения
        }
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
package org.stypox.dicio.io.input

import kotlinx.coroutines.flow.StateFlow
import org.stypox.dicio.settings.datastore.UserSettings

interface SttInputDevice {
    val uiState: StateFlow<SttState>

    fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean

    fun stopListening()

    fun onClick(eventListener: (InputEvent) -> Unit)

    suspend fun destroy()

    companion object {
        // Пользователь часто задумывается / говорит длинные фразы (боевой фидбек): дефолт увеличен с 2 до 6,
        // чтобы Vosk не обрывал речь при короткой паузе. Диапазон настройки — 1..7.
        const val DEFAULT_STT_SILENCE_DURATION = 6
        fun getSttSilenceDurationOrDefault(settings: UserSettings): Int {
            // unfortunately there is no way to tell protobuf to use "2" as the default value
            return settings.sttSilenceDuration.takeIf { it > 0 } ?: DEFAULT_STT_SILENCE_DURATION
        }
    }
}

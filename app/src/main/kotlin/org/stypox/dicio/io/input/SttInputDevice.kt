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
        // Пользователь задумывается и говорит длинные фразы (боевые логи: дважды «не дослушал»).
        // 6 тихих интервалов не хватило; дефолт = новому максимуму (10). Дефолт фактический:
        // settings.pb на устройстве не создаётся (uninstall при каждой установке).
        const val DEFAULT_STT_SILENCE_DURATION = 10
        fun getSttSilenceDurationOrDefault(settings: UserSettings): Int {
            // unfortunately there is no way to tell protobuf to use "2" as the default value
            return settings.sttSilenceDuration.takeIf { it > 0 } ?: DEFAULT_STT_SILENCE_DURATION
        }
    }
}

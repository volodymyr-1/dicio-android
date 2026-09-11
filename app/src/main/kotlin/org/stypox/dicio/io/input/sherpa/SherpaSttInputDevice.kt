package org.stypox.dicio.io.input.sherpa

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Debug
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.util.DiagnosticsLog
import java.io.File
import javax.inject.Inject

/**
 * Ru-канал STT (V1 замер, docs/plan.md): Silero VAD + OfflineRecognizer zipformer-ru,
 * паттерн перенесён из smartnote voice-loop (VERIFIED на SM-T220). Правила из первоисточника:
 *  - модель копируется из assets в filesDir и грузится оттуда (не напрямую AssetManager);
 *  - фреймы 512 сэмплов (окно Silero), конверсия short→float /32768;
 *  - сегменты VAD копятся в реплику; пауза > END_GAP_MS = конец реплики → offline recognize;
 *  - «дверь микрофона»: во время TTS аудио не кормится (анти-эхо).
 */
class SherpaSttInputDevice @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    @Suppress("UNUSED_PARAMETER") okHttpClient: OkHttpClient,
) : SttInputDevice {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _state = MutableStateFlow<SttState>(SttState.NotLoaded)
    override val uiState: StateFlow<SttState> = _state

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var listeningActive = false
    @Volatile private var micMutedUntil = 0L

    private var currentListener: ((InputEvent) -> Unit)? = null

    private fun sampleRate(): Int = 16000
    private fun endGapMs(): Long = 2500L
    private fun modelDir(): String = "sherpa-onnx-zipformer-ru-int8-2025-04-20"

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        val s = _state.value
        if (s == SttState.NotLoaded || s is SttState.ErrorLoading) {
            if (thenStartListeningEventListener != null) {
                currentListener = thenStartListeningEventListener
            }
            load()
            return true
        } else if (thenStartListeningEventListener != null && s == SttState.Loaded) {
            currentListener = thenStartListeningEventListener
            startListening()
            return true
        } else if (thenStartListeningEventListener != null && s == SttState.Listening) {
            // слушаем непрерывно — просто обновляем получателя событий
            currentListener = thenStartListeningEventListener
            return true
        }
        return false
    }

    override fun stopListening() {
        listeningActive = false
        if (_state.value == SttState.Listening) {
            _state.value = SttState.Loaded
        }
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        currentListener = eventListener
        when (_state.value) {
            SttState.Loaded -> startListening()
            SttState.Listening -> stopListening()
            else -> load()
        }
    }

    override suspend fun destroy() {
        running = false
        try { audioRecord?.stop() } catch (_: Exception) { }
        audioRecord?.release()
        audioRecord = null
        try { recognizer?.release() } catch (_: Exception) { }
        recognizer = null
        try { vad?.release() } catch (_: Exception) { }
        vad = null
        _state.value = SttState.NotLoaded
    }

    private fun load() {
        _state.value = SttState.Loading(thenStartListening = currentListener != null)
        scope.launch {
            try {
                initRecognizer()
                initVad()
            } catch (e: Throwable) {
                DiagnosticsLog.log("STT-S", "ошибка загрузки: ${e.javaClass.simpleName} ${e.message}")
                _state.value = SttState.ErrorLoading(e)
                return@launch
            }
            _state.value = SttState.Loaded
            if (currentListener != null) {
                startListening()
            }
        }
    }

    private fun initRecognizer() {
        val files = listOf(
            "${modelDir()}/encoder.int8.onnx",
            "${modelDir()}/decoder.onnx",
            "${modelDir()}/joiner.int8.onnx",
            "${modelDir()}/tokens.txt",
            "${modelDir()}/bpe.model",
        )
        val paths = files.map { copyAsset(it) }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate(), featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = paths[0],
                    decoder = paths[1],
                    joiner = paths[2],
                ),
                tokens = paths[3],
                bpeVocab = paths[4],
                numThreads = 2,
                modelType = "zipformer",
            ),
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(assetManager = null, config = config)
        DiagnosticsLog.log("STT-S", "offline zipformer-ru загружен")
    }

    private fun initVad() {
        val cfg = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.3f,
                minSilenceDuration = 0.7f,
                minSpeechDuration = 0.3f,
                windowSize = 512,
                maxSpeechDuration = 40f,
            ),
            sampleRate = sampleRate(),
            numThreads = 1,
        )
        vad = Vad(assetManager = appContext.assets, config = cfg)
        DiagnosticsLog.log("STT-S", "Silero VAD загружен")
    }

    private fun copyAsset(path: String): String {
        val outFile = File(appContext.filesDir, path)
        if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath
        outFile.parentFile?.mkdirs()
        appContext.assets.open(path).use { input ->
            java.io.FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        return outFile.absolutePath
    }

    private fun startListening() {
        if (running) {
            listeningActive = true
            if (_state.value != SttState.Listening) _state.value = SttState.Listening
            return
        }
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate(), AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4,
            )
            audioRecord?.startRecording()
        } catch (e: Exception) {
            DiagnosticsLog.log("STT-S", "AudioRecord не открыт: ${e.message}")
            _state.value = SttState.ErrorLoading(e)
            return
        }
        running = true
        listeningActive = true
        _state.value = SttState.Listening

        thread = Thread {
            val frame = ShortArray(512)        // окно silero VAD
            val utter = ArrayList<Float>()     // накопленные сэмплы текущей реплики
            var lastVoice = 0L
            var hadSpeech = false
            while (running) {
                val record = audioRecord ?: break
                val ret = record.read(frame, 0, frame.size)
                if (ret <= 0) continue
                // «Дверь микрофона»: во время озвучки аудио не кормим (анти-эхо, smartnote)
                if (System.currentTimeMillis() < micMutedUntil) {
                    utter.clear(); hadSpeech = false; continue
                }
                val floats = FloatArray(ret) { frame[it] / 32768.0f }
                val currentVad = vad ?: break
                currentVad.acceptWaveform(floats)

                val now = System.currentTimeMillis()
                if (!currentVad.empty()) {
                    val seg = currentVad.front()
                    currentVad.clear()
                    if (seg.samples.isNotEmpty()) {
                        for (sample in seg.samples) utter.add(sample)
                        lastVoice = now
                        hadSpeech = true
                    }
                    continue
                }
                // тишина: если была речь и пауза длинная — реплика закончена
                if (hadSpeech && now - lastVoice > endGapMs()) {
                    val full = utter.toFloatArray()
                    utter.clear(); hadSpeech = false
                    if (full.isNotEmpty() && listeningActive) {
                        handleUtterance(full)
                    }
                }
            }
        }
        thread?.start()
    }

    private fun handleUtterance(samples: FloatArray) {
        val started = System.currentTimeMillis()
        val rec = recognizer ?: return
        val stream = rec.createStream()
        stream.acceptWaveform(samples, sampleRate())
        rec.decode(stream)
        val text = rec.getResult(stream).text.trim()
        stream.release()
        val elapsed = System.currentTimeMillis() - started
        val seconds = samples.size / sampleRate()
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        DiagnosticsLog.log(
            "STT-S",
            "распознано за ${elapsed} мс (${seconds} с аудио, ${memInfo.totalPss} МБ PSS): \"$text\""
        )
        if (text.isNotEmpty()) {
            currentListener?.invoke(InputEvent.Final(listOf(text to 1.0f)))
        }
    }

    /** Анти-эхо: во время озвучки не кормим VAD. Хвост 400 мс — паттерн smartnote. */
    fun muteMicForTts() {
        micMutedUntil = System.currentTimeMillis() + 400L
    }
}
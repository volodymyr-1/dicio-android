# Аудит устройства — Samsung Galaxy Tab A7 Lite (SM-T220)

Перенос из проекта-предшественника (smartnote), где аудит снят через ADB на реальном устройстве.

## Основное
| Параметр | Значение |
|---|---|
| Модель | Samsung Galaxy Tab A7 Lite Wi-Fi, SM-T220 |
| SoC | MediaTek MT8768WT (Helio P35), 8× Cortex-A53 ~2.3 ГГц |
| Android | 14 (One UI 6.1), SDK 34 |
| ABI | arm64-v8a (основной) |
| RAM | 2925 МБ total (~1336 МБ available под нагрузкой) |
| Экран | 800×1340, 213 dpi, landscape ~1340×800 при повороте |
| ADB | авторизован, serial R83W50BX0ME |

## Ограничения / риски (для нашего ассистента)
1. **RAM ~3 ГБ, Cortex-A53** — тяжёлые модели ASR/TTS непригодны; целевые ≤100 МБ.
2. **Samsung Doze + агрессивный фон** — wake-служба требует battery-optimization whitelist.
3. **Один микрофон, без DSP hotword** — wake word только программно (OpenWakeWord на CPU).
4. **Logcat не показывает `android.util.Log` приложения** — диагностика только через файл (ADR-6).

## Особенности деплоя
- Debug-сборка меняет applicationId по ветке (`org.stypox.dicio.<branch>`).
- Каждая CI-сборка подписана новым debug-ключом → перед установкой `adb uninstall`.
- Uninstall сбрасывает permissions (RECORD_AUDIO и др.) — выдавать заново.
- Чтение файла лога: `adb shell run-as org.stypox.dicio.<branch> cat files/diagnostics.log`.

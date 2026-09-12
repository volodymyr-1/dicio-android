# Карта ресурсов (reuse-first)

Регламент проекта: перед любой реализацией — сначала сверка с этой картой.
1. Есть готовое решение/алгоритм/словарь/движок → берём/адаптируем (источник указываем в комментарии к коду и в ADR).
2. Нет → пишем своё и сразу дописываем находку сюда.
Решения по выбору — только с согласованием пользователя (см. docs/architecture.md).

## 1. Готовые алгоритмы (наш собственный код)
| Ресурс | Что берём | Статус |
|---|---|---|
| `smartnote/voice-loop/core/ClothingAdvisor.kt` | совет «как одеться»: чистая функция advise(feels °C, maxProb %, wind км/ч) — 9 диапазонов + осадки + ветер; есть тесты | ✅ проверен в бою в smartnote |
| `smartnote/voice-loop/core/Router.kt` + `Similarity` | детерминированный роутер, отрицание/negative-guard | перенесено в `IntentRouter` |

## 2. Русские ассистенты (архитектура/фразы)
| Ресурс | Что берём |
|---|---|
| `Oknolaz/vasisualy` (`vasisualy/skills/*.py`) | структура навыков, формулировки ru-команд (погода с городом/без, будильник, напоминания) |
| **RHVoice** (F-Droid, Android TTS-движок) | качественный русский TTS — кандидат ADR-3 |
| **Supertonic-3 ru (sherpa-onnx TTS Engine)** | качественный ru-TTS, проверен в smartnote («гораздо лучше») — основной кандидат ADR-3 |
| **Silero STT v5 ru / Whisper tiny** | замены STT — кандидат ADR-2 (отложено) |

## 3. NLU/словари интентов
| Ресурс | Что берём |
|---|---|
| **Rasa** (методология `nlu.yml`: intent + примеры + fallback) | паттерны фраз/синонимов для роутера и навыков, fallback-реплики |
| **HF датасеты**: Common Voice (ru), Golos (Sber), Sova Speech | корпуса реплик (при обучении/расширении словарей) |
| **openWakeWord** + HA wake-words collection + training notebook | обучение wake-модели «ассистент» (ADR-4) |

## 4. Проверенные API (из smartnote-тестов, docs/tests/*)
| API | Данные | Примечание |
|---|---|---|
| **Open-Meteo** (api.open-meteo.com + geocoding-api) | feels_like, precipitation_probability, wind **км/ч**, геокодинг по-русски | ключей не требует; контракт 1:1 с ClothingAdvisor; VERIFIED (230 мс) |
| OpenWeatherMap (текущий в Dicio) | current: temp/feels/wind **м/с**; **нет** precipitation_probability | остаётся для экрана погоды |
| BBC Russian + Meduza RSS | новости | VERIFIED в smartnote (для будущих навыков) |
| OpenRouter (LLM) | спецвопросы | позже |

## 5. Правило по TTS/STT-движкам
Качественные ru-движки ставятся **на устройство** как системные (RHVoice/Supertonic через Android TTS API), код Dicio не меняем — см. ADR-3.

## 6. RealtimeSTT (KoljaB/RealtimeSTT, MIT, 10.1k★) — архитектурная шпаргалка
**Рантайм: Python/PC — код напрямую не переносим. Ценность = паттерны + готовый список sherpa-моделей.**

### 6.1 Ядро: двухпроходный STT (главный паттерн)
```
Аудио → VAD (Silero) → пока речь: быстрая streaming-модель → живые гипотезы (partial)
Сегмент завершён → авторитетный финальный проход крупной моделью по ПОЛНОМУ сегменту
```
Прод-рецепт от RealtimeSTT (оба — sherpa-onnx, как у нас):
- live: `sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8`
- final: `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8`
- CPU INT8 — рекомендовано для CPU-деплоя (наш случай SM-T220)

### 6.2 Карта переноса в наш проект
| Паттерн | Наш статус | Действие |
|---|---|---|
| Двухпроходный STT | частично (один offline-проход по сегменту) | «STT 2.0»: streaming-small-ru (28 МБ, уже в assets) → partial; offline zipformer → финал. Замер RAM на SM-T220 |
| Silero VAD | ✅ уже | — |
| Wake | ✅ текстовый | OpenWakeWord — запасной |
| Echo/buffer | ✅ дверь + echo-guard | — |

### 6.3 «STT 2.0» — план (отложено, после V2/T)
1. Подключить streaming-small-ru (уже в assets) как partial-движок → живой текст во время речи
2. Финал по сегменту — текущий offline zipformer (не меняется)
3. Замер: RAM (две модели одновременно — риск), латентность partial, качество финала
4. Кандидаты parakeet/nemotron int8 — только если качества не хватит

## 7. Регламент (обязателен перед любой реализацией)
```
1. Открыть этот файл → есть ли готовое решение/алгоритм/словарь/движок?
2. Есть → взять/адаптировать (источник — в комментарии кода и ADR).
3. Нет → писать своё и дописывать находку сюда.
Решения по выбору — только с согласованием пользователя.
```

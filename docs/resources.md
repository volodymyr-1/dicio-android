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

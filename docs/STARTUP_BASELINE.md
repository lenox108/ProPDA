# ForPDA — Startup Baseline (T-05)

> **Назначение:** зафиксировать baseline cold-start time **до** любых startup-оптимизаций (P-04 и др.), и иметь протокол для измерения дельты.

## Что измеряем

| Метрика | Источник | Что значит |
|---------|----------|-----------|
| `start_to_resumed_ms` | wall-clock от `am start` до `ResumedActivity` | Полный цикл от запуска интента до интерактивного UI. |
| `start_to_first_frame_ms` | `dumpsys activity processes` → `start=...ms` | Время до первого кадра. Лучший proxy для "user-visible cold start". |

## Когда используем

- **До** старта работ по P-04 (CookieManager off-main).
- **До** любых изменений в `App.onCreate()`, `MainActivity.onCreate()`, Hilt-графе, или порядка init'а зависимостей.
- **После** мержа таких изменений, для regress-check.

## Протокол baseline

### 0. Подготовка устройства

- **Один и тот же девайс** для всей серии (low-end emulator + flagship — две отдельные серии).
- **Один и тот же билд**, за исключением того, что меняем.
- **Cold boot** перед первой серией. Между ранами — `am force-stop` (это делает скрипт).
- **Стабильная сеть** (WiFi с известной скоростью, или отключенный радио для офлайн-измерения).
- **Батарея ≥ 50 %** (низкий заряд замедляет CPU).
- **Одинаковое окружение**: ночной режим, font scale, system theme — зафиксировать и не менять между сериями.

### 1. Снять baseline

```bash
# 10 cold starts + p50/p95
./scripts/startup-benchmark.sh ru.forpdateam.forpda 10

# С Perfetto-trace (для первой серии — чтобы было что открыть в ui.perfetto.dev)
./scripts/startup-benchmark.sh --perfetto ru.forpdateam.forpda 5
```

Сохранить вывод и `build/perf/<ts>-*-cold-start.csv` в `docs/perf/baselines/<date>-<sha>-<device>.md`.

### 2. Профилировать hot path (опционально)

StrictMode в debug-сборке (`App.kt:220-235`) уже включает `detectAll()`. При cold start в debug:

- Disk read на main thread → warning в logcat.
- Network на main thread → warning.
- `runBlocking` без явного диспатчера → warning (если попадёт под policy).

```bash
adb logcat -c
./scripts/startup-benchmark.sh ru.forpdateam.forpda.debug 1
adb logcat -d -s StrictMode > docs/perf/baselines/<date>-strictmode.log
```

> **Замечание:** в release StrictMode отключён, и этот путь в production-измерениях не работает. Плана на release-safe telemetry пока нет — см. "Open questions" ниже.

### 3. Зафиксировать baseline в коммите

Создать файл `docs/perf/baselines/YYYY-MM-DD-<sha>-<device>.md` с шаблоном:

```markdown
# Startup baseline — YYYY-MM-DD

- **Device:** <Pixel 4a, Android 14, API 34>
- **Build:** <debug / store>
- **Package:** <ru.forpdateam.forpda>
- **Commit:** <git sha>
- **Runs:** N

| Metric                     | min  | p50  | p95  | max  | stdev |
|----------------------------|------|------|------|------|-------|
| start_to_resumed_ms        | 740  | 812  | 905  | 1010 | 64    |
| start_to_first_frame_ms    | 420  | 478  | 532  | 590  | 41    |

## StrictMode findings (debug only)
- <список реальных находок>

## Notable slices (from Perfetto)
- Application creation: <N> ms
- MainActivity onCreate: <N> ms

## Open questions
- <что не покрыто>
```

### 4. После изменения — regress-check

```bash
# Применили изменение
./scripts/startup-benchmark.sh ru.forpdateam.forpda 10
```

Сравнить p50 / p95 с baseline. Допустимый шум ±10 %. Больше — ищем причину в git diff.

## Open questions / TODO

- **Release-safe telemetry.** StrictMode выключен в release. Сейчас нельзя собрать baseline на store-flavor. Возможные пути:
  - Lightweight in-process counter (например, `LongArray` под key события cold start) с `BuildConfig.DEBUG` gate для вывода в AppMetrica.
  - Macrobenchmark library (`androidx.benchmark:benchmark-macro-junit4`) — даёт AOT-cold-start, требует отдельного benchmark-модуля.
  - Оба варианта — out of scope для текущего PR. Зафиксировано в плане как Sprint 6 / C-09.

- **AVD vs физическое устройство.** Все числа нужно снимать на **одном и том же** устройстве между сериями. Не сравнивать emulator-с baseline физического.

- **TTID vs TTI.** `start_to_first_frame_ms` ближе к "Time To Initial Display". "Time To Interactive" (когда UI реально реагирует на ввод) — отдельная метрика, текущий скрипт её не покрывает.

## Связанные документы

- [`docs/PERF_DIAGNOSIS.md`](../PERF_DIAGNOSIS.md) — основной perf-диагноз. Этот документ — **дополнение** к нему, не замена.
- [`docs/AUDIT_REPORT.md`](../AUDIT_REPORT.md) §6 — perf-находки.
- [`docs/AUDIT_AND_ACTIONS.md`](../AUDIT_AND_ACTIONS.md) — T-05 в Sprint 3.
- [`scripts/startup-benchmark.README.md`](../scripts/startup-benchmark.README.md) — детали скрипта.
- [`scripts/battery-audit.README.md`](../scripts/battery-audit.README.md) — для battery-числа, не startup.

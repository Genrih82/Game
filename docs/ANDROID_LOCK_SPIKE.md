# Android lock spike (MVP)

## Триггеры после сна

| Событие | Где обрабатывается |
|---------|-------------------|
| `ACTION_SCREEN_ON` | Динамическая регистрация в `LockMonitorService` (с Android 8 нельзя надёжно ловить из манифеста) |
| `ACTION_USER_PRESENT` | Тот же receiver + запись в манифесте |
| `BOOT_COMPLETED` | `BootCompletedReceiver` → старт мониторинга, если lock включён |

Поток: событие → `LockController.onScreenAwake` → `repository.shouldPresentLock()` → `LockActivity`.

## Удержание поверх приложений

1. `LockActivity` — `singleTask`, `showWhenLocked`, флаги lock-screen.
2. `LearnLockAccessibilityService` — при смене окна, если сессия истекла, снова показывает задачу (без чтения контента).
3. Overlay permission — онбординг просит `SYSTEM_ALERT_WINDOW` (задел; текущий MVP стартует Activity).

## Сессия

`SessionStore` (DataStore) хранит `nextLaunchAtMillis`. Пока этот момент в будущем, задача не показывается. Ключ `unlocked_until` остаётся в файле и показом не управляет. Свободный промежуток начинается после `tasksToUnlock` верных ответов и длится `launchIntervalMinutes`.

## Проверка на устройстве

1. Включить блокировку + права.
2. Power off screen 30s → unlock → должна появиться задача.
3. Набрать нужное число верных ответов → уйти в Chrome → до `nextLaunchAt` LearnLock не должен мешать.
4. Дождаться записанного срока следующего запуска → снова задача.
5. Уйти с lock-экрана кнопкой Home при истёкшей сессии → Accessibility должен вернуть задачу.

Протестировать минимум на 2 прошивках (например Pixel + Xiaomi).

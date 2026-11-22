# LearnLock (MVP)

Android-приложение для детей и родителей: когда наступает следующий запуск, экран показывает учебную задачу (математика / чтение / логика). Телефон открывается после нужного числа верных ответов, затем действует пауза `launchIntervalMinutes`.

## Модули

| Модуль | Назначение |
|--------|------------|
| `task-engine` | Чистый Kotlin: генерация задач, сложность по возрасту. Без Android-зависимостей — задел под iOS/KMP. |
| `app` | Android UI (Compose), Room, сессии, lock после `SCREEN_ON` / `USER_PRESENT`, Accessibility helper |

## Требования

- Android Studio Ladybug+ / JDK 17+
- Android SDK 35
- minSdk 26

## Сборка

```bat
cd learnlock
gradlew.bat :task-engine:test
gradlew.bat :app:assembleDebug
```

Если локально есть JDK 17 в `.jdks\jdk-17`, можно указать:

```bat
set JAVA_HOME=%CD%\.jdks\jdk-17
gradlew.bat :task-engine:test
```

## Быстрый сценарий родителя

1. Открыть приложение → «Начать настройку» → задать PIN.
2. Выдать права: overlay, Accessibility, игнор оптимизации батареи.
3. В настройках задать возраст, микс категорий, интервал запуска и число заданий.
4. Включить «Блокировка после сна».
5. Проверить «Показать экран задачи».

## Ограничения MVP

На consumer Android без Device Owner блокировка — это **сильное трение**, не абсолютный kiosk. Возможные обходы и чеклист бета-теста: `docs/BETA_TEST.md`, `docs/BYPASS_NOTES.md`.

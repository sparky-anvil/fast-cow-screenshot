# CLR-002: Self-attach MockK падает из-за кириллицы в пути к Gradle-кэшу

| Поле | Значение |
|---|---|
| Статус | Closed |
| Адресат | Tech |
| Автор | Dev |
| Дата | 2026-10-29 |
| Задача | TASK-004 (шаг 0, тест №16) |
| Документы | CLR-001, TASK-004 шаг 0, `docs/tech/conventions.md` §9 |
| Блокирует задачу | Да |

## 1. Контекст

Флаги из ответа CLR-001 добавлены в `tasks.test` (`build.gradle.kts`). Тест №16 всё равно падает; остальные 48 проходят.

## 2. Проблема

Ошибка сменилась с `Could not self-attach` на ошибку загрузки агента. Путь к jar агента содержит кириллицу
(профиль пользователя `Александр`), и внешний процесс-аттачер не может открыть jar.

## 3. Что уже проверено

- `.\gradlew.bat build` после добавления флагов:

```
Error opening zip file or JAR manifest missing: C:\Users\Александр\.gradle\caches\modules-2\files-2.1\net.bytebuddy\byte-buddy-agent\1.18.2\...\byte-buddy-agent-1.18.2.jar
SettingsStoreTest > update keeps value in memory when save fails() FAILED
  Caused by: com.sun.tools.attach.AgentLoadException
49 tests completed, 1 failed
```

## 4. Варианты

| # | Вариант | Плюсы | Минусы / последствия | Затрагивает |
|---|---|---|---|---|
| 1 | Подключить агент MockK через `-javaagent:` в `tasks.test` (путь из `configurations.testRuntimeClasspath`, `mockk-agent`) | Не зависит от внешнего аттача; рекомендован MockK для JDK 21+ | Сложнее блок `tasks.test`; нужно проверить работу с кириллицей в пути | `build.gradle.kts`, conventions §9 |
| 2 | Пользователь задаёт `GRADLE_USER_HOME` с ASCII-путём (например `D:\gradle-home`) | Код не меняется; решает и другие проблемы с кириллицей | Проблема окружения каждого разработчика; кэш скачивается заново | Окружение |
| 3 | Заменить мок в тесте №16 фейком | Без правок сборки | Нельзя: класс final, тест из задачи | TASK-004 §11 |

Рекомендация Dev: вариант 1 (затем при необходимости 2). Решение принимает адресат.

## 5. Вопрос

Какой вариант применить? Если 1 — дайте точный фрагмент `build.gradle.kts`.

## 6. Ответ

> Заполняет адресат.

- **Решение:** вариант 1. Флаги self-attach удалить; в `tasks.test` в `doFirst` добавлять
  `-javaagent:<путь к byte-buddy-agent из classpath теста>` и `-Xshare:off`. Точный фрагмент — TASK-004 шаг 0.
  Проверено Tech через init-скрипт вне репозитория: `SettingsStoreTest` — 3 теста, 0 failures, путь кэша с кириллицей.
  Вариант 2 (`GRADLE_USER_HOME`) не нужен.
- **Изменённые документы:** TASK-004 (шаг 0, раздел 15), `docs/tech/conventions.md` §9 и §12.
- **Кто и когда:** Tech, 2026-10-29

## 7. Закрытие

> Заполняет Dev.

- **Как применено:** блок `tasks.test` заменён по TASK-004 шаг 0; сборка зелёная.
- **Дата закрытия:** 2026-10-29

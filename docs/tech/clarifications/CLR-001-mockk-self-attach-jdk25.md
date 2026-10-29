# CLR-001: MockK не может self-attach на JDK 25 — нужен флаг тестовой JVM

| Поле | Значение |
|---|---|
| Статус | Closed |
| Адресат | Tech |
| Автор | Dev |
| Дата | 2026-10-29 |
| Задача | TASK-004 (шаг 5, тест №16) |
| Документы | TASK-004 раздел 11, `docs/tech/conventions.md` раздел 7, `build.gradle.kts` |
| Блокирует задачу | Да |

## 1. Контекст

Реализованы все файлы TASK-004, тесты написаны. 48 из 49 тестов проходят; падает
`SettingsStoreTest` / `update keeps value in memory when save fails` (единственный тест с MockK).

## 2. Проблема

MockK создаёт мок финального класса через ByteBuddy agent и пытается self-attach к JVM. На JDK 25 это
запрещено по умолчанию. `build.gradle.kts` (не входит в «Область изменений» TASK-004) задаёт для тестов только `appJvmArgs`.

Цитата из документа:

> TASK-004, раздел 15: «`SettingsRepository` — обычный (не `open`) класс. MockK мокает final-классы без настроек.»

## 3. Что уже проверено

- `.\gradlew.bat build`:

```
Caused by: java.lang.IllegalStateException: Could not self-attach to current VM using external process
  at io.mockk.proxy.jvm.JvmMockKAgentFactory...
49 tests completed, 1 failed
```

- `build.gradle.kts`, строки 39–42: `tasks.test { useJUnitPlatform(); jvmArgs(appJvmArgs) }` — флага `-Djdk.attach.allowAttachSelf=true` нет.

## 4. Варианты

| # | Вариант | Плюсы | Минусы / последствия | Затрагивает |
|---|---|---|---|---|
| 1 | Добавить в `tasks.test` `jvmArgs("-Djdk.attach.allowAttachSelf=true")` (отдельно от `appJvmArgs`) | Тест остаётся как в задаче; нужен и всем будущим тестам с MockK | Правка `build.gradle.kts` (вне области TASK-004), вероятно, нужно обновить TASK-001/conventions | `build.gradle.kts`, conventions §7/§9 |
| 2 | Подключить mockk-agent как `-javaagent` в тестах | Нет предупреждений JDK | Сложнее конфигурация, нужна правка сборки | `build.gradle.kts` |
| 3 | Заменить мок в тесте №16 на ручной фейк | Сборка не меняется | Нельзя: `SettingsRepository` final, тест из задачи меняется | TASK-004 раздел 11 |

Рекомендация Dev: вариант 1. Решение принимает адресат.

## 5. Вопрос

Разрешаете ли добавить `-Djdk.attach.allowAttachSelf=true` в `tasks.test` в `build.gradle.kts` в рамках TASK-004 (вариант 1)?

## 6. Ответ

> Заполняет адресат.

- **Решение:** вариант 1, расширенный. В блок `tasks.test` `build.gradle.kts` отдельной строкой добавить
  `jvmArgs("-Djdk.attach.allowAttachSelf=true", "-XX:+EnableDynamicAgentLoading")`. Второй флаг убирает
  предупреждение JDK о динамической загрузке агента. `appJvmArgs` не менять. Правка входит в TASK-004 (шаг 0) и
  нужна всем последующим задачам с MockK (TASK-009, 010, 012, 014).
- **Изменённые документы:** TASK-004 (разделы 4, 5, 7 — шаг 0, 13, 15), `docs/tech/conventions.md` §9 и §12.
- **Кто и когда:** Tech, 2026-10-29

## 7. Закрытие

> Заполняет Dev.

- **Как применено:** флаги добавлены в `tasks.test`; тест всё ещё падает по другой причине — см. CLR-002.
- **Дата закрытия:** 2026-10-29

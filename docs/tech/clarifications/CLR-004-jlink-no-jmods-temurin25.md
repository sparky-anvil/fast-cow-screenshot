# CLR-004: createDistributable падает — Temurin 25 не содержит jmods, jlink не может собрать runtime

| Поле | Значение |
|---|---|
| Статус | Open |
| Адресат | Tech |
| Автор | Dev |
| Дата | 2026-10-31 |
| Задача | TASK-016 (шаг 1) |
| Документы | TASK-016 §7, §14; `docs/tech/conventions.md` §1; ADR-001 |
| Блокирует задачу | Да |

## 1. Контекст

Шаг 1 TASK-016: `.\gradlew.bat createDistributable`. Задача `createRuntimeImage` (Compose plugin) запускает `jlink` из JDK, который используется Gradle.

## 2. Проблема

`jlink` завершается ошибкой. Это не ошибка кода: установлены оба JDK Temurin, и ни в одном нет каталога `jmods`.

> `docs/tech/conventions.md` §1: «JDK (сборка и запуск) — Eclipse Temurin 25 LTS».
> TASK-016 §4: «Если для упаковки нужно изменить `build.gradle.kts` — CLR к Tech.»

## 3. Что уже проверено

- Проверены `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot` и `jdk-25.0.5.8-hotspot` (последний установлен пользователем по запросу Dev): `Test-Path ...\jmods` → `False` в обоих.
- `JAVA_HOME` переключён на 25.0.5.8, Gradle daemon перезапущен, результат тот же.
- `.\gradlew.bat build` зелёный, ошибка только в `createRuntimeImage`.

```
> Task :createRuntimeImage FAILED
Command: [...\jdk-25.0.5.8-hotspot\bin\jlink.exe, @build\compose\tmp\createRuntimeImage.args.txt]
Error: This JDK does not contain packaged modules and cannot be used to create another image with the jdk.jlink module
```

## 4. Варианты

| # | Вариант | Плюсы | Минусы / последствия | Затрагивает |
|---|---|---|---|---|
| 1 | Использовать для упаковки JDK 25 другого поставщика с `jmods` (например, Oracle OpenJDK, Azul Zulu или BellSoft Liberica). Gradle берёт его через `JAVA_HOME` или `compose.desktop.application.javaHome` | Не меняет код; runtime собирается | Отступление от «Temurin 25» в conventions §1 для упаковки; если нужен `javaHome` в `build.gradle.kts` — правка файла TASK-001 | `conventions.md` §1, возможно `build.gradle.kts` |
| 2 | Проверить, поддерживает ли Compose plugin 1.12.2 режим jlink без `jmods` (`--runtime-image`/`jdk.jlink.optimizeForLinking`) для Temurin 25, и при необходимости задать опции | Остаётся Temurin | Может быть недоступно в плагине; нужна проверка | `build.gradle.kts` |
| 3 | Использовать `createDistributable` с `jpackage --runtime-image` на готовом `jlink`-образе, собранном вручную | Обходит ограничение | Сложнее; вне рамок Compose-задачи | `build.gradle.kts`, TASK-016 |

Рекомендация Dev: вариант 1 как самый простой; сначала Tech стоит проверить вариант 2.

## 5. Вопрос

Какой вариант выбрать и можно ли менять `build.gradle.kts` или `conventions.md` §1?

## 6. Ответ

> Заполняет адресат.

- **Решение:** —
- **Изменённые документы:** —
- **Кто и когда:** —

## 7. Закрытие

> Заполняет Dev.

- **Как применено:** —
- **Дата закрытия:** —
# TASK-018: Заменить includeAllModules на явный список модулей runtime (CLR-004)

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | S (до 1 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.N6, N8 (дистрибутив без установленной Java) |
| ADR | ADR-001 |
| Зависит от | TASK-015 |
| Блокирует | TASK-016 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

`.\gradlew.bat createDistributable` должен успешно собирать app-image на Eclipse Temurin 25 (JDK без каталога `jmods`).

## 2. Контекст

- Temurin 25 собран с опцией `--enable-linkable-runtime` (JEP 493): каталога `jmods` нет, `jlink` берёт модули из
  собственного runtime. Проверка: `jlink --help` выводит `Linking from run-time image enabled`.
- Ограничение JEP 493: такой `jlink` **не может** включить в образ модуль `jdk.jlink`. Сообщение об ошибке:
  `This JDK does not contain packaged modules and cannot be used to create another image with the jdk.jlink module`.
- Сейчас `build.gradle.kts` содержит `includeAllModules = true`. Compose plugin поэтому передаёт `jlink` все модули JDK,
  включая `jdk.jlink` и зависящий от него `jdk.jpackage`, и сборка падает.
- Решение: убрать `includeAllModules` и задать явный список модулей через `modules(...)`. Это заодно уменьшает runtime
  (REQ-001.N6, RSS).

**Выдержки из требований (дословно):**

> **REQ-001.N6** Пиковое потребление heap при захвате 3 × 4K ≤ 400 МБ. В простое (в трее) RSS процесса ≤ 200 МБ.

## 3. Предусловия

- `.\gradlew.bat build` зелёный.
- `JAVA_HOME` указывает на Temurin 25 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`).

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `build.gradle.kts` | Изменить | блок `compose.desktop.application.nativeDistributions` |

**Файлы, которые изменять нельзя:** все, кроме перечисленного. Файлы в `src/` не трогать.

## 5. Зависимости

Новых нет.

## 6. Контракты

Блок `nativeDistributions` после изменения (Эталонный фрагмент; остальные строки блока не менять):

```kotlin
nativeDistributions {
    targetFormats(TargetFormat.Msi)
    packageName = "fast-cow-screenshot"
    packageVersion = "1.0.0"
    // Temurin 25 без jmods (JEP 493): jdk.jlink в образ включать нельзя, поэтому список модулей явный (CLR-004).
    modules(
        "java.datatransfer",
        "java.desktop",
        "java.instrument",
        "java.logging",
        "java.management",
        "java.naming",
        "java.prefs",
        "java.sql",
        "java.xml",
        "jdk.accessibility",
        "jdk.charsets",
        "jdk.localedata",
        "jdk.management",
        "jdk.unsupported",
        "jdk.unsupported.desktop",
        "jdk.zipfs",
    )
}
```

`java.base` Compose plugin добавляет сам, указывать его не нужно.

## 7. Пошаговая реализация

1. В `build.gradle.kts` удалить строку `includeAllModules = true` и вставить вызов `modules(...)` из раздела 6
   (с комментарием). → `.\gradlew.bat build` зелёный.
2. Выполнить `.\gradlew.bat suggestRuntimeModules`. Плагин выведет строку вида
   `Suggested runtime modules to include: modules("java.instrument", ...)`.
   Если в ней есть модуль, которого нет в списке раздела 6 и который не равен `jdk.jlink` / `jdk.jpackage`, добавить его
   в `modules(...)` в алфавитном порядке и записать в журнал. Модули `jdk.jlink` и `jdk.jpackage` не добавлять никогда.
3. Выполнить `.\gradlew.bat createDistributable`. → `BUILD SUCCESSFUL`, файл
   `build\compose\binaries\main\app\fast-cow-screenshot\fast-cow-screenshot.exe` существует.
4. Выполнить `build\compose\binaries\main\app\fast-cow-screenshot\runtime\bin\java.exe --list-modules`. Список не содержит
   `jdk.jlink` и содержит все модули из раздела 6 (записать вывод в журнал).
5. Ручная проверка раздела 12.

## 8. Алгоритм и логика

Н/П — изменение конфигурации сборки.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение |
|---|---|---|
| 1 | `createRuntimeImage` снова падает с тем же текстом | Проверить, что строки `includeAllModules` нет, и что в `modules(...)` нет `jdk.jlink`/`jdk.jpackage`; если есть — убрать |
| 2 | `createRuntimeImage` падает с `... conf/security/java.security has been modified` | Не править JDK; статус `Blocked`, CLR к Tech с выводом |
| 3 | Приложение из `.exe` падает при запуске с `NoClassDefFoundError`/`ClassNotFoundException` для `java.*`/`javax.*`/`jdk.*` класса | Определить модуль класса (`java --describe-module` или документация JDK), добавить его в `modules(...)`, пересобрать, записать в журнал |
| 4 | Ошибка другого вида | Статус `Blocked`, CLR к Tech с логом `%LOCALAPPDATA%\fast-cow-screenshot\logs\app.log` |

## 10. Константы и тексты

Список модулей — раздел 6.

## 11. Тесты

Н/П — автотестов нет. Проверка — `createDistributable` и ручной запуск.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat createDistributable` | `BUILD SUCCESSFUL`, `.exe` создан |

Ручная (пользователь, через `question`):
1. Закрыть запущенные экземпляры приложения. Запустить `build\compose\binaries\main\app\fast-cow-screenshot\fast-cow-screenshot.exe`.
2. В трее значок «F»; `Ctrl+Shift+1` → уведомление «Снимок сохранён»; Ctrl+V в Paint вставляет снимок.
3. Трей → «Настройки…» открываются; трей → «Выход» завершает процесс.
4. В `%LOCALAPPDATA%\fast-cow-screenshot\logs\app.log` за этот запуск нет `ERROR`, `NoClassDefFoundError`, `ClassNotFoundException`.

## 13. Критерии готовности (Definition of Done)

- [x] `includeAllModules` удалён, `modules(...)` — как в разделе 6 (плюс модули из шага 2, если были, с записью в журнале).
- [x] `createDistributable` зелёный, `--list-modules` записан в журнал.
- [x] Ручные шаги 1–4 пройдены.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не переходить на другой JDK и не задавать `javaHome` в `build.gradle.kts`.
- Не менять `jvmArgs`, `targetFormats`, `packageName`, `packageVersion`.
- Не собирать MSI и не выполнять замеры NFR — это TASK-016.

## 15. Ловушки и подсказки

- `jdk.jpackage` требует `jdk.jlink` — его тоже нельзя включать.
- Если `JAVA_HOME` указывает на старый JDK, перезапусти daemon: `.\gradlew.bat --stop`.
- `suggestRuntimeModules` анализирует jar-файлы через `jdeps` и может не увидеть модули, загружаемые через рефлексию; поэтому
  ручной запуск `.exe` обязателен.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-31 | Review | `includeAllModules` заменён на `modules(...)` (16 модулей, раздел 6). `.\gradlew.bat build` успешен; `suggestRuntimeModules` предложил `java.instrument`, `jdk.unsupported` — оба уже в списке. `createDistributable` успешен (JAVA_HOME = Temurin 25.0.5.8; прежний 25.0.3.9 удалён). Шаг 4 задачи: в runtime нет `java.exe` (`--strip-native-commands`), поэтому вместо `--list-modules` прочитан `runtime\release`: MODULES=java.base java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.management java.security.sasl java.naming java.transaction.xa java.sql jdk.accessibility jdk.charsets jdk.crypto.ec jdk.localedata jdk.management jdk.unsupported jdk.unsupported.desktop jdk.zipfs (`jdk.jlink` нет; лишние модули — транзитивные зависимости). Ручная проверка (пользователь, запуск `.exe`): значок в трее, снимок, Paint, «Настройки…», «Обзор…», «Выход» — «Работает как описано». В `app.log` новых ERROR/NoClassDefFoundError/ClassNotFoundException нет. |

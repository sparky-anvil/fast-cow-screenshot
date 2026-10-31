# TASK-020: Перенести версию в gradle.properties, подключить плагин релиза и задачу packageReleaseZip

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-002 |
| Требования | ADR-004 (запрос пользователя «настроить релиз-плагин и выпустить 0.0.1») |
| ADR | ADR-004, ADR-001 |
| Зависит от | TASK-016 |
| Блокирует | TASK-021, TASK-022 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Сделать `gradle.properties` единственным источником версии (`0.0.1-SNAPSHOT`), откуда её берут Gradle-проект,
`packageVersion` дистрибутива и `AppInfo.VERSION`. Подключить плагин `net.researchgate.release` 3.2.0 (тег `v<версия>`,
ветка `main`) и добавить Gradle-задачу `packageReleaseZip`, упаковывающую app-image в ZIP. Переименовать
исполняемый файл дистрибутива с `fast-cow-screenshot.exe` на `FastCScreen.exe` (`packageName = "FastCScreen"`).

## 2. Контекст

- Сейчас версия `1.0.0` записана в трёх местах: `build.gradle.kts` строка `version = "1.0.0"`, `build.gradle.kts`
  `packageVersion = "1.0.0"`, `AppInfo.kt` `const val VERSION: String = "1.0.0"`; тест `AppInfoTest` проверяет `"1.0.0"`.
- Gradle сам читает свойство `version` из `gradle.properties` корня, если в `build.gradle.kts` нет присваивания `version`.
- Плагин `net.researchgate.release` (задача `release`) при релизе переписывает строку `version=` в `gradle.properties`:
  снимает `-SNAPSHOT`, запускает `build`, коммитит, ставит тег, записывает следующую версию, коммитит и пушит.
  **В этой задаче `release` не запускать** — это TASK-022.
- jpackage (`packageVersion`) не принимает суффикс `-SNAPSHOT`, поэтому `packageVersion` = версия без `-SNAPSHOT`.
- Версия в коде нужна в рантайме (лог старта в `Main.kt`: `log.info("{} {} started, java {}", AppInfo.NAME, AppInfo.VERSION, …)`).
  Её даёт ресурс `app-info.properties`, в который Gradle при `processResources` подставляет версию.
- Дистрибутив `createDistributable` сейчас кладёт app-image в `build\compose\binaries\main\app\fast-cow-screenshot\`
  (внутри `fast-cow-screenshot.exe`, `app\`, `runtime\`). Имя папки, exe и файла `app\<имя>.cfg` задаёт
  `nativeDistributions.packageName`. По решению пользователя оно меняется на `FastCScreen`: после задачи app-image —
  `build\compose\binaries\main\app\FastCScreen\FastCScreen.exe`.
- Переименование касается **только дистрибутива**. Не меняются: `rootProject.name`, `AppInfo.NAME`
  (`"fast-cow-screenshot"` — от него зависят каталоги `%APPDATA%\fast-cow-screenshot` с настройками и
  `%LOCALAPPDATA%\fast-cow-screenshot\logs`), имя jar внутри `app\`, имя ZIP (`fast-cow-screenshot-<версия>-windows-x64.zip`).

**Выдержка из ADR-004 (дословно):**

> `net.researchgate.release` **3.2.0**, тег `v<version>` (`v0.0.1`), релизная ветка `main`;
> версия в `gradle.properties` (`version=0.0.1-SNAPSHOT`), единственный источник; `packageVersion` и
> `AppInfo.VERSION` выводятся из неё;
> артефакт — `fast-cow-screenshot-<version>-windows-x64.zip` с app-image; MSI не публикуется.

## 3. Предусловия

- Выполнены TASK-001 – TASK-019 (статус `Done`), в частности TASK-016 (`createDistributable` работает).
- Существуют: `gradle.properties` (2 строки: `kotlin.code.style=official`, `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8`),
  `gradle/libs.versions.toml`, `build.gradle.kts`, `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfo.kt`,
  `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfoTest.kt`, `src/main/resources/logback.xml`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `gradle.properties` | Изменить | Добавить строку `version=0.0.1-SNAPSHOT` |
| 2 | `gradle/libs.versions.toml` | Изменить | Версия `gradle-release` и плагин `researchgate-release` |
| 3 | `build.gradle.kts` | Изменить | Плагин, удалить `version = "1.0.0"`, `processResources`, `packageVersion`, `packageName = "FastCScreen"`, `packageReleaseZip`, `ReleaseExtension` |
| 4 | `src/main/resources/app-info.properties` | Создать | Одна строка `version=${version}` |
| 5 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfo.kt` | Изменить | `VERSION` из ресурса, функция `parseVersion` |
| 6 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfoTest.kt` | Изменить | Тесты из раздела 11 (старый тест `version is 1_0_0` удалить) |

**Файлы, которые изменять нельзя:** все, кроме перечисленных, а также этот файл задачи (только статус и журнал) и
`docs/tech/README.md` (только статус). В частности, не трогать `Main.kt`, `logback.xml`, `settings.gradle.kts`, `.gitignore`.

## 5. Зависимости

| Алиас в `libs.versions.toml` | Координаты | Версия | Где |
|---|---|---|---|
| `researchgate-release` (секция `[plugins]`) | плагин `net.researchgate.release` | `3.2.0` (проверено на plugins.gradle.org 2026-10-31) | `plugins { }` в `build.gradle.kts` |

Плагин скачивается с Gradle Plugin Portal — он уже есть в `settings.gradle.kts` (`pluginManagement.repositories.gradlePluginPortal()`).
Библиотечных зависимостей не добавлять.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot

import java.io.InputStream

/** Сведения о приложении. Потокобезопасен: значения неизменяемы, инициализируются один раз при загрузке объекта. */
internal object AppInfo {
    const val NAME: String = "fast-cow-screenshot"

    /** Путь ресурса с версией в classpath. */
    const val VERSION_RESOURCE: String = "/app-info.properties"

    /** Версия приложения из [VERSION_RESOURCE], например `0.0.1` или `0.0.2-SNAPSHOT`. */
    val VERSION: String = parseVersion(AppInfo::class.java.getResourceAsStream(VERSION_RESOURCE))

    /**
     * Читает свойство `version` из потока в формате `java.util.Properties` и закрывает поток.
     * @param input поток ресурса; `null`, если ресурс не найден
     * @return значение `version` без пробелов по краям
     * @throws IllegalStateException с сообщением [VERSION_MISSING_MESSAGE], если [input] равен `null`, свойства нет,
     *   оно пустое после trim или содержит `${` (подстановка Gradle не выполнилась)
     */
    internal fun parseVersion(input: InputStream?): String = TODO()
}
```

Константа сообщения: `private const val VERSION_MISSING_MESSAGE = "app-info.properties: version is missing"` — top-level
в `AppInfo.kt` (вне объекта, над ним).

## 7. Пошаговая реализация

### Шаг 1. Версия в `gradle.properties`
- **Файл:** `gradle.properties`
- **Сделать:** добавить третьей строкой `version=0.0.1-SNAPSHOT`. Ровно так: без пробелов вокруг `=`, без комментариев.
- **Результат:** файл из 3 строк.

### Шаг 2. Плагин в version catalog
- **Файл:** `gradle/libs.versions.toml`
- **Сделать:** в `[versions]` последней строкой добавить `gradle-release = "3.2.0"`; в `[plugins]` последней строкой —
  `researchgate-release = { id = "net.researchgate.release", version.ref = "gradle-release" }`.

### Шаг 3. `build.gradle.kts` — плагин и версия
- **Файл:** `build.gradle.kts`
- **Сделать:**
  1. Второй строкой импорта (после `import org.jetbrains.compose.desktop.application.dsl.TargetFormat`) добавить
     `import net.researchgate.release.ReleaseExtension`.
  2. В блок `plugins { }` последней строкой добавить `alias(libs.plugins.researchgate.release)`.
  3. Удалить строку `version = "1.0.0"`. Строку `group = …` оставить.
  4. Сразу после строки `group = …` добавить:
     ```kotlin
     // Версия задаётся в gradle.properties (ADR-004) и правится плагином net.researchgate.release.
     val appVersion: String = project.version.toString()
     val appPackageVersion: String = appVersion.removeSuffix("-SNAPSHOT")
     ```
  5. В `nativeDistributions` заменить `packageVersion = "1.0.0"` на `packageVersion = appPackageVersion`.
  6. В `nativeDistributions` заменить `packageName = "fast-cow-screenshot"` на `packageName = "FastCScreen"`.
- **Результат:** `.\gradlew.bat help` → `BUILD SUCCESSFUL`.

### Шаг 4. Подстановка версии в ресурс
- **Файл:** `src/main/resources/app-info.properties` — создать с одной строкой `version=${version}` и переводом строки.
- **Файл:** `build.gradle.kts` — после блока `tasks.test { … }` добавить:
  ```kotlin
  tasks.processResources {
      inputs.property("appVersion", appVersion)
      filesMatching("app-info.properties") {
          expand(mapOf("version" to appVersion))
      }
  }
  ```
- **Результат:** `.\gradlew.bat processResources` → файл `build\resources\main\app-info.properties` содержит
  `version=0.0.1-SNAPSHOT`.

### Шаг 5. `AppInfo`
- **Файл:** `AppInfo.kt` — реализовать контракт раздела 6 по алгоритму раздела 8.
- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 6. Тесты
- **Файл:** `AppInfoTest.kt` — удалить тест `` `version is 1_0_0`() ``, оставить `` `app name is fast-cow-screenshot`() ``,
  добавить тесты раздела 11.
- **Результат:** `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.AppInfoTest"` проходит.

### Шаг 7. Задача `packageReleaseZip`
- **Файл:** `build.gradle.kts` — в конец файла добавить:
  ```kotlin
  // ZIP app-image для GitHub Release (ADR-004, TASK-021).
  tasks.register<Zip>("packageReleaseZip") {
      group = "distribution"
      description = "Packs the app-image into build/release/fast-cow-screenshot-<version>-windows-x64.zip"
      dependsOn("createDistributable")
      from(layout.buildDirectory.dir("compose/binaries/main/app"))
      archiveFileName.set("fast-cow-screenshot-$appVersion-windows-x64.zip")
      destinationDirectory.set(layout.buildDirectory.dir("release"))
  }
  ```
- **Результат:** `.\gradlew.bat packageReleaseZip` создаёт `build\release\fast-cow-screenshot-0.0.1-SNAPSHOT-windows-x64.zip`.

### Шаг 8. Настройка плагина релиза
- **Файл:** `build.gradle.kts` — в конец файла добавить:
  ```kotlin
  // Релиз: .\gradlew.bat release (TASK-022). Тег v<версия>, только с ветки main (ADR-004).
  configure<ReleaseExtension> {
      tagTemplate.set("v\${version}")
      with(git) {
          requireBranch.set("main")
      }
  }
  ```
- **Результат:** `.\gradlew.bat help --task release` → `BUILD SUCCESSFUL`, в выводе есть `Path` `:release`.

## 8. Алгоритм и логика

`parseVersion(input)`:
1. Если `input == null` → `throw IllegalStateException(VERSION_MISSING_MESSAGE)`.
2. `val props = Properties()`; `input.use { props.load(it) }` (поток закрывается).
3. `val value = props.getProperty("version")?.trim()`.
4. Если `value == null`, `value.isEmpty()` или `value.contains("\${")` → `throw IllegalStateException(VERSION_MISSING_MESSAGE)`.
5. Вернуть `value`.

Пример: байты `"version=0.0.1\n"` → `props["version"] = "0.0.1"` → `"0.0.1"`.
Пример `packageVersion`: `appVersion = "0.0.1-SNAPSHOT"` → `appPackageVersion = "0.0.1"`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователю |
|---|---|---|---|---|
| 1 | Ресурса нет | `IllegalStateException("app-info.properties: version is missing")` при первом обращении к `AppInfo` | нет (исключение уходит наверх) | Приложение не стартует |
| 2 | Нет ключа `version` / пустое значение | То же | нет | То же |
| 3 | Значение `${version}` (не подставлено) | То же | нет | То же |
| 4 | `version` в `gradle.properties` без `-SNAPSHOT` | `appPackageVersion == appVersion` | — | — |

## 10. Константы и тексты

| Имя | Тип | Значение | Где |
|---|---|---|---|
| `VERSION_RESOURCE` | `String` | `"/app-info.properties"` | `AppInfo` |
| `VERSION_MISSING_MESSAGE` | `String` | `"app-info.properties: version is missing"` | top-level `private const` в `AppInfo.kt` |
| версия | — | `0.0.1-SNAPSHOT` | `gradle.properties` |
| тег | — | `v${version}` | `build.gradle.kts` |
| имя ZIP | — | `fast-cow-screenshot-$appVersion-windows-x64.zip` | `build.gradle.kts` |
| `packageName` | — | `FastCScreen` | `build.gradle.kts`, `nativeDistributions` |

## 11. Тесты

Файл: `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfoTest.kt`. Поток для `parseVersion` —
`"…".byteInputStream()` (ISO-8859-1-совместимые ASCII-строки).

| # | Метод | Given | When | Then |
|---|---|---|---|---|
| 1 | `` `app name is fast-cow-screenshot`() `` | — | `AppInfo.NAME` | `"fast-cow-screenshot"` (существующий тест) |
| 2 | `` `version from resources is semver with optional snapshot`() `` | собранный ресурс | `AppInfo.VERSION` | соответствует `Regex("""^\d+\.\d+\.\d+(-SNAPSHOT)?$""")` |
| 3 | `` `parses version`() `` | `"version=0.0.1\n"` | `parseVersion` | `"0.0.1"` |
| 4 | `` `trims version value`() `` | `"version = 0.0.2-SNAPSHOT   \n"` | `parseVersion` | `"0.0.2-SNAPSHOT"` |
| 5 | `` `fails when resource is missing`() `` | `null` | `parseVersion(null)` | `assertFailsWith<IllegalStateException>`, `message == "app-info.properties: version is missing"` |
| 6 | `` `fails when version key is absent`() `` | `"name=x\n"` | `parseVersion` | `IllegalStateException`, то же сообщение |
| 7 | `` `fails when version is blank`() `` | `"version=   \n"` | `parseVersion` | `IllegalStateException` |
| 8 | `` `fails when placeholder was not expanded`() `` | `"version=\${version}\n"` | `parseVersion` | `IllegalStateException` |

Моков нет.

## 12. Проверка

**Автоматическая:**

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.AppInfoTest"` | 8 тестов проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |
| `Get-Content build\resources\main\app-info.properties` | `version=0.0.1-SNAPSHOT` |
| `.\gradlew.bat packageReleaseZip` | `BUILD SUCCESSFUL`; существует `build\release\fast-cow-screenshot-0.0.1-SNAPSHOT-windows-x64.zip` |
| `Test-Path build\compose\binaries\main\app\FastCScreen\FastCScreen.exe` | `True` |
| `Test-Path build\compose\binaries\main\app\FastCScreen\app\FastCScreen.cfg` | `True` |
| `Expand-Archive build\release\fast-cow-screenshot-0.0.1-SNAPSHOT-windows-x64.zip -DestinationPath $env:TEMP\fcs-zip-check -Force; Test-Path "$env:TEMP\fcs-zip-check\FastCScreen\FastCScreen.exe"` | `True` |
| `.\gradlew.bat help --task release` | `BUILD SUCCESSFUL`, в выводе `:release` |
| `git status --short` | изменены/созданы только 6 файлов раздела 4 (+ файл задачи и `docs/tech/README.md`) |

**Ручная:**
1. Запустить `$env:TEMP\fcs-zip-check\FastCScreen\FastCScreen.exe` → значок в трее; в Диспетчере задач процесс
   называется `FastCScreen.exe`.
2. Открыть `%LOCALAPPDATA%\fast-cow-screenshot\logs\app.log` → последняя строка запуска содержит
   `fast-cow-screenshot 0.0.1-SNAPSHOT started`. Закрыть приложение через «Выход» в трее.

## 13. Критерии готовности (Definition of Done)

- [ ] Все шаги раздела 7 выполнены.
- [ ] Сигнатуры совпадают с разделом 6.
- [ ] Случаи раздела 9 обработаны.
- [ ] 8 тестов раздела 11 проходят.
- [ ] Проверки раздела 12 выполнены, результат в журнале.
- [ ] Строка `1.0.0` больше не встречается в `build.gradle.kts`, `AppInfo.kt`, `AppInfoTest.kt`.
- [ ] В `build.gradle.kts` `packageName = "FastCScreen"`; `AppInfo.NAME` и `rootProject.name` не изменены.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- **Не запускать** `.\gradlew.bat release` и не создавать git-теги — это TASK-022.
- Не менять `targetFormats(TargetFormat.Msi)`, список `modules(...)`, `appJvmArgs`, блок `tasks.test`.
- Не менять `AppInfo.NAME`, `rootProject.name`, имя ZIP, пути к настройкам и логам — переименовывается только дистрибутив.
- Не добавлять других плагинов и зависимостей.
- Не создавать `.github/workflows/` — это TASK-021.
- Не заменять `expand` на генерацию Kotlin-исходника или `BuildConfig`-плагин.

## 15. Ловушки и подсказки

- `expand` применять **только** к `app-info.properties` через `filesMatching`: в `logback.xml` есть свои `${…}`,
  глобальный `expand` сломает его или упадёт сборка.
- В Kotlin-строке `"v\${version}"` знак `$` экранирован: плагин должен получить буквальный шаблон `v${version}` и сам
  подставит версию. Без `\` Kotlin подставит `project.version` на этапе конфигурации — тег будет `v0.0.1-SNAPSHOT`.
- В тесте 8 строка `"version=\${version}\n"` — тоже с экранированным `$`.
- `gradle.properties` плагин читает в ISO-8859-1: не добавлять в него кириллицу и комментарии.
- `inputs.property("appVersion", appVersion)` обязателен: без него после смены версии `processResources` будет `UP-TO-DATE`
  и в ресурсе останется старая версия.
- `from(layout.buildDirectory.dir("compose/binaries/main/app"))` — именно каталог `app`, а не `app/FastCScreen`:
  в ZIP верхним уровнем должна быть папка `FastCScreen\`.
- После смены `packageName` в `build\compose\binaries\main\app\` может остаться старая папка `fast-cow-screenshot\`
  от прошлых сборок, и она попадёт в ZIP. Перед проверкой раздела 12 выполнить `.\gradlew.bat clean`.
- `*.zip` и `build/` уже в `.gitignore` — архив в git не попадёт; `.gitignore` не менять.
- Если `alias(libs.plugins.researchgate.release)` или `configure<ReleaseExtension>` не компилируется на Gradle 9.8.1
  (ошибка резолва плагина, `Unresolved reference`), не подбирать другую версию: статус `Blocked`, CLR к Tech с полным
  текстом ошибки.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

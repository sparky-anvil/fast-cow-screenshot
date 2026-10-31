# TASK-001: Создать каркас Gradle-проекта (Kotlin 2.4, JDK 25, Compose Desktop, JNA, логирование, тесты)

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001 §7 (десктопное JVM-приложение на Kotlin), REQ-001.N8 |
| ADR | ADR-001, ADR-003 |
| Зависит от | — |
| Блокирует | TASK-002, TASK-003, TASK-005 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Сейчас в репозитории нет ни сборки, ни кода. Нужно создать собираемый Gradle-проект: wrapper 9.8.1, version catalog
со всеми зависимостями проекта, точку входа `main()`, конфигурацию логирования и один тест. Так проверяется тестовая
инфраструктура. После задачи `.\gradlew.bat build` проходит, а `.\gradlew.bat run` пишет строку запуска в лог и завершается.

## 2. Контекст

- Приложение — фоновая программа для Windows 10/11 на Kotlin/JVM. UI сделан на Compose Multiplatform Desktop,
  доступ к WinAPI — через JNA. Это решения ADR-001 и ADR-002, они уже приняты.
- Gradle на машине **не установлен**. Wrapper создаётся одноразовым дистрибутивом Gradle из временной папки (шаг 2).
- В корне уже есть файлы IntelliJ (`.idea/`, `fast-cow-screenshot.iml`), `README.md`, `LICENSE`, `.gitignore`.
  `.gitignore` игнорирует `*.jar`, поэтому `gradle-wrapper.jar` нужно явно разрешить (шаг 8).
- JDK: Temurin 25 установлен (`java -version` → `openjdk version "25.0.3"`).

**Выдержки из требований (дословно):**

> **REQ-001 §7** Допущение: приложение — десктопное JVM-приложение на Kotlin, работает в фоне и имеет значок в системном трее.
>
> **REQ-001.N8** Windows 10 22H2 x64 и Windows 11 (22H2 и новее) x64.

## 3. Предусловия

- `java -version` показывает версию 25.
- Есть доступ в интернет (Maven Central, Gradle Plugin Portal, Google Maven, services.gradle.org).
- Если `java -version` не 25 или сети нет — это проблема окружения: CLR к пользователю.

## 4. Область изменений

| # | Файл (от корня репозитория) | Действие | Что именно |
|---|---|---|---|
| 1 | `settings.gradle.kts` | Создать | Репозитории, имя проекта |
| 2 | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` | Создать (генерирует Gradle) | Wrapper 9.8.1 |
| 3 | `gradle.properties` | Создать | Свойства сборки |
| 4 | `gradle/libs.versions.toml` | Создать | Version catalog |
| 5 | `build.gradle.kts` | Создать | Плагины, зависимости, тесты, Compose application |
| 6 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfo.kt` | Создать | `AppInfo` |
| 7 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/Main.kt` | Создать | `main()` |
| 8 | `src/main/resources/logback.xml` | Создать | Логирование |
| 9 | `src/test/resources/logback-test.xml` | Создать | Логирование в тестах |
| 10 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfoTest.kt` | Создать | Тест раздела 11 |
| 11 | `.gitignore` | Изменить | Добавить блок Gradle в конец |

**Файлы, которые изменять нельзя:** все остальные, в том числе `.idea/`, `fast-cow-screenshot.iml`, `README.md`, `docs/`.

## 5. Зависимости

Все зависимости проекта объявляются в catalog **сейчас**. Подключаются в `build.gradle.kts` тоже сейчас: последующие
задачи `build.gradle.kts` не меняют.

| Алиас в `libs.versions.toml` | Координаты | Версия | Конфигурация |
|---|---|---|---|
| `compose-desktop-windows` | `org.jetbrains.compose.desktop:desktop-jvm-windows-x64` | 1.12.2 | `implementation` |
| `compose-material` | `org.jetbrains.compose.material:material` | 1.12.2 | `implementation` |
| `jna` | `net.java.dev.jna:jna` | 5.19.1 | `implementation` |
| `jna-platform` | `net.java.dev.jna:jna-platform` | 5.19.1 | `implementation` |
| `kotlinx-serialization-json` | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | `implementation` |
| `slf4j-api` | `org.slf4j:slf4j-api` | 2.0.17 | `implementation` |
| `logback-classic` | `ch.qos.logback:logback-classic` | 1.5.38 | `implementation` |
| `junit-bom` | `org.junit:junit-bom` | 6.1.3 | `testImplementation(platform(...))` |
| `junit-jupiter` | `org.junit.jupiter:junit-jupiter` | из BOM | `testImplementation` |
| `junit-platform-launcher` | `org.junit.platform:junit-platform-launcher` | из BOM | `testRuntimeOnly` |
| `kotlin-test` | `org.jetbrains.kotlin:kotlin-test` | 2.4.21 | `testImplementation` |
| `mockk` | `io.mockk:mockk` | 1.14.11 | `testImplementation` |

Плагины:

| Алиас | id | Версия |
|---|---|---|
| `kotlin-jvm` | `org.jetbrains.kotlin.jvm` | 2.4.21 |
| `kotlin-compose` | `org.jetbrains.kotlin.plugin.compose` | 2.4.21 |
| `kotlin-serialization` | `org.jetbrains.kotlin.plugin.serialization` | 2.4.21 |
| `compose-multiplatform` | `org.jetbrains.compose` | 1.12.2 |

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot

/** Константы приложения. Потокобезопасен (только константы). */
internal object AppInfo {
    const val NAME: String = "fast-cow-screenshot"
    const val VERSION: String = "1.0.0"
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot

/** Точка входа. В этой задаче только пишет строку запуска в лог и завершается. */
fun main()
```

## 7. Пошаговая реализация

### Шаг 1. settings.gradle.kts
- **Файл:** `settings.gradle.kts`
- **Сделать:** ровно это содержимое:
  ```kotlin
  pluginManagement {
      repositories {
          gradlePluginPortal()
          mavenCentral()
          google()
      }
  }

  dependencyResolutionManagement {
      repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
      repositories {
          mavenCentral()
          google()
      }
  }

  rootProject.name = "fast-cow-screenshot"
  ```
- **Результат:** файл создан. `build.gradle.kts` на этом шаге ещё **не создавать**: wrapper генерируется по пустой сборке.

### Шаг 2. Сгенерировать Gradle wrapper
- **Сделать:** выполнить в PowerShell из корня репозитория (по одной команде):
  ```powershell
  $zip = "$env:TEMP\gradle-9.8.1-bin.zip"
  Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-9.8.1-bin.zip" -OutFile $zip
  (Get-FileHash $zip -Algorithm SHA256).Hash
  ```
  Хеш должен быть `DCE76F55F8E251A3A1F130EB120F30B3D271DE2B76C9B0729D316B5A1B6DC01F`. Если он другой — остановиться,
  CLR к пользователю. Далее:
  ```powershell
  Expand-Archive -Path $zip -DestinationPath "$env:TEMP\gradle-bootstrap" -Force
  & "$env:TEMP\gradle-bootstrap\gradle-9.8.1\bin\gradle.bat" wrapper --gradle-version 9.8.1 --distribution-type bin --gradle-distribution-sha256-sum dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f
  ```
- **Результат:** появились `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
  `gradle/wrapper/gradle-wrapper.properties`. В нём строки
  `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.1-bin.zip` и `distributionSha256Sum=dce76f…`.
  Команда `.\gradlew.bat --version` печатает `Gradle 9.8.1`.

### Шаг 3. gradle.properties
- **Файл:** `gradle.properties`
- **Сделать:**
  ```properties
  kotlin.code.style=official
  org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
  ```

### Шаг 4. Version catalog
- **Файл:** `gradle/libs.versions.toml`
- **Сделать:**
  ```toml
  [versions]
  kotlin = "2.4.21"
  compose-multiplatform = "1.12.2"
  jna = "5.19.1"
  kotlinx-serialization = "1.11.0"
  slf4j = "2.0.17"
  logback = "1.5.38"
  junit = "6.1.3"
  mockk = "1.14.11"

  [libraries]
  compose-desktop-windows = { module = "org.jetbrains.compose.desktop:desktop-jvm-windows-x64", version.ref = "compose-multiplatform" }
  compose-material = { module = "org.jetbrains.compose.material:material", version.ref = "compose-multiplatform" }
  jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
  jna-platform = { module = "net.java.dev.jna:jna-platform", version.ref = "jna" }
  kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
  slf4j-api = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }
  logback-classic = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
  junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
  junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
  junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
  kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
  mockk = { module = "io.mockk:mockk", version.ref = "mockk" }

  [plugins]
  kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
  kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
  kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
  compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
  ```

### Шаг 5. build.gradle.kts
- **Файл:** `build.gradle.kts`
- **Сделать:**
  ```kotlin
  import org.jetbrains.compose.desktop.application.dsl.TargetFormat

  plugins {
      alias(libs.plugins.kotlin.jvm)
      alias(libs.plugins.kotlin.compose)
      alias(libs.plugins.kotlin.serialization)
      alias(libs.plugins.compose.multiplatform)
  }

  group = "by.sparkyanvil.fastcowscreenshot"
  version = "1.0.0"

  kotlin {
      jvmToolchain(25)
  }

  dependencies {
      implementation(libs.compose.desktop.windows)
      implementation(libs.compose.material)
      implementation(libs.jna)
      implementation(libs.jna.platform)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.slf4j.api)
      implementation(libs.logback.classic)

      testImplementation(platform(libs.junit.bom))
      testImplementation(libs.junit.jupiter)
      testImplementation(libs.kotlin.test)
      testImplementation(libs.mockk)
      testRuntimeOnly(libs.junit.platform.launcher)
  }

  val appJvmArgs = listOf(
      "--enable-native-access=ALL-UNNAMED",
      "--sun-misc-unsafe-memory-access=allow",
      "-Xmx768m",
  )

  tasks.test {
      useJUnitPlatform()
      jvmArgs(appJvmArgs)
  }

  compose.desktop {
      application {
          mainClass = "by.sparkyanvil.fastcowscreenshot.MainKt"
          jvmArgs += appJvmArgs
          nativeDistributions {
              targetFormats(TargetFormat.Msi)
              packageName = "fast-cow-screenshot"
              packageVersion = "1.0.0"
              includeAllModules = true
          }
      }
  }
  ```
- **Результат:** `.\gradlew.bat help` → `BUILD SUCCESSFUL` (зависимости и плагины разрешаются).

### Шаг 6. AppInfo и Main
- **Файлы:** `AppInfo.kt`, `Main.kt` (пути — раздел 4).
- **Сделать:** `AppInfo` — ровно как в разделе 6. `Main.kt`:
  - top-level `private val log = LoggerFactory.getLogger("by.sparkyanvil.fastcowscreenshot.Main")`;
  - `fun main()` выполняет одну строку
    `log.info("{} {} started, java {}", AppInfo.NAME, AppInfo.VERSION, System.getProperty("java.version"))`.
- **Результат:** `.\gradlew.bat compileKotlin` → `BUILD SUCCESSFUL`.

### Шаг 7. Конфигурация логирования
- **Файл:** `src/main/resources/logback.xml`:
  ```xml
  <configuration>
      <property name="LOG_DIR" value="${LOCALAPPDATA:-${user.home}}/fast-cow-screenshot/logs"/>
      <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
          <encoder>
              <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
          </encoder>
      </appender>
      <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
          <file>${LOG_DIR}/app.log</file>
          <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
              <fileNamePattern>${LOG_DIR}/app.%d{yyyy-MM-dd}.log</fileNamePattern>
              <maxHistory>14</maxHistory>
          </rollingPolicy>
          <encoder>
              <charset>UTF-8</charset>
              <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
          </encoder>
      </appender>
      <logger name="by.sparkyanvil.fastcowscreenshot" level="DEBUG"/>
      <root level="INFO">
          <appender-ref ref="CONSOLE"/>
          <appender-ref ref="FILE"/>
      </root>
  </configuration>
  ```
- **Файл:** `src/test/resources/logback-test.xml` — то же без `FILE`-аппендера и без `property`: только `CONSOLE`,
  `logger` уровня `DEBUG` и `root` уровня `INFO` со ссылкой на `CONSOLE`.

### Шаг 8. .gitignore
- **Файл:** `.gitignore`
- **Сделать:** дописать в конец (после пустой строки):
  ```
  # Gradle
  .gradle/
  build/
  !gradle/wrapper/gradle-wrapper.jar
  ```
- **Результат:** `git status` показывает `gradle/wrapper/gradle-wrapper.jar` как новый файл; `build/` и `.gradle/` не видны.

### Шаг 9. Тест
- **Файл:** `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfoTest.kt` — по разделу 11.
- **Результат:** `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 8. Алгоритм и логика

Н/П — логики нет, только конфигурация.

## 9. Ошибки и граничные случаи

| # | Ситуация | Ожидаемое поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | Хеш дистрибутива Gradle не совпал | Остановиться, ничего не генерировать | — | CLR к пользователю |
| 2 | `jvmToolchain(25)`: «No matching toolchains found» | Не менять версию; проверить `JAVA_HOME` | — | CLR к пользователю (окружение) |
| 3 | Не разрешается артефакт/плагин из раздела 5 | Не менять версию/координаты | — | CLR к Tech с текстом ошибки |
| 4 | Предупреждения компилятора | Допустимы, сборку не валят | — | — |

## 10. Константы и тексты

| Имя | Тип | Значение | Где объявить |
|---|---|---|---|
| `AppInfo.NAME` | `String` | `"fast-cow-screenshot"` | `AppInfo.kt` |
| `AppInfo.VERSION` | `String` | `"1.0.0"` | `AppInfo.kt` |

Текстов UI нет.

## 11. Тесты

Файл: `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/AppInfoTest.kt`, класс `AppInfoTest`.

| # | Метод теста | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `` `app name is fast-cow-screenshot`() `` | — | читаем `AppInfo.NAME` | `assertEquals("fast-cow-screenshot", AppInfo.NAME)` | инфраструктура |
| 2 | `` `version is 1_0_0`() `` | — | читаем `AppInfo.VERSION` | `assertEquals("1.0.0", AppInfo.VERSION)` | инфраструктура |

Импорты: `org.junit.jupiter.api.Test`, `kotlin.test.assertEquals`. Моков нет.

## 12. Проверка

**Автоматическая:**

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat --version` | `Gradle 9.8.1`, `Launcher JVM: 25…` |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL`, 2 теста пройдены |
| `.\gradlew.bat run` (таймаут 120 с) | В консоли строка `INFO … fast-cow-screenshot 1.0.0 started, java 25…`, процесс завершается сам |

**Ручная:** проверить, что появился `%LOCALAPPDATA%\fast-cow-screenshot\logs\app.log` со строкой запуска.

## 13. Критерии готовности (Definition of Done)

- [x] Все шаги раздела 7 выполнены.
- [x] Сигнатуры совпадают с разделом 6.
- [x] Все тесты раздела 11 написаны и проходят.
- [x] Проверки раздела 12 выполнены.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.
- [x] `gradle-wrapper.jar` не игнорируется git.

## 14. Чего НЕ делать

- Не добавлять зависимости, плагины и репозитории сверх раздела 5 и шага 1. Не менять версии.
- Не создавать пакеты и классы, кроме `AppInfo` и `Main.kt`.
- Не включать configuration cache, ktlint, detekt.
- Не удалять и не менять `.idea/` и `fast-cow-screenshot.iml`.
- Не коммитить временную папку `%TEMP%\gradle-bootstrap`.

## 15. Ловушки и подсказки

- `gradle wrapper` запускать **до** создания `build.gradle.kts`. Иначе он попытается разрешить плагины, а без
  catalog это закончится ошибкой.
- В Gradle 9 JUnit Platform Launcher не добавляется автоматически. Без `testRuntimeOnly(libs.junit.platform.launcher)`
  тесты падают с ошибкой `Failed to load JUnit Platform`.
- Алиас `compose-desktop-windows` в Kotlin DSL — это `libs.compose.desktop.windows` (дефисы → точки).
- `compose.desktop` в `build.gradle.kts` — расширение плагина `org.jetbrains.compose`, а не catalog.
- `.gitignore` уже игнорирует `*.jar`. Строка `!gradle/wrapper/gradle-wrapper.jar` должна стоять **после** неё.
- Предупреждение «Unsupported JavaFX» не появится: JavaFX не используется.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-24 | Review | Все шаги выполнены по задаче без отклонений. Хеш Gradle совпал; wrapper 9.8.1; `gradlew build` — BUILD SUCCESSFUL, 2 теста пройдены; `gradlew run` — строка запуска в консоли и в %LOCALAPPDATA%\fast-cow-screenshot\logs\app.log. Файлы .idea/*.iml были в индексе до начала работы и не включались в коммит. |

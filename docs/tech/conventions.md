# Технические соглашения проекта fast-cow-screenshot

| Поле | Значение |
|---|---|
| Автор | Tech |
| Дата последнего изменения | 2026-10-31 |
| Связанные ADR | ADR-001, ADR-002, ADR-003 |

> Единый источник правды для Dev. Каждая задача (TASK) предполагает, что исполнитель прочитал этот документ.
> Всё, что здесь записано, обязательно, если в задаче явно не сказано иное.

## 1. Стек и версии

| Компонент | Решение | Версия | Координаты / источник | Обоснование |
|---|---|---|---|---|
| Язык | Kotlin/JVM | 2.4.21 | плагин `org.jetbrains.kotlin.jvm` | ADR-001 |
| JDK (сборка и запуск) | Eclipse Temurin | 25 LTS | https://adoptium.net, `jvmToolchain(25)` | ADR-001 |
| Система сборки | Gradle (Kotlin DSL) + wrapper | 9.8.1 | `gradle/wrapper/gradle-wrapper.properties` | ADR-001 |
| UI | Compose Multiplatform Desktop | 1.12.2 | плагин `org.jetbrains.compose`; `org.jetbrains.compose.desktop:desktop-jvm-windows-x64`; `org.jetbrains.compose.material:material` | ADR-001 |
| Compose compiler | Kotlin Compose plugin | 2.4.21 | плагин `org.jetbrains.kotlin.plugin.compose` | ADR-001 |
| Нативный доступ к WinAPI | JNA | 5.19.1 | `net.java.dev.jna:jna`, `net.java.dev.jna:jna-platform` | ADR-002 |
| Асинхронность | `java.util.concurrent` (именованные однопоточные `ExecutorService`) | JDK | — | TDD-001 §7 |
| Сериализация настроек | kotlinx.serialization JSON | 1.11.0 | `org.jetbrains.kotlinx:kotlinx-serialization-json`, плагин `org.jetbrains.kotlin.plugin.serialization` 2.4.21 | ADR-003 |
| Логирование | SLF4J + Logback | 2.0.17 / 1.5.38 | `org.slf4j:slf4j-api`, `ch.qos.logback:logback-classic` | ADR-003 |
| Тесты | JUnit Jupiter + MockK + kotlin-test | 6.1.3 / 1.14.11 / 2.4.21 | `org.junit:junit-bom`, `org.junit.jupiter:junit-jupiter`, `org.junit.platform:junit-platform-launcher`, `io.mockk:mockk`, `org.jetbrains.kotlin:kotlin-test` | ADR-003 |
| Упаковка | Compose `nativeDistributions` → `createDistributable` (jpackage app-image); runtime — явный список `modules(...)`, без `includeAllModules` и без `jdk.jlink`/`jdk.jpackage` (Temurin 25 без `jmods`, JEP 493; CLR-004) | — | — | ADR-001 |

Добавлять зависимости, не перечисленные в этой таблице или в задаче, **запрещено**. `kotlinx-coroutines` приходит
транзитивно через Compose — в своём коде его **не использовать**.

## 2. Структура репозитория

Один Gradle-модуль (корневой проект).

```
fast-cow-screenshot/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew, gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/ (gradle-wrapper.jar, gradle-wrapper.properties)
├── src/
│   ├── main/kotlin/by/sparkyanvil/fastcowscreenshot/
│   │   ├── Main.kt                 # точка входа main()
│   │   ├── app/                    # сборка зависимостей, жизненный цикл, потоки
│   │   ├── domain/                 # чистая логика: геометрия, имена файлов, модели
│   │   ├── settings/               # модель настроек, JSON-хранилище
│   │   ├── capture/                # интерфейсы захвата и координатор сценариев
│   │   ├── storage/                # запись PNG, автосохранение
│   │   ├── platform/windows/       # реализации на WinAPI
│   │   │   └── jna/                # JNA-интерфейсы, структуры, константы
│   │   └── ui/                     # трей, уведомление, оверлей, окно настроек, «Сохранить как»
│   ├── main/resources/logback.xml
│   └── test/kotlin/by/sparkyanvil/fastcowscreenshot/   # зеркально main
└── docs/
```

Базовый пакет: **`by.sparkyanvil.fastcowscreenshot`**. Путь: `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/`.

## 3. Пакеты и слои

| Пакет | Ответственность | Может зависеть от | Не может зависеть от |
|---|---|---|---|
| `…fastcowscreenshot.domain` | Геометрия, имена файлов, значения | только JDK (`java.time`, `java.nio.file`) | `java.awt.*` (кроме `java.awt.image.BufferedImage`), `javax.swing`, `com.sun.jna`, `androidx.compose` |
| `…fastcowscreenshot.settings` | `AppSettings`, `HotkeyBinding`, хранилище JSON | `domain`, kotlinx.serialization | `platform`, `ui`, `com.sun.jna` |
| `…fastcowscreenshot.capture` | Интерфейсы (`ScreenCapturer`, `ClipboardWriter`…), `CaptureCoordinator` | `domain`, `settings`, `storage` (интерфейсы) | `platform`, `ui`, `com.sun.jna`, `javax.swing` |
| `…fastcowscreenshot.storage` | Запись PNG, автосохранение | `domain`, `javax.imageio` | `platform`, `ui`, `com.sun.jna` |
| `…fastcowscreenshot.platform.windows` (+ `.jna`) | Реализации интерфейсов на WinAPI | `domain`, `capture`, `settings`, JNA | `ui` |
| `…fastcowscreenshot.ui` | Окна, трей, диалоги | все пакеты выше, Compose, Swing/AWT | — |
| `…fastcowscreenshot.app` | Сборка графа объектов, потоки, выход | все | — |

Правила:
- Любой вызов `com.sun.jna.*` — только в `platform.windows` и `platform.windows.jna`. Исключение — `ui` получает HWND окна
  через `WindowHandles` из `platform.windows` (TASK-005).
- Классы `capture` и `storage` тестируются без Windows — все нативные зависимости у них только через интерфейсы.

## 4. Сборка и команды (Windows PowerShell)

| Действие | Команда | Ожидаемый результат |
|---|---|---|
| Сборка + тесты | `.\gradlew.bat build` | `BUILD SUCCESSFUL` |
| Только тесты | `.\gradlew.bat test` | `BUILD SUCCESSFUL`, 0 failed |
| Один тест | `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.domain.PixelRectTest"` | Тесты класса проходят |
| Компиляция | `.\gradlew.bat compileKotlin compileTestKotlin` | `BUILD SUCCESSFUL` |
| Запуск приложения | `.\gradlew.bat run` | Приложение запущено, значок в трее; закрыть через «Выход» в трее. Запускать с таймаутом, если запускает агент |
| Дистрибутив | `.\gradlew.bat createDistributable` | `build\compose\binaries\main\app\fast-cow-screenshot\fast-cow-screenshot.exe` |

Статический анализ в v1 не подключается.

## 5. Стиль кода

- Форматирование: IntelliJ Kotlin official style (`kotlin.code.style=official` в `gradle.properties`), отступ 4 пробела,
  максимальная длина строки 120.
- Именование: классы `PascalCase`, функции и свойства `camelCase`, константы `UPPER_SNAKE_CASE` (`const val` в
  `companion object` или top-level в файле, указанном задачей). Имена функций JNA-интерфейсов совпадают с именами
  WinAPI (`GetDC`, `BitBlt`), поля JNA-структур — с полями C (`cbSize`).
- Один публичный/internal класс или интерфейс — один файл, имя файла = имя класса. Sealed-иерархия с вложенными
  классами — в одном файле.
- Видимость: по умолчанию `internal`. `public` — только `main()`.
- Null-safety: `!!` запрещён. Платформенные типы из Java/JNA сразу приводить к явному Kotlin-типу (`val dc: HDC? = …`).
- Неизменяемость: `val` и неизменяемые коллекции по умолчанию; `data class` для значений.
- Ошибки:
  - ожидаемые ошибки (буфер занят, нет прав на запись) — `sealed interface …Result`;
  - сбой нативного вызова внутри `platform.windows` — `WinApiException(message)` (TASK-005), который ловит вызывающий
    код и превращает в `Result` или уведомление;
  - пустые `catch` и `catch (e: Throwable)` запрещены. Ловить `Exception` разрешено только там, где это указано
    в задаче (границы потоков).
- Комментарии и KDoc — на русском языке. KDoc обязателен для всех `internal`-классов и интерфейсов из раздела
  «Контракты» задачи.
- Ресурсы WinAPI (`HDC`, `HBITMAP`, `HICON`, `HGLOBAL`, PIDL) освобождаются в `try/finally` в той же функции,
  где получены (кроме случаев, когда задача явно передаёт владение).

## 6. Многопоточность

| Вид работы | Где выполняется | Как переключаться |
|---|---|---|
| UI (трей, уведомление, оверлей, окно настроек) | AWT EDT | `SwingUtilities.invokeLater { }`; в коде — через `AppThreads.ui` (Executor) |
| Перечисление мониторов, захват, указатель, буфер обмена | поток `fcs-capture` (однопоточный, контекст DPI Per-Monitor V2) | `AppThreads.capture.execute { }` |
| Кодирование PNG и запись файлов | поток `fcs-save` (однопоточный) | `AppThreads.save.execute { }` |
| Нативные диалоги (сохранение, выбор папки, MessageBox), «Показать в папке» | поток `fcs-dialog` (однопоточный, COM STA) | `AppThreads.dialog.execute { }` |
| Горячие клавиши, окно-владелец буфера | поток `fcs-hotkeys` (цикл `GetMessageW`) | `NativeMessageLoop.invoke { }` |

`AppThreads` создаётся в TASK-010. Классы, которые не создают потоки сами, получают `Executor` через конструктор:
в тестах подставляется `Executor { it.run() }`.

Запреты:
- блокирующий ввод-вывод, захват экрана, нативные диалоги на EDT;
- `Thread.sleep` на EDT;
- `kotlinx.coroutines`, `GlobalScope`, `runBlocking`;
- создание потоков вне `AppThreads` и `NativeMessageLoop`;
- вызов Swing/AWT-методов окон вне EDT.

## 7. Нативный код (WinAPI)

- Библиотека: JNA 5.19.1. Свои интерфейсы загружаются так:
  `Native.load("user32", User32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)`. `DEFAULT_OPTIONS` сам выбирает
  `…W`-версии функций, поэтому в интерфейсе функции объявляются **без** суффикса `W` (`GetMonitorInfo`, `MessageBox`).
- Где лежат маппинги: `by.sparkyanvil.fastcowscreenshot.platform.windows.jna` (TASK-005). Новые функции добавляются только
  туда и только задачей.
- Типы из `jna-platform` (`WinDef.HWND`, `WinDef.HDC`, `WinDef.RECT`, `WinDef.POINT`, `WinUser.MSG`,
  `WinUser.MONITORINFOEX`, `WinGDI.BITMAPINFO`, `WinGDI.ICONINFO`) использовать разрешено.
- Проверять каждый код возврата. При ошибке брать `Native.getLastError()` и включать его в сообщение исключения
  или лога: `"BitBlt failed, GetLastError=5"`.
- DPI: процессом управляет AWT (Per-Monitor). Поток `fcs-capture` при старте вызывает
  `DpiAwareness.enterPerMonitorV2()` (TASK-005). Все координаты в `domain`, `capture` и `platform` — **физические пиксели
  виртуального рабочего стола**.
- Флаги JVM (заданы в `build.gradle.kts`, TASK-001): `--enable-native-access=ALL-UNNAMED`,
  `--sun-misc-unsafe-memory-access=allow`, `-Xmx768m`; для возврата памяти ОС в простое (REQ-001.N6, TASK-019, CLR-005) —
  `-XX:G1PeriodicGCInterval=15000`, `-XX:MinHeapFreeRatio=10`, `-XX:MaxHeapFreeRatio=20`.

## 8. Логирование

- Логгер: SLF4J. Объявление в классе:
  `private val log = LoggerFactory.getLogger(ИмяКласса::class.java)` (в `companion object` или top-level `private val`).
- Куда пишется: консоль + `%LOCALAPPDATA%\fast-cow-screenshot\logs\app.log`, ротация по дням, 14 файлов
  (`src/main/resources/logback.xml`, TASK-001).
- Уровни: `ERROR` — сбой, из-за которого действие пользователя не выполнено; `WARN` — деградация с обходом (повтор
  буфера, fallback папки); `INFO` — запуск/остановка, регистрация клавиш, завершённый захват (режим, размер, мс),
  сохранённый файл; `DEBUG` — детали (координаты мониторов, проигнорированные нажатия).
- Формат сообщений — английский язык, параметры через `{}`: `log.info("Captured {} {}x{} in {} ms", mode, w, h, ms)`.
- Запрещено логировать содержимое снимков и содержимое буфера обмена.

## 9. Тестирование

- Фреймворк: JUnit Jupiter 6 (`org.junit.jupiter.api.Test`), утверждения `kotlin.test.*` (`assertEquals`,
  `assertTrue`, `assertNull`, `assertIs`, `assertFailsWith`), моки — MockK.
- Расположение: зеркально `src/main`, имя класса `<ClassName>Test`.
- Имена тестов — в обратных кавычках, на английском: `` fun `returns primary monitor when cursor is outside`() ``.
- Временные файлы — `@TempDir lateinit var tempDir: Path` (`org.junit.jupiter.api.io.TempDir`). Это **единственное**
  разрешённое использование `lateinit`.
- Unit-тестами покрываются: `domain`, `settings`, `storage`, `capture` (с фейками), чистые функции `platform`
  (`DibEncoder`, `HotkeyBinding`).
- MockK на JDK 25: агент ByteBuddy подключается к тестовой JVM через `-javaagent:<byte-buddy-agent.jar>` и
  `-Xshare:off` в `doFirst` блока `tasks.test` `build.gradle.kts` (TASK-004, CLR-001, CLR-002). Self-attach
  (`-Djdk.attach.allowAttachSelf=true`) не использовать: он ломается на путях с кириллицей. В `appJvmArgs` эти
  флаги не добавлять. Ошибки `Could not self-attach to current VM` или `Error opening zip file` в тестах с MockK
  означают, что блок `tasks.test` не соответствует TASK-004 шаг 0.
- Compose-события клавиатуры: `KeyEvent.nativeKeyEvent` **не** приводить к `java.awt.event.KeyEvent` (в Compose 1.12.2
  это `InternalKeyEvent`). Код клавиши — `e.key.nativeKeyCode` (AWT `VK_*`), модификаторы — `e.isCtrlPressed`,
  `e.isAltPressed`, `e.isShiftPressed` (CLR-003). Решение по нажатию выносить в чистую функцию с unit-тестами.
- Тесты с реальным WinAPI помечаются `@EnabledOnOs(OS.WINDOWS)` и не требуют действий пользователя.
- Реальный захват на нескольких мониторах, HiDPI, горячие клавиши, трей и вставку из буфера проверяет пользователь
  по шагам раздела «Проверка» задачи.

## 10. Общий Definition of Done

- [ ] `.\gradlew.bat build` завершается `BUILD SUCCESSFUL`.
- [ ] Все тесты из задачи написаны и проходят.
- [ ] Изменены только файлы из раздела «Область изменений» задачи.
- [ ] Нет новых зависимостей, кроме указанных в задаче.
- [ ] Нет `!!`, `TODO` и закомментированного кода (кроме разрешённых задачей).
- [ ] Ручная проверка из задачи выполнена, результат записан в «Журнал выполнения» задачи.
- [ ] Статус задачи изменён на `Review`.

## 11. Запреты

- Не использовать `java.awt.Robot` для захвата (ADR-002).
- Не использовать AWT `Toolkit.getSystemClipboard()` для изображений (ADR-002).
- Не использовать `JFileChooser` и AWT `FileDialog` (ADR-001, ADR-002).
- Не использовать Material3, `kotlinx.coroutines`, `application { }` из Compose.
- Не использовать ANSI-версии WinAPI (`…A`).
- Не изменять `.idea/`, `fast-cow-screenshot.iml`, `docs/`, `.kilo/` (кроме файла своей задачи, CLR и `docs/tech/README.md`).

## 12. Журнал изменений

| Дата | Изменение | ADR / TASK |
|---|---|---|
| 2026-10-18 | Первая версия | ADR-001, ADR-002, ADR-003 |
| 2026-10-29 | §9: агент MockK через `-javaagent` в тестовой JVM | CLR-001, CLR-002, TASK-004 |
| 2026-10-31 | §9: правило для Compose-событий клавиатуры | CLR-003, TASK-017 |
| 2026-10-31 | §1: runtime дистрибутива — явный список модулей (Temurin 25 без jmods) | CLR-004, TASK-018 |
| 2026-10-31 | §7: флаги G1 для возврата памяти ОС | CLR-005, TASK-019 |

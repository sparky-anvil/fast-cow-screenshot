# Техническая документация проекта fast-cow-screenshot

Рабочее пространство агентов **Tech** (`.kilo/agent/tech.md`) и **Dev** (`.kilo/agent/dev.md`).

| Этап | Агент | Запуск | Шаблоны |
|---|---|---|---|
| Требования | Analyst | `/analyze <запрос>`, `@analyst` | `.kilo/skills/analysis-templates/templates/` |
| Технические решения, дизайн, задачи | Tech | `/tech <REQ/US/CLR или запрос>`, `@tech` | `.kilo/skills/tech-templates/templates/` |
| Реализация задачи | Dev | `/dev [TASK-NNN]`, `@dev` | `.kilo/skills/dev-workflow/templates/` |

Входные данные — документы Analyst в [docs/analysis/](../analysis/README.md).
Результат — задачи для агента **Dev**: исполнитель читает задачу и [conventions.md](conventions.md).
Если задачи не хватает для реализации, Dev оформляет запрос уточнения (CLR) к Tech, Analyst или пользователю.

## Структура

| Путь | Содержимое | Префикс |
|---|---|---|
| [conventions.md](conventions.md) | Технические соглашения: стек, версии, структура, стиль, сборка, тесты | — |
| [designs/](designs/) | Технические дизайны фич | `TDD-NNN` |
| [tasks/](tasks/) | Задачи для Dev | `TASK-NNN` |
| [clarifications/](clarifications/) | Запросы уточнения от Dev | `CLR-NNN` |
| [../analysis/decisions/](../analysis/decisions/) | Архитектурные и технологические решения (общий реестр с Analyst) | `ADR-NNN` |

## Технические дизайны

| ID | Название | Входные артефакты | Статус |
|---|---|---|---|
| [TDD-001](designs/TDD-001-screen-capture-v1.md) | Захват экрана (3 режима), буфер, автосохранение, «Сохранить как», настройки — v1 Windows | REQ-001, US-001 – US-007, RES-001 | Review |
| [TDD-002](designs/TDD-002-release-process.md) | Процесс релиза и выпуск версии 0.0.1 | ADR-004, запрос пользователя | Review |

## Задачи

Статусы: `Draft` → `Ready` → `In Progress` → `Review` → `Done`; `Blocked` — есть открытый блокирующий CLR.

| ID | Название | TDD | Зависит от | Оценка | Статус |
|---|---|---|---|---|---|
| [TASK-001](tasks/TASK-001-gradle-project-skeleton.md) | Каркас Gradle-проекта | TDD-001 | — | M | Done |
| [TASK-002](tasks/TASK-002-domain-geometry.md) | Доменная геометрия и модели | TDD-001 | 001 | M | Done |
| [TASK-003](tasks/TASK-003-file-names.md) | Имена файлов, `.png`, сокращение пути | TDD-001 | 001 | S | Done |
| [TASK-004](tasks/TASK-004-settings-model-and-storage.md) | Модель настроек и JSON-хранилище | TDD-001 | 002 | M | Done |
| [TASK-005](tasks/TASK-005-jna-winapi-mappings.md) | JNA-маппинги WinAPI, DPI, HWND | TDD-001 | 001 | L | Done |
| [TASK-006](tasks/TASK-006-monitors-gdi-capture-cursor.md) | Мониторы, GDI-захват, указатель | TDD-001 | 002, 005 | L | Done |
| [TASK-007](tasks/TASK-007-message-loop-hotkeys.md) | Цикл сообщений и горячие клавиши | TDD-001 | 004, 005 | M | Done |
| [TASK-008](tasks/TASK-008-clipboard-writer.md) | Буфер обмена CF_DIB | TDD-001 | 005, 006, 007 | M | Done |
| [TASK-009](tasks/TASK-009-png-autosave-known-folders.md) | Автосохранение PNG и Known Folders | TDD-001 | 003, 004, 005 | M | Done |
| [TASK-010](tasks/TASK-010-capture-coordinator.md) | Координатор захвата и потоки | TDD-001 | 006, 008, 009 | L | Done |
| [TASK-011](tasks/TASK-011-notification-window.md) | Окно уведомлений | TDD-001 | 003, 005, 010 | M | Done |
| [TASK-012](tasks/TASK-012-save-as.md) | «Сохранить как…» | TDD-001 | 003, 004, 005, 009, 010 | M | Done |
| [TASK-013](tasks/TASK-013-region-overlay.md) | Оверлей выделения области | TDD-001 | 002, 005, 010 | L | Done |
| [TASK-014](tasks/TASK-014-settings-window.md) | Окно настроек | TDD-001 | 004, 005, 007, 009, 010, 011 | L | Done |
| [TASK-015](tasks/TASK-015-tray-app-wiring.md) | Трей, сборка приложения, приёмка | TDD-001 | 011, 012, 013, 014, 017 | M | Done |
| [TASK-016](tasks/TASK-016-distribution-and-nfr.md) | Дистрибутив app-image и замеры NFR | TDD-001 | 015, 018, 019 | S | Done |
| [TASK-017](tasks/TASK-017-fix-hotkey-recording.md) | Исправление записи сочетания клавиш (CLR-003) | TDD-001 | 014 | S | Done |
| [TASK-018](tasks/TASK-018-runtime-modules-list.md) | Явный список модулей runtime (CLR-004) | TDD-001 | 015 | S | Done |
| [TASK-019](tasks/TASK-019-jvm-heap-uncommit.md) | Флаги G1 для возврата памяти ОС, замер RSS (CLR-005) | TDD-001 | 018 | S | Done |
| [TASK-020](tasks/TASK-020-version-and-release-plugin.md) | Версия из gradle.properties, плагин релиза, `packageReleaseZip` | TDD-002 | 016 | M | Ready |
| [TASK-021](tasks/TASK-021-github-release-workflow.md) | Workflow GitHub Actions для релиза | TDD-002 | 020 | S | Ready |
| [TASK-022](tasks/TASK-022-release-0-0-1.md) | Выпуск версии 0.0.1 | TDD-002 | 020, 021 | S | Ready |

## Порядок выполнения

Задачи выполняются сверху вниз; задача берётся в работу, только когда все её зависимости в статусе `Done`.

TASK-001 → 002 → 003 → 004 → 005 → 006 → 007 → 008 → 009 → 010 → 011 → 012 → 013 → 014 → 017 → 015 → 018 → 019 → 016.
TASK-002/003/005 и TASK-011/012/013 между собой независимы.

Релиз (TDD-002): TASK-020 → 021 → 022. Перед TASK-022 изменения TASK-020/021 должны быть закоммичены и отправлены
в `main` (предусловия TASK-022).

## Запросы уточнения

Статусы: `Open` → `Answered` → `Closed`; `Withdrawn` — вопрос снят.

| ID | Вопрос | Задача | Адресат | Блокирует | Статус |
|---|---|---|---|---|---|
| [CLR-001](clarifications/CLR-001-mockk-self-attach-jdk25.md) | MockK self-attach на JDK 25: нужен флаг в tasks.test | TASK-004 | Tech | Да | Closed |
| [CLR-002](clarifications/CLR-002-mockk-agent-cyrillic-path.md) | Self-attach MockK падает из-за кириллицы в пути к Gradle-кэшу | TASK-004 | Tech | Да | Closed |
| [CLR-003](clarifications/CLR-003-compose-native-key-event-cast.md) | ClassCastException nativeKeyEvent в окне настроек (шаг 26 приёмки) | TASK-015 (дефект TASK-014) | Tech | Да | Closed |
| [CLR-004](clarifications/CLR-004-jlink-no-jmods-temurin25.md) | createDistributable: Temurin 25 без jmods, jlink падает | TASK-016 | Tech | Да | Closed |
| [CLR-005](clarifications/CLR-005-rss-over-200mb-after-captures.md) | RSS 438–646 МБ после снимков (N6 ≤ 200); неэталонная конфигурация замеров | TASK-016 | Tech | Да | Closed |
| [CLR-006](clarifications/CLR-006-n6-rss-definition.md) | N6: «в простое» — до или после снимков (RSS 266–271 МБ при heap 38 МБ) | TASK-019 | Tech | Да | Closed |





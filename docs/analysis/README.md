# Аналитика проекта fast-cow-screenshot

Рабочее пространство агента **Analyst** (`.kilo/agent/analyst.md`).
Шаблоны: `.kilo/skills/analysis-templates/templates/`. Запуск анализа: `/analyze <запрос>` или `@analyst`.

Следующий этап — агент **Tech** (`/tech` или `@tech`): по этим документам принимает технические решения и готовит
задачи для агента **Dev** в [docs/tech/](../tech/README.md). Технологические ADR агент Tech пишет в общий реестр `decisions/`.
Запросы уточнения от Dev с адресатом `Analyst` лежат в [docs/tech/clarifications/](../tech/clarifications/).

## Структура

| Папка | Содержимое | Префикс |
|---|---|---|
| [requirements/](requirements/) | Требования и функциональные спецификации | `REQ-NNN` |
| [user-stories/](user-stories/) | User stories с критериями приёмки | `US-NNN` |
| [decisions/](decisions/) | Архитектурные решения (ADR) | `ADR-NNN` |
| [research/](research/) | Исследования, сравнения, анализ рисков | `RES-NNN` |

## Требования

| ID | Название | Приоритет | Статус |
|---|---|---|---|
| [REQ-001](requirements/REQ-001-screen-capture-clipboard-save.md) | Захват экрана с учётом мультимонитора, автокопирование в буфер и сохранение файлов | Must | Review |

## User stories

| ID | Название | Требования | Статус |
|---|---|---|---|
| [US-001](user-stories/US-001-capture-monitor-under-cursor.md) | Захват монитора под курсором | REQ-001 | Review |
| [US-002](user-stories/US-002-capture-all-monitors.md) | Захват всех мониторов одним изображением | REQ-001 | Review |
| [US-003](user-stories/US-003-capture-region.md) | Захват произвольной области, в том числе через границу мониторов | REQ-001 | Review |
| [US-004](user-stories/US-004-auto-copy-to-clipboard.md) | Автоматическое копирование снимка в буфер обмена | REQ-001 | Review |
| [US-005](user-stories/US-005-auto-save-to-default-directory.md) | Автосохранение снимка в директорию по умолчанию | REQ-001 | Review |
| [US-006](user-stories/US-006-save-as.md) | «Сохранить как…» в выбранную директорию | REQ-001 | Review |
| [US-007](user-stories/US-007-default-directory-setting.md) | Настройка директории по умолчанию | REQ-001 | Review |

## Архитектурные решения

| ID | Решение | Статус |
|---|---|---|
| [ADR-001](decisions/ADR-001-platform-stack-jdk25-compose.md) | Kotlin 2.4 / JDK 25 / Gradle 9 / Compose Multiplatform Desktop (вместо TornadoFX) | Accepted |
| [ADR-002](decisions/ADR-002-winapi-via-jna.md) | WinAPI через JNA: GDI-захват, RegisterHotKey, CF_DIB, нативные диалоги | Accepted |
| [ADR-003](decisions/ADR-003-settings-logging-tests.md) | Настройки JSON в %APPDATA%, SLF4J + Logback, JUnit 6 + MockK | Accepted |
| [ADR-004](decisions/ADR-004-release-process.md) | Релиз: net.researchgate.release, ZIP app-image, GitHub Actions по тегу | Accepted |

## Исследования

| ID | Тема | Статус |
|---|---|---|
| [RES-001](research/RES-001-windows-capture-tech-risks.md) | Технические варианты и риски захвата экрана на Windows (мультимонитор, HiDPI, горячие клавиши, буфер обмена) | Draft |

# TASK-021: Создать workflow GitHub Actions для публикации релиза по тегу

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | S (≤ 1 ч) |
| Технический дизайн | TDD-002 |
| Требования | ADR-004 |
| ADR | ADR-004 |
| Зависит от | TASK-020 |
| Блокирует | TASK-022 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Добавить workflow `.github/workflows/release.yml`: при push тега `v*` на Windows-runner проверить, что тег совпадает с
версией в `gradle.properties`, собрать ZIP задачей `packageReleaseZip` (TASK-020) и создать GitHub Release с этим ZIP.

## 2. Контекст

- Тег создаёт плагин `net.researchgate.release` в TASK-022 (формат `v0.0.1`). На коммите тега `gradle.properties`
  содержит `version=0.0.1` (без `-SNAPSHOT`).
- `.\gradlew.bat packageReleaseZip` создаёт `build\release\fast-cow-screenshot-<version>-windows-x64.zip`.
- JDK — Temurin 25 (`kotlin { jvmToolchain(25) }`). У Temurin 25 нет `jmods`, это уже учтено в `build.gradle.kts` (CLR-004).
- Тесты в CI **не запускаются**: WinAPI-тесты требуют интерактивного рабочего стола; их выполняет локальный `release`.
- Версии actions проверены 2026-10-31: `actions/checkout` v7.0.1, `actions/setup-java` v6.1.0,
  `gradle/actions` v6.4.1, `softprops/action-gh-release` v3.0.3. Используются мажорные теги `@v7`, `@v6`, `@v6`, `@v3`.

**Выдержка из ADR-004 (дословно):**

> GitHub Actions (`windows-latest`) по push тега `v*` собирает ZIP и создаёт GitHub Release
> (`softprops/action-gh-release@v3`).

## 3. Предусловия

- TASK-020 `Done`: в `build.gradle.kts` есть задача `packageReleaseZip`, в `gradle.properties` — `version=`.
- Каталога `.github/` в репозитории нет.
- Проверка: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `.github/workflows/release.yml` | Создать | Содержимое раздела 8 дословно |

**Файлы, которые изменять нельзя:** все остальные (кроме статуса/журнала этой задачи и статуса в `docs/tech/README.md`).

## 5. Зависимости

Новых Gradle-зависимостей нет. GitHub Actions: `actions/checkout@v7`, `actions/setup-java@v6`,
`gradle/actions/setup-gradle@v6`, `softprops/action-gh-release@v3`.

## 6. Контракты

Н/П — Kotlin-кода нет. Контракт workflow: триггер `push.tags: ['v*']`; результат — GitHub Release с именем и тегом
`${{ github.ref_name }}` и одним файлом `fast-cow-screenshot-<version>-windows-x64.zip`.

## 7. Пошаговая реализация

### Шаг 1. Создать файл
- **Файл:** `.github/workflows/release.yml`
- **Сделать:** создать каталоги `.github\workflows\` и файл с содержимым раздела 8 **дословно** (отступы — 2 пробела,
  кодировка UTF-8 без BOM, переводы строк LF или CRLF — без разницы).
- **Результат:** файл существует.

### Шаг 2. Проверить синтаксис YAML
- **Сделать:** выполнить ручную проверку раздела 12 (пункты 1–2). Отдельный YAML-линтер не устанавливать.
- **Результат:** файл совпадает с разделом 8, табуляций нет.

## 8. Алгоритм и логика

Эталонное содержимое файла (дословно):

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

permissions:
  contents: write

jobs:
  release:
    runs-on: windows-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v7

      - name: Set up JDK 25
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Verify tag matches gradle.properties version
        shell: pwsh
        run: |
          $expected = '${{ github.ref_name }}'.TrimStart('v')
          $line = Get-Content gradle.properties | Where-Object { $_ -match '^version=' } | Select-Object -First 1
          $actual = ($line -replace '^version=', '').Trim()
          if ($actual -ne $expected) {
            Write-Error "Tag version $expected != gradle.properties version $actual"
            exit 1
          }

      - name: Build release ZIP
        shell: pwsh
        run: .\gradlew.bat packageReleaseZip --no-daemon

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v3
        with:
          name: ${{ github.ref_name }}
          tag_name: ${{ github.ref_name }}
          files: build/release/fast-cow-screenshot-*-windows-x64.zip
          fail_on_unmatched_files: true
          generate_release_notes: true
          draft: false
          prerelease: false
```

Пример: тег `v0.0.1` → `$expected = "0.0.1"`; строка `version=0.0.1` → `$actual = "0.0.1"` → проверка пройдена →
`build/release/fast-cow-screenshot-0.0.1-windows-x64.zip` → Release `v0.0.1`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Что видит пользователь |
|---|---|---|---|
| 1 | Тег `v0.0.1`, а в `gradle.properties` `0.0.2-SNAPSHOT` | job падает на шаге проверки | `Tag version 0.0.1 != gradle.properties version 0.0.2-SNAPSHOT` |
| 2 | ZIP не создан | `fail_on_unmatched_files` → job падает | Красный шаг `Create GitHub Release` |
| 3 | Нет прав на запись | job падает с 403 | Включить в Settings → Actions → Workflow permissions «Read and write» (действие пользователя) |

## 10. Константы и тексты

| Имя | Значение |
|---|---|
| Имя workflow | `Release` |
| Триггер | `v*` |
| Runner | `windows-latest` |
| Текст ошибки | `Tag version $expected != gradle.properties version $actual` |

## 11. Тесты

Н/П — автотестов workflow нет; проверяется в TASK-022 реальным запуском.

## 12. Проверка

**Автоматическая:**

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` (workflow сборку не затрагивает) |
| `git status --short` | `?? .github/` (плюс файл задачи и `docs/tech/README.md`) |

**Ручная (сверка с разделом 8):**
1. Файл совпадает с разделом 8 символ в символ (сравнить построчно).
2. Нет символов табуляции: `Select-String -Path .github\workflows\release.yml -Pattern "`t"` → пусто.
3. Workflow локально **не запускать** и тег **не создавать** — запуск в TASK-022.

## 13. Критерии готовности (Definition of Done)

- [ ] Файл `.github/workflows/release.yml` создан с содержимым раздела 8.
- [ ] Табуляций нет.
- [ ] Других файлов не изменено.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не добавлять шаги `test`/`build` в workflow, не добавлять триггеры `push` на ветки и `pull_request`.
- Не менять версии actions и не закреплять их по SHA.
- Не добавлять MSI (`packageMsi`) и подпись.
- Не создавать теги и не пушить.

## 15. Ловушки и подсказки

- `'${{ github.ref_name }}'` — в одинарных кавычках PowerShell: GitHub подставляет значение до запуска pwsh.
- `.\gradlew.bat` в шаге `shell: pwsh` — на Windows-runner `./gradlew` (bash-скрипт) не используется.
- Отступы YAML строго пробелами; блок `run: |` — многострочный, его строки с отступом на 2 пробела больше `run:`.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

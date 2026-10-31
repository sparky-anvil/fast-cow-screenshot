# TASK-022: Выпустить версию 0.0.1

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | S (≤ 1 ч) |
| Технический дизайн | TDD-002 |
| Требования | ADR-004; запрос пользователя «выпустить версию 0.0.1» |
| ADR | ADR-004 |
| Зависит от | TASK-020, TASK-021 |
| Блокирует | — |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Выполнить задачу `release` плагина `net.researchgate.release`: на `main` создать коммиты и тег `v0.0.1`, перевести
версию на `0.0.2-SNAPSHOT`, отправить в `origin`; убедиться, что workflow `Release` опубликовал GitHub Release `v0.0.1`
с файлом `fast-cow-screenshot-0.0.1-windows-x64.zip`, и что приложение из ZIP запускается с версией `0.0.1`.

## 2. Контекст

- Задача **меняет git-историю и пушит** в `https://github.com/sparky-anvil/fast-cow-screenshot.git`. Это разрешено
  только в этой задаче и только после явного подтверждения пользователя (шаг 2).
- Что делает `release` (по порядку): проверяет, что нет незакоммиченных и неотслеживаемых файлов, что ветка — `main`
  и она совпадает с `origin/main`; заменяет `version=0.0.1-SNAPSHOT` на `version=0.0.1` в `gradle.properties`;
  выполняет `build` (все тесты); коммитит (сообщение начинается с `[Gradle Release Plugin] - pre tag commit:`);
  создаёт тег `v0.0.1`; записывает `version=0.0.2-SNAPSHOT`; коммитит (`[Gradle Release Plugin] - new version commit:`);
  пушит коммиты и тег в `origin`. При ошибке до тега плагин откатывает `gradle.properties`.
- Push тега запускает workflow `.github/workflows/release.yml` (TASK-021) на GitHub.
- Свойства `-Prelease.useAutomaticVersion=true`, `-Prelease.releaseVersion=0.0.1`, `-Prelease.newVersion=0.0.2-SNAPSHOT`
  отключают интерактивные вопросы плагина.

**Выдержка из ADR-004 (дословно):**

> `net.researchgate.release` **3.2.0**, тег `v<version>` (`v0.0.1`), релизная ветка `main`;
> артефакт — `fast-cow-screenshot-<version>-windows-x64.zip` с app-image; MSI не публикуется.

## 3. Предусловия

Выполняет **пользователь** до начала задачи (Dev проверяет на шаге 1 и, если не выполнено, ставит `Blocked`
с перечнем невыполненного — CLR не нужен):

1. TASK-020 и TASK-021 в статусе `Done`, их изменения закоммичены.
2. Ветка `feature/TDD-001-screen-capture-v1` слита в `main`, `main` отправлена в `origin`.
3. На GitHub: Settings → Actions → General → Workflow permissions = «Read and write permissions».
4. В `docs/tech/README.md` статус TASK-022 уже `In Progress` и закоммичен (иначе дерево будет «грязным», см. раздел 15).

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `gradle.properties` | Изменяет плагин | `0.0.1-SNAPSHOT` → `0.0.1` → `0.0.2-SNAPSHOT` (руками **не** редактировать) |
| 2 | этот файл, `docs/tech/README.md` | Изменить **после** релиза | статус и журнал (шаг 7) |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

Н/П — кода нет.

## 7. Пошаговая реализация

### Шаг 1. Проверить состояние репозитория
Выполнить по очереди:

| Команда | Ожидается |
|---|---|
| `git checkout main` | `Switched to branch 'main'` или `Already on 'main'` |
| `git pull --ff-only` | без конфликтов |
| `git status --short` | пустой вывод |
| `git log --oneline origin/main..main` | пустой вывод |
| `Select-String -Path gradle.properties -Pattern '^version='` | `version=0.0.1-SNAPSHOT` |
| `git tag --list "v0.0.1"` | пустой вывод |
| `Test-Path .github\workflows\release.yml` | `True` |

Любое несовпадение → статус `Blocked`, в журнал — что не совпало. Ничего не исправлять самому.

### Шаг 2. Получить подтверждение пользователя
Через инструмент `question` спросить: «Запустить `.\gradlew.bat release` для версии 0.0.1? Будут созданы 2 коммита
и тег v0.0.1 на main и отправлены в origin.» Варианты: «Запустить», «Отменить». При «Отменить» — статус `Blocked`,
журнал: «Пользователь отменил релиз».

### Шаг 3. Запустить релиз
Команда (таймаут 15 минут):

```powershell
.\gradlew.bat release "-Prelease.useAutomaticVersion=true" "-Prelease.releaseVersion=0.0.1" "-Prelease.newVersion=0.0.2-SNAPSHOT"
```

Ожидается `BUILD SUCCESSFUL`.

### Шаг 4. Проверить результат локально

| Команда | Ожидается |
|---|---|
| `git tag --list "v0.0.1"` | `v0.0.1` |
| `Select-String -Path gradle.properties -Pattern '^version='` | `version=0.0.2-SNAPSHOT` |
| `git log --oneline -3` | два верхних коммита с префиксом `[Gradle Release Plugin]` |
| `git show v0.0.1:gradle.properties` | содержит `version=0.0.1` |
| `git ls-remote --tags origin v0.0.1` | одна строка с `refs/tags/v0.0.1` |
| `git status --short` | пустой вывод |

### Шаг 5. Проверить GitHub Release
1. Подождать 5 минут после шага 3.
2. `webfetch` URL `https://api.github.com/repos/sparky-anvil/fast-cow-screenshot/releases/tags/v0.0.1`
   (format `text`). Ожидается JSON с `"tag_name": "v0.0.1"` и в `assets` — `"name": "fast-cow-screenshot-0.0.1-windows-x64.zip"`.
3. Если ответ 404 — подождать ещё 5 минут и повторить, всего не более 3 попыток. После 3 неудач — попросить
   пользователя открыть `https://github.com/sparky-anvil/fast-cow-screenshot/actions` и прислать статус и лог job
   `Release`; статус `Blocked`, CLR к Tech с этим логом.

### Шаг 6. Проверить артефакт
1. Скачать: `Invoke-WebRequest -Uri "https://github.com/sparky-anvil/fast-cow-screenshot/releases/download/v0.0.1/fast-cow-screenshot-0.0.1-windows-x64.zip" -OutFile "$env:TEMP\fcs-0.0.1.zip"`.
2. `Expand-Archive "$env:TEMP\fcs-0.0.1.zip" -DestinationPath "$env:TEMP\fcs-0.0.1" -Force`.
3. `Test-Path "$env:TEMP\fcs-0.0.1\FastCScreen\FastCScreen.exe"` → `True`.
4. Попросить пользователя запустить этот exe, затем выполнить `Select-String -Path "$env:LOCALAPPDATA\fast-cow-screenshot\logs\app.log" -Pattern "fast-cow-screenshot 0.0.1 started"` → минимум одна строка. Пользователь закрывает приложение через «Выход» в трее.

### Шаг 7. Зафиксировать результат
1. Заполнить журнал этой задачи (вывод шагов 4–6), поставить статус `Review` здесь и в `docs/tech/README.md`.
2. Спросить пользователя через `question`, закоммитить ли эти 2 файла с сообщением `TASK-022: release 0.0.1`
   и отправить в `origin/main`. Выполнить только при согласии.

## 8. Алгоритм и логика

Н/П — последовательность в разделе 7.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение |
|---|---|---|
| 1 | `release` падает до создания тега (тесты, проверки плагина) | Плагин откатывает `gradle.properties`. Статус `Blocked`, CLR к Tech с полным выводом. Повторно не запускать |
| 2 | `release` упал после создания тега (например, push отклонён) | Не удалять тег и не делать `git reset`. Статус `Blocked`, CLR к Tech с выводом `git log --oneline -3`, `git tag`, `git status` |
| 3 | `Unresolved reference` / плагин не найден | Статус `Blocked`, CLR к Tech (дефект TASK-020) |
| 4 | Workflow упал | Тег не удалять. Статус `Blocked`, CLR к Tech с логом job |
| 5 | В логе приложения нет `0.0.1 started` | Статус `Blocked`, CLR к Tech с содержимым `app\fast-cow-screenshot-*.jar!/app-info.properties` (через `Expand-Archive` jar как zip) |

## 10. Константы и тексты

| Имя | Значение |
|---|---|
| Релизная версия | `0.0.1` |
| Следующая версия | `0.0.2-SNAPSHOT` |
| Тег | `v0.0.1` |
| Файл релиза | `fast-cow-screenshot-0.0.1-windows-x64.zip` |
| Сообщение коммита журнала | `TASK-022: release 0.0.1` |

## 11. Тесты

Н/П — новых тестов нет; все существующие выполняет `release` (`build`).

## 12. Проверка

Шаги 4–6 раздела 7 — это и есть проверка.

## 13. Критерии готовности (Definition of Done)

- [ ] Тег `v0.0.1` есть в `origin`.
- [ ] `gradle.properties` на `main` — `version=0.0.2-SNAPSHOT`.
- [ ] GitHub Release `v0.0.1` содержит `fast-cow-screenshot-0.0.1-windows-x64.zip`.
- [ ] Приложение из ZIP пишет в лог `fast-cow-screenshot 0.0.1 started`.
- [ ] Журнал заполнен, статус `Review`.

## 14. Чего НЕ делать

- Не редактировать `gradle.properties` руками и не создавать тег командой `git tag`.
- Не запускать `release` без подтверждения пользователя и не запускать его повторно после ошибки.
- Не использовать `git push --force`, `git reset --hard`, `git tag -d`.
- Не менять код, сборку и workflow.
- Не редактировать файлы (включая этот) между шагом 1 и шагом 4.

## 15. Ловушки и подсказки

- Плагин падает на любом изменённом или неотслеживаемом файле (`failOnCommitNeeded`, `failOnUnversionedFiles`): поэтому
  статус `In Progress` должен быть закоммичен пользователем заранее (предусловие 4), а журнал заполняется только на шаге 7.
- Плагин требует, чтобы `main` совпадала с `origin/main` (`failOnPublishNeeded`, `failOnUpdateNeeded`).
- Аргументы `-P…` в PowerShell брать в двойные кавычки целиком, иначе `.` и `=` разбираются неверно.
- `build` внутри `release` запускает Windows-тесты с реальным WinAPI — не сворачивать сеанс и не блокировать экран
  во время шага 3.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

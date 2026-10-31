# ADR-004: Процесс релиза — net.researchgate.release, ZIP app-image, GitHub Actions по тегу

| Поле | Значение |
|---|---|
| Статус | Accepted |
| Дата | 2026-10-31 |
| Автор | Tech |
| Связанные | ADR-001, TDD-002 |

## Контекст

Нужно выпустить первую версию `0.0.1`. Сейчас версия `1.0.0` захардкожена в трёх местах (`build.gradle.kts`:
`version`, `packageVersion`; `AppInfo.VERSION`), тегов нет, CI нет, `gh` CLI на машине разработчика не установлен.
Дистрибутив — jpackage app-image (`createDistributable`, ADR-001, CLR-004).

## Рассмотренные варианты

Управление версией:
1. **net.researchgate.release 3.2.0** — версия в `gradle.properties`, задача `release`: проверка чистого дерева и
   синхронизации с remote, снятие `-SNAPSHOT`, `build`, коммит, тег, следующая `-SNAPSHOT`-версия, push.
2. **axion-release** — версия из git-тегов.
3. **Без плагина** — ручное редактирование и ручной тег.

Артефакт: ZIP app-image / ZIP + MSI / только тег. Место сборки: GitHub Actions по тегу / локально.

## Сравнение

| Критерий | researchgate | axion | вручную |
|---|---|---|---|
| Версия видна без git | да (`gradle.properties`) | нет | да |
| Защита от ошибок (грязное дерево, неотправленные коммиты) | да | частично | нет |
| Сложность | низкая | средняя | низкая, но ручная |
| Зависимости | 1 плагин сборки | 1 плагин сборки | нет |

## Решение

Выбрано пользователем:
- `net.researchgate.release` **3.2.0**, тег `v<version>` (`v0.0.1`), релизная ветка `main`;
- версия в `gradle.properties` (`version=0.0.1-SNAPSHOT`), единственный источник; `packageVersion` и
  `AppInfo.VERSION` выводятся из неё;
- артефакт — `fast-cow-screenshot-<version>-windows-x64.zip` с app-image; MSI не публикуется;
- исполняемый файл дистрибутива — `FastCScreen.exe` (`packageName = "FastCScreen"`); имя проекта, `AppInfo.NAME` и
  каталоги настроек/логов остаются `fast-cow-screenshot`;
- GitHub Actions (`windows-latest`) по push тега `v*` собирает ZIP и создаёт GitHub Release
  (`softprops/action-gh-release@v3`).

## Последствия

- **Положительные:** одна точка правды о версии; релиз воспроизводим на чистом runner; `gh` локально не нужен.
- **Отрицательные / риски:** плагин коммитит и пушит от имени разработчика — релиз выполняется только с `main` при
  чистом дереве; ZIP не подписан (SmartScreen предупредит при первом запуске); тесты в CI не запускаются
  (WinAPI-тесты требуют интерактивного рабочего стола) — они выполняются локально задачей `release` (`build`).

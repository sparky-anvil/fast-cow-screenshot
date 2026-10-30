# TASK-019: Флаги JVM для возврата свободной памяти heap ОС и повторный замер RSS (CLR-005)

| Поле | Значение |
|---|---|
| Статус | Blocked |
| Приоритет | Must |
| Оценка | S (до 1 ч) + ~10 мин автоматического замера |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.N6 |
| ADR | ADR-001 |
| Зависит от | TASK-018 |
| Блокирует | TASK-016 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

После серии снимков RSS процесса в простое должен опускаться до ≤ 200 МБ. Для этого G1 должен возвращать ОС
неиспользуемую committed-память heap. Сделать это флагами JVM и подтвердить повторным замером.

## 2. Контекст

- Замер TASK-016 (CLR-005), Windows 11, 1 монитор 3440×1440:
  - через 60 с после старта — RSS 101 МБ;
  - после 10 снимков и 60 с простоя — RSS 438 МБ, хотя heap used после `GC.run` всего 27 МБ;
  - committed heap после снимков — 396–765 МБ.
- Причина: G1 с флагами по умолчанию (`MaxHeapFreeRatio=70`, периодический GC выключен) не уменьшает committed heap в
  простое. Committed heap входит в RSS (рабочий набор).
- Решение:
  - `-XX:G1PeriodicGCInterval=15000` — в простое G1 раз в 15 с запускает сборку и после неё отдаёт память ОС;
  - `-XX:MinHeapFreeRatio=10` и `-XX:MaxHeapFreeRatio=20` — после сборки G1 оставляет не более 20 % свободного места в
    committed heap.
- `-Xmx768m` не меняется: он нужен для захвата 3 × 4K.

**Выдержки из требований (дословно):**

> **REQ-001.N6** Пиковое потребление heap при захвате 3 × 4K ≤ 400 МБ. В простое (в трее) RSS процесса ≤ 200 МБ.

## 3. Предусловия

- TASK-018 `Done`. `.\gradlew.bat build` зелёный.
- В каждой команде Gradle: `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot'`.
- Пользователь разрешил автоматический замер (эмуляцию `Ctrl+Shift+1`). Перед запуском замера Dev через `question`
  предупреждает его: «не трогать клавиатуру и мышь ~5 минут».

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `build.gradle.kts` | Изменить | список `appJvmArgs` |

**Файлы, которые изменять нельзя:** все, кроме перечисленного. Скрипт замера (раздел 8) хранится вне репозитория:
`C:\Users\8523~1\AppData\Local\Temp\kilo\rss.ps1`.

## 5. Зависимости

Новых нет.

## 6. Контракты

`appJvmArgs` после изменения (Эталонный фрагмент; порядок строк — как здесь):

```kotlin
val appJvmArgs = listOf(
    "--enable-native-access=ALL-UNNAMED",
    "--sun-misc-unsafe-memory-access=allow",
    "-Xmx768m",
    // Возврат свободной памяти heap ОС в простое (REQ-001.N6, CLR-005).
    "-XX:G1PeriodicGCInterval=15000",
    "-XX:MinHeapFreeRatio=10",
    "-XX:MaxHeapFreeRatio=20",
)
```

## 7. Пошаговая реализация

1. Изменить `appJvmArgs` по разделу 6. → `.\gradlew.bat build` зелёный (флаги попадают и в тестовую JVM; тесты
   должны проходить).
2. `.\gradlew.bat createDistributable` → `BUILD SUCCESSFUL`.
3. Проверить, что флаги попали в дистрибутив: файл
   `build\compose\binaries\main\app\fast-cow-screenshot\app\fast-cow-screenshot.cfg` содержит в секции `[JavaOptions]`
   строки `java-options=-XX:G1PeriodicGCInterval=15000`, `java-options=-XX:MinHeapFreeRatio=10`,
   `java-options=-XX:MaxHeapFreeRatio=20`. Если строк нет — статус `Blocked`, CLR к Tech.
4. Закрыть все экземпляры (`Get-Process fast-cow-screenshot | Stop-Process -Force`), запомнить время начала, сохранить скрипт
   раздела 8 в `C:\Users\8523~1\AppData\Local\Temp\kilo\rss.ps1` и запустить его в фоне:
   `Start-Process powershell -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','C:\Users\8523~1\AppData\Local\Temp\kilo\rss.ps1' -WindowStyle Hidden`.
5. Дождаться строки `DONE` в `C:\Users\8523~1\AppData\Local\Temp\kilo\rss.txt` (примерно 5 мин; опрашивать раз в 2 мин).
6. Удалить PNG, созданные замером: файлы в `[Environment]::GetFolderPath('MyPictures') + '\Screenshots'` с
   `CreationTime` позже времени из шага 4. Остановить процесс приложения.
7. Оценить результат по разделу 9, заполнить журнал.

## 8. Алгоритм и логика

Скрипт замера `rss.ps1` (Эталонный фрагмент, сохранить дословно):

```powershell
Add-Type 'using System;using System.Runtime.InteropServices;public class K{[DllImport("user32.dll")]public static extern void keybd_event(byte b,byte s,uint f,UIntPtr e);}'
function Press1 { foreach($k in 0x11,0x10,0x31){[K]::keybd_event($k,0,0,[UIntPtr]::Zero)}; Start-Sleep -Milliseconds 40; foreach($k in 0x31,0x10,0x11){[K]::keybd_event($k,0,2,[UIntPtr]::Zero)} }
$jc='C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\jcmd.exe'; $out='C:\Users\8523~1\AppData\Local\Temp\kilo\rss.txt'; Remove-Item $out -ErrorAction SilentlyContinue
function Snap($label){ $p=Get-Process fast-cow-screenshot | Sort WorkingSet64 -Descending | Select -First 1; $h=(& $jc $p.Id GC.heap_info 2>&1 | Out-String); $c=[regex]::Match($h,'committed (\d+)K').Groups[1].Value; $u=[regex]::Match($h,'used (\d+)K').Groups[1].Value; "$label WS=$([int]($p.WorkingSet64/1MB))MB committedK=$c usedK=$u" | Add-Content $out }
Start-Process 'D:\github\fast-cow-screenshot\build\compose\binaries\main\app\fast-cow-screenshot\fast-cow-screenshot.exe'
Start-Sleep 60; Snap 'A idle+60s'
1..10 | % { Press1; Start-Sleep 4 }; Start-Sleep 60; Snap 'B after10+60s'
1..100 | % { Press1; Start-Sleep -Milliseconds 1500 }; Start-Sleep 60; Snap 'C after110+60s'
Start-Sleep 60; Snap 'D after110+120s'
'DONE' | Add-Content $out
```

`Snap` не вызывает `GC.run`: это нужно, чтобы измерить поведение без принудительной сборки.

## 9. Ошибки и граничные случаи

| # | Результат | Поведение |
|---|---|---|
| 1 | `A`, `B`, `D` — WS ≤ 200 МБ | Цель достигнута. Статус `Review` |
| 2 | `B` или `D` — WS > 200 МБ, при этом `committedK` ≤ 150 000 | heap уже возвращён ОС, остаток — нативная память (Skia/AWT). Статус `Blocked`, CLR к **Analyst** с вопросом: «N6 „в простое“ — до первого снимка (сейчас 101 МБ) или после снимков (сейчас N МБ)? Допустим ли порог X МБ после снимков?»; в CLR — все строки `rss.txt` |
| 3 | `B` или `D` — WS > 200 МБ и `committedK` > 150 000 | Флаги не сработали. Статус `Blocked`, CLR к Tech со строками `rss.txt` и содержимым `.cfg` |
| 4 | `app.log` за запуск содержит `ERROR` | Статус `Blocked`, CLR к Tech с фрагментом лога |
| 5 | Тесты `.\gradlew.bat build` падают после изменения флагов | Статус `Blocked`, CLR к Tech с выводом |

## 10. Константы и тексты

`G1PeriodicGCInterval=15000` (мс), `MinHeapFreeRatio=10`, `MaxHeapFreeRatio=20`, порог WS 200 МБ, порог committed 150 000 КБ.

## 11. Тесты

Н/П — автотестов нет; проверка — замер раздела 8.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat createDistributable` | `BUILD SUCCESSFUL`, `.cfg` содержит три новых флага |
| Скрипт раздела 8 | `rss.txt` с 4 строками `A`–`D` и `DONE` |

## 13. Критерии готовности (Definition of Done)

- [x] `appJvmArgs` — как в разделе 6.
- [x] Флаги присутствуют в `fast-cow-screenshot.cfg`.
- [x] Строки `A`–`D` из `rss.txt` записаны в журнал; итог по разделу 9.
- [x] PNG замера удалены.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не менять `-Xmx768m`, не менять GC (`-XX:+UseSerialGC` и т. п.).
- Не вызывать `System.gc()` в коде, не менять файлы в `src/`.
- Не выполнять остальные замеры TASK-016 — это TASK-016.

## 15. Ловушки и подсказки

- PID: процессов `fast-cow-screenshot` два — лаунчер (~6 МБ) и JVM. Берётся процесс с наибольшим `WorkingSet64`.
- `jcmd` — из JDK (`C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\jcmd.exe`). В дистрибутиве его нет.
- Если `jcmd` выводит `Unknown diagnostic command`, значит, ему передан не один PID, а массив.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-31 | Blocked | `appJvmArgs` дополнены тремя флагами G1; `.\gradlew.bat build` и `createDistributable` успешны, флаги есть в `fast-cow-screenshot.cfg`. Автозамер (110 снимков, 0 ERROR): A idle+60s WS=102MB committedK=10240 usedK=6818; B after10+60s WS=266MB committedK=37888 usedK=27877; C after110+60s WS=270MB committedK=37888 usedK=28160; D after110+120s WS=271MB committedK=37888 usedK=28156. Heap возвращён ОС (было 396–765 МБ), но RSS > 200 МБ при committed ≤ 150 МБ — по §9 п.2 → [CLR-006](../clarifications/CLR-006-n6-rss-definition.md) к Analyst. 110 PNG замера удалены. |

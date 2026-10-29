# TASK-008: Реализовать запись снимка в буфер обмена Windows (CF_DIB, повторы)

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.23, .24, .25, .26, N1, N2; US-004 AC-1, AC-3, AC-4 |
| ADR | ADR-002 |
| Зависит от | TASK-005, TASK-006, TASK-007 |
| Блокирует | TASK-010 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Записывать `BufferedImage` в системный буфер обмена в формате `CF_DIB` (24 бит, строки снизу вверх). Если буфер
занят другим процессом, делать до 8 попыток с паузой 100 мс. Данные должны оставаться в буфере после выхода
из приложения.

## 2. Контекст

- `CF_DIB` (код 8) — блок памяти `GlobalAlloc(GMEM_MOVEABLE)`: заголовок `BITMAPINFOHEADER` (40 байт), за ним пиксели.
  При 24 битах на пиксель байты идут в порядке `B, G, R`. Каждая строка дополняется нулями до кратности 4 байтам
  (`stride`). При положительной высоте первой в памяти идёт **нижняя** строка изображения. Этот формат вставляют
  Paint, Word, Telegram и Chrome/Edge.
- 24 бита выбраны намеренно: часть приложений трактует нулевую альфу 32-битного DIB как прозрачность.
- Последовательность: `OpenClipboard(owner)` → `EmptyClipboard()` → `SetClipboardData(CF_DIB, hMem)` → `CloseClipboard()`.
  После **успешного** `SetClipboardData` памятью владеет система — `GlobalFree` вызывать нельзя. При неудаче — нужно.
- Данные передаются сразу (без отложенного рендеринга), поэтому остаются после выхода (REQ-001.26).
- Владелец — `NativeMessageLoop.messageWindow` (TASK-007). С `null`-владельцем `SetClipboardData` после `EmptyClipboard`
  завершается ошибкой.
- `writer.write` вызывается на `fcs-capture`.

**Выдержки из требований (дословно):**

> **REQ-001.23** После каждого успешного захвата система должна автоматически помещать изображение в системный буфер обмена Windows в формате, который можно вставить как изображение в Paint, Microsoft Word, Telegram Desktop и браузер (Chrome/Edge).
>
> **REQ-001.25** Если буфер обмена временно занят другим процессом, система должна повторить попытку (не менее 5 попыток в течение не более 1 с). После неудачи последней попытки система должна показать уведомление «Не удалось скопировать в буфер обмена»; файл при этом сохраняется.
>
> **REQ-001.26** Изображение должно оставаться в буфере обмена после завершения работы приложения.

## 3. Предусловия

- TASK-005: `User32Api` (`OpenClipboard`, `EmptyClipboard`, `SetClipboardData`, `CloseClipboard`), `Kernel32Api`
  (`GlobalAlloc`, `GlobalLock`, `GlobalUnlock`, `GlobalFree`), `WinConst.CF_DIB`, `WinConst.GMEM_MOVEABLE`.
- TASK-007: `NativeMessageLoop` с `start()`, `messageWindow`, `stop()`.
- `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/ClipboardResult.kt` | Создать | sealed |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/ClipboardWriter.kt` | Создать | интерфейс |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/DibEncoder.kt` | Создать | чистый кодер |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/ClipboardRetry.kt` | Создать | повторы |
| 5 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/WindowsClipboardWriter.kt` | Создать | реализация |
| 6 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/DibEncoderTest.kt` | Создать | unit |
| 7 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/ClipboardRetryTest.kt` | Создать | unit |
| 8 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/WindowsClipboardWriterTest.kt` | Создать | integration |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.capture

/** Результат записи в буфер. reason — техническое описание для лога (на английском). */
internal sealed interface ClipboardResult {
    data object Success : ClipboardResult
    data class Failure(val reason: String) : ClipboardResult
}

/** Запись изображения в буфер обмена. Вызывать на fcs-capture. Не бросает исключений. */
internal interface ClipboardWriter {
    fun write(image: java.awt.image.BufferedImage): ClipboardResult
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** Кодирование в упакованный CF_DIB 24 бит bottom-up. Без состояния. */
internal object DibEncoder {
    const val HEADER_SIZE: Int = 40
    /** ((width * 3 + 3) / 4) * 4. */
    fun stride(width: Int): Int = TODO()
    /** HEADER_SIZE + stride(width).toLong() * height. */
    fun totalSize(width: Int, height: Int): Long = TODO()
    /**
     * Пишет заголовок (offset 0, длина 40), затем строки y = h-1 … 0 (offset 40 + i*stride, длина stride).
     * Буфер строки переиспользуется — sink должен скопировать данные до возврата.
     * @throws IllegalArgumentException если image.type != TYPE_INT_RGB.
     */
    fun write(image: java.awt.image.BufferedImage, sink: (offset: Long, buffer: ByteArray, length: Int) -> Unit): Unit = TODO()
    /** Удобная обёртка для тестов: весь DIB в ByteArray. */
    fun encode(image: java.awt.image.BufferedImage): ByteArray = TODO()
}

/** Повтор действия с паузами. */
internal object ClipboardRetry {
    const val MAX_ATTEMPTS: Int = 8
    const val DELAY_MS: Long = 100
    /**
     * Вызывает action(1), action(2)… до первого не-null результата или до maxAttempts.
     * Между попытками — sleep(delayMs); после последней паузы нет. Возвращает результат или null.
     */
    fun <T : Any> attempt(
        maxAttempts: Int = MAX_ATTEMPTS,
        delayMs: Long = DELAY_MS,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        action: (attempt: Int) -> T?,
    ): T? = TODO()
}

/** CF_DIB через WinAPI. Потокобезопасен на уровне ОС (буфер открывается и закрывается в одном вызове). */
internal class WindowsClipboardWriter(
    private val ownerWindow: () -> HWND,          // { loop.messageWindow }
    private val user32: User32Api = User32Api.INSTANCE,
    private val kernel32: Kernel32Api = Kernel32Api.INSTANCE,
) : ClipboardWriter
```

## 7. Пошаговая реализация

1. `ClipboardResult.kt`, `ClipboardWriter.kt`.
2. `DibEncoder.kt` + `DibEncoderTest` (раздел 8.1).
3. `ClipboardRetry.kt` + `ClipboardRetryTest`.
4. `WindowsClipboardWriter.kt` (раздел 8.2) + `WindowsClipboardWriterTest`.
5. Ручная проверка раздела 12.

## 8. Алгоритм и логика

### 8.1. DibEncoder.write

1. `require(image.type == BufferedImage.TYPE_INT_RGB) { "TYPE_INT_RGB expected" }`; `w`, `h`, `s = stride(w)`,
   `data = (image.raster.dataBuffer as DataBufferInt).data`.
2. Заголовок — `ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)`: `putInt(40)`, `putInt(w)`, `putInt(h)`,
   `putShort(1)`, `putShort(24)`, `putInt(0)` (BI_RGB), `putInt(s * h)`, `putInt(0)`, `putInt(0)`, `putInt(0)`, `putInt(0)`.
   `sink(0, header.array(), 40)`.
3. `row = ByteArray(s)` (байты набивки остаются нулями). Для `i in 0 until h`: `y = h - 1 - i`; для `x in 0 until w`:
   `p = data[y * w + x]`; `row[3x] = (p and 0xFF)`, `row[3x+1] = (p shr 8 and 0xFF)`, `row[3x+2] = (p shr 16 and 0xFF)`
   (в `Byte`); `sink(40L + i.toLong() * s, row, s)`.

**Пример:** изображение 2×2: (0,0) = `0xFF0000`, (1,0) = `0x00FF00`, (0,1) = `0x0000FF`, (1,1) = `0xFFFFFF`. `stride = 8`,
`totalSize = 56`. Байты 40..47 (строка y = 1): `FF 00 00 FF FF FF 00 00`. Байты 48..55 (y = 0): `00 00 FF 00 FF 00 00 00`.

### 8.2. WindowsClipboardWriter.write

```
start = nanoTime
size = DibEncoder.totalSize(w, h)
hMem = kernel32.GlobalAlloc(GMEM_MOVEABLE, size) ?: return fail("GlobalAlloc failed, GetLastError=…")
ptr = kernel32.GlobalLock(hMem) ?: run { GlobalFree(hMem); return fail("GlobalLock failed…") }
try { DibEncoder.write(image) { off, buf, len -> ptr.write(off, buf, 0, len) } } finally { kernel32.GlobalUnlock(hMem) }
owner = ownerWindow()
opened = ClipboardRetry.attempt { n ->
    if (user32.OpenClipboard(owner)) true
    else { log.warn("Clipboard busy, attempt {}/{}", n, MAX_ATTEMPTS); null } }
if (opened == null) { GlobalFree(hMem); return fail("clipboard is busy") }
try {
    if (!user32.EmptyClipboard()) { val e = lastErrorText("EmptyClipboard"); GlobalFree(hMem); return fail(e) }
    if (user32.SetClipboardData(CF_DIB, hMem) == null) { val e = lastErrorText("SetClipboardData"); GlobalFree(hMem); return fail(e) }
} finally { user32.CloseClipboard() }
log.debug("Clipboard DIB {}x{} written in {} ms", w, h, ms)
return Success
```
`fail(reason)` = `log.error("Clipboard write failed: {}", reason)`; `ClipboardResult.Failure(reason)`.
Исключение из `ownerWindow()` или `DibEncoder` (`Exception`) — поймать на уровне `write`: освободить `hMem`, вернуть
`fail(e.message ?: e.javaClass.simpleName)`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | Буфер занят все 8 попыток (7 пауз = 700 мс) | `Failure("clipboard is busy")`, память освобождена | `WARN` ×8, `ERROR "Clipboard write failed: clipboard is busy"` | уведомление — TASK-011 |
| 2 | Буфер освободился на попытке 3 | Запись выполнена | `WARN` ×2 | `Success` |
| 3 | `GlobalAlloc` не выделил память (11520×2160 ≈ 75 МБ) | `Failure` | `ERROR` | — |
| 4 | `GlobalUnlock` вернул `false` | Это норма (счётчик блокировок стал 0) — не ошибка | — | — |
| 5 | Не `TYPE_INT_RGB` | `IllegalArgumentException` → `Failure` | `ERROR` | — |

## 10. Константы и тексты

`ClipboardRetry.MAX_ATTEMPTS = 8`, `ClipboardRetry.DELAY_MS = 100`, `DibEncoder.HEADER_SIZE = 40`. Текстов UI нет
(текст уведомления — TASK-011).

## 11. Тесты

| # | Класс / метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `DibEncoderTest` / `` `stride is padded to 4 bytes`() `` | — | `stride(1)`, `stride(2)`, `stride(4)` | `4`, `8`, `12` | — |
| 2 | `` `header fields`() `` | 2×2 из 8.1 | `encode` | размер 56; `int@0 = 40`, `int@4 = 2`, `int@8 = 2`, `short@12 = 1`, `short@14 = 24`, `int@20 = 16` (LE) | REQ-001.23 |
| 3 | `` `pixels are bgr bottom up`() `` | 2×2 из 8.1 | `encode` | байты 40..55 — как в примере 8.1 | REQ-001.23 |
| 4 | `` `alpha byte is ignored`() `` | 1×1, пиксель записан в `data` как `0x7F102030` | `encode` | байты 40..42 = `30 20 10` | — |
| 5 | `` `rejects argb image`() `` | `TYPE_INT_ARGB` | `encode` | `IllegalArgumentException` | — |
| 6 | `ClipboardRetryTest` / `` `stops on first success`() `` | action: null, null, "ok" | `attempt(sleep = { sleeps += it })` | `"ok"`; 3 вызова; `sleeps == [100, 100]` | REQ-001.25 |
| 7 | `` `gives up after max attempts`() `` | action всегда null | `attempt(sleep = …)` | `null`; 8 вызовов; 7 пауз по 100 (сумма 700 ≤ 1000) | REQ-001.25 |
| 8 | `WindowsClipboardWriterTest` / `` `written image can be read back`() `` `@EnabledOnOs(OS.WINDOWS)` | `NativeMessageLoop` запущен; изображение 300×200, пиксель (10,10) = `0x336699` | `write(image)`, затем `Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.imageFlavor) as Image` | `Success`; ширина 300, высота 200 (через `BufferedImage` из полученного `Image`); пиксель (10,10) `and 0xFFFFFF == 0x336699` | REQ-001.23 |

В тесте 8 чтение через AWT разрешено (запрет `conventions.md` — только на **запись** изображений через AWT). Для получения
пикселя: нарисовать полученный `Image` в `BufferedImage(300, 200, TYPE_INT_RGB)` через `createGraphics().drawImage(img, 0, 0, null)`.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.platform.windows.DibEncoderTest"` | 5 тестов |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.platform.windows.ClipboardRetryTest"` | 2 теста |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsClipboardWriterTest"` | 1 тест |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (пользователь): сразу после теста 8 (процесс тестов уже завершён) открыть Paint → Ctrl+V → вставилось
изображение 300×200. Так проверяется REQ-001.26 (данные пережили выход процесса). Полная проверка в Word, Telegram
и Chrome — в TASK-015.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–5 выполнены; контракты — как в разделе 6.
- [x] После успешного `SetClipboardData` нет `GlobalFree`; во всех ветках неудачи — есть.
- [x] 8 тестов проходят; ручная проверка записана.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать AWT `Clipboard.setContents` и не добавлять форматы `PNG`/`CF_BITMAP`/`CF_DIBV5`.
- Не кодировать PNG для буфера.
- Не вызывать `Thread.sleep` напрямую в `WindowsClipboardWriter` — только через `ClipboardRetry`.

## 15. Ловушки и подсказки

- `Pointer.write(offset: Long, buf: ByteArray, index: Int, length: Int)`.
- `(p and 0xFF).toByte()` — в Kotlin `Byte` знаковый, это нормально.
- `CloseClipboard` вызывать всегда после успешного `OpenClipboard` (`finally`), иначе буфер останется заблокированным
  для всей системы.
- `GlobalAlloc` — `dwBytes: Long` (SIZE_T на x64).

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-29 | Review | Реализованы ClipboardResult, ClipboardWriter, DibEncoder, ClipboardRetry, WindowsClipboardWriter и 8 тестов (5 + 2 + 1), все пройдены; ``gradlew build`` - BUILD SUCCESSFUL. Ручная проверка (Paint, Ctrl+V после завершения процесса тестов): «Работает как описано». TASK-007 по решению пользователя переведена в Done. Отклонений нет. |

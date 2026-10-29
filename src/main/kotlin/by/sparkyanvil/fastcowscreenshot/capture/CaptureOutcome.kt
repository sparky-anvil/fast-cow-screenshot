package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.Screenshot
import by.sparkyanvil.fastcowscreenshot.storage.SaveResult

/** Итог захвата. save == null — автосохранение выключено. */
internal data class CaptureOutcome(val screenshot: Screenshot, val clipboard: ClipboardResult, val save: SaveResult?)
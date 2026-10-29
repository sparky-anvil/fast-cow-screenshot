package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint

/** Сохранённый указатель. Владеет нативной копией иконки; close() идемпотентен. */
internal interface CursorSnapshot : AutoCloseable {
    /** Позиция горячей точки, физические координаты стола. */
    val hotspot: PixelPoint
}
package by.sparkyanvil.fastcowscreenshot.capture

/** Уведомления. Методы вызываются на EDT. */
internal interface CaptureNotifier {
    fun showOutcome(outcome: CaptureOutcome)
    fun showMessage(title: String, lines: List<String>)
}
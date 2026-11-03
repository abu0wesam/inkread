package dev.jraghavan.inkread

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import java.util.Locale

/** Arabic UI: forces a right-to-left layout and an e-ink-friendly Arabic typeface for the chrome. */
object ArabicUi {
    private val AR = Locale("ar")
    private var fontLoaded = false

    fun wrap(base: Context): Context {
        loadFont(base)
        Locale.setDefault(AR)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(AR)
        cfg.setLayoutDirection(AR)
        return base.createConfigurationContext(cfg)
    }

    private fun loadFont(c: Context) {
        if (fontLoaded) return
        try {
            val face = Typeface.createFromAsset(c.assets, "fonts/FreeSerif.ttf")
            // Bold throughout: thin strokes wash out on e-ink.
            val bold = Typeface.create(face, Typeface.BOLD)
            Ink.serif = bold
            Ink.serifItalic = bold
            Ink.serifBold = bold
            Ink.mono = bold
            fontLoaded = true
        } catch (_: Exception) {
            // Keep the system faces.
        }
    }
}

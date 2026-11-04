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

    /** Adds a bold floating "رجوع" button (bottom-left) that closes the screen. */
    fun addBack(a: android.app.Activity) {
        val decor = a.window.decorView as? android.widget.FrameLayout ?: return
        if (decor.findViewWithTag<android.view.View>("ink_back") != null) return
        val d = a.resources.displayMetrics.density
        decor.addView(
            android.widget.ImageView(a).apply {
                tag = "ink_back"; setImageResource(R.drawable.ic_menu_back); contentDescription = "رجوع"
                setBackgroundResource(R.drawable.ink_dialog_bg)
                val p = (12 * d).toInt(); setPadding(p, p, p, p)
                setOnClickListener { a.onBackPressed() }
            },
            android.widget.FrameLayout.LayoutParams((64 * d).toInt(), (64 * d).toInt(), android.view.Gravity.BOTTOM or android.view.Gravity.LEFT).apply {
                setMargins((16 * d).toInt(), 0, 0, (16 * d).toInt())
            },
        )
    }
}

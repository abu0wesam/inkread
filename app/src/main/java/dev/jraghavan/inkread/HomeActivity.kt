package dev.jraghavan.inkread

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipFile

/**
 * Home: a file browser over the device's Document folder (like the Supernote file manager) — folders
 * and books in a paged grid, with the read position under each book. It lists the files in place
 * (nothing is copied) and refreshes the moment a file appears, changes, or is removed.
 */
class HomeActivity : Activity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(ArabicUi.wrap(newBase))

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val covers = LruCache<String, Bitmap>(48)
    private val root: File = File(Environment.getExternalStorageDirectory(), "Document")
    private var dir: File = root
    private var page = 0
    private var observer: FileObserver? = null
    private var entries: List<File> = emptyList()

    private lateinit var crumb: TextView
    private lateinit var content: FrameLayout
    private lateinit var pager: LinearLayout
    private val refresh = Runnable { reload() }

    private val cols = 4
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ink.uiScale = DisplayPrefs(this).uiScale
        setContentView(build())
        DailyAutoCompileWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (!Environment.isExternalStorageManager()) {
            askAccess()
            return
        }
        reload()
        watch()
        UpdateGate.maybeCheckOnLaunch(this)
    }

    override fun onPause() {
        observer?.stopWatching()
        observer = null
        super.onPause()
    }

    override fun onBackPressed() {
        if (dir != root && dir.parentFile != null) {
            dir = dir.parentFile!!
            page = 0
            watch()
            reload()
        } else {
            super.onBackPressed()
        }
    }

    private var downX = 0f
    private var downY = 0f
    private var lastPages = 1

    override fun dispatchTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = e.x - downX
                if (Math.abs(dx) > dp(90) && Math.abs(dx) > Math.abs(e.y - downY) * 2) {
                    // Default (Arabic): swipe left->right = next page.
                    val next = if (AppSettings.flipRtl(this)) dx > 0 else dx < 0
                    val to = (page + if (next) 1 else -1).coerceIn(0, lastPages - 1)
                    if (to != page) { page = to; render() }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(e)
    }

    private fun askAccess() {
        content.removeAllViews()
        content.addView(
            TextView(this).apply {
                text = "يحتاج التطبيق إذن الوصول إلى الملفات ليقرأ مجلد المستندات.\nاضغط هنا لمنحه."
                textSize = Ink.sp(20f); typeface = Ink.serifBold; gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                setOnClickListener {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")),
                    )
                }
            },
            FrameLayout.LayoutParams(-1, -1),
        )
    }

    /** Re-list instantly whenever something changes in the open folder. */
    private fun watch() {
        observer?.stopWatching()
        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF
        observer = object : FileObserver(dir, mask) {
            override fun onEvent(event: Int, path: String?) {
                ui.removeCallbacks(refresh)
                ui.postDelayed(refresh, 250)
            }
        }.also { it.startWatching() }
    }

    private fun isBook(f: File) = f.isFile && f.extension.lowercase() in setOf("epub", "pdf", "cbz", "txt")

    private fun reload() {
        if (!dir.exists()) dir = root
        val all = dir.listFiles()?.filter { !it.name.startsWith(".") && !it.name.endsWith(".inkread") } ?: emptyList()
        entries = all.filter { it.isDirectory }.sortedBy { it.name.lowercase() } +
            all.filter { isBook(it) }.sortedBy { it.name.lowercase() }
        crumb.text = "Supernote  ‹  " + dir.relativeTo(root.parentFile ?: root).path.replace("/", "  ‹  ")
        render()
    }

    private fun build(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        crumb = TextView(this).apply {
            textSize = Ink.sp(20f); typeface = Ink.serifBold; setTextColor(Color.BLACK); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            textDirection = View.TEXT_DIRECTION_RTL
            setOnClickListener { onBackPressed() }
        }
        header.addView(
            android.widget.ImageView(this).apply {
                setImageResource(R.drawable.ic_menu_back); contentDescription = "رجوع"
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { onBackPressed() }
            },
        )
        header.addView(crumb, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(
            TextView(this).apply {
                text = "☰"; textSize = Ink.sp(30f); typeface = Ink.serifBold; setTextColor(Color.BLACK)
                setPadding(dp(18), 0, dp(18), 0)
                setOnClickListener { menu() }
            },
        )
        col.addView(header)
        col.addView(View(this).apply { setBackgroundColor(Color.BLACK) }, LinearLayout.LayoutParams(-1, dp(2)))
        content = FrameLayout(this)
        content.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) render()
        }
        col.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        pager = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        col.addView(pager, LinearLayout.LayoutParams(-1, dp(64)))
        return col
    }

    private fun menu() {
        val items = arrayOf("الصحيفة اليومية", "المكتبة والتنزيل", "رفّ الكتب", "فتح ملف من خارج المستندات", "الإعدادات", "تحديث")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> startActivity(Intent(this, DailyActivity::class.java))
                1 -> startActivity(Intent(this, OpdsActivity::class.java))
                2 -> startActivity(Intent(this, ShelfActivity::class.java))
                3 -> startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_PICK, true))
                4 -> startActivity(Intent(this, SettingsActivity::class.java))
                else -> reload()
            }
        }.show()
    }

    private fun render() {
        val w = content.width
        val h = content.height
        if (w == 0 || h == 0) return
        content.removeAllViews()
        val cellW = (w - dp(24)) / cols
        val cellH0 = (cellW * 1.42f).toInt() + dp(78)
        val rows = Math.round(h.toFloat() / cellH0).coerceAtLeast(1)
        val cellH = (h - dp(6)) / rows // stretch so the grid fills the whole screen
        val perPage = rows * cols
        val pages = ((entries.size + perPage - 1) / perPage).coerceAtLeast(1)
        lastPages = pages
        page = page.coerceIn(0, pages - 1)
        val slice = entries.drop(page * perPage).take(perPage)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(6), dp(12), 0) }
        slice.chunked(cols).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { row.addView(cell(it, cellW, cellH)) }
            grid.addView(row, LinearLayout.LayoutParams(-1, cellH))
        }
        if (entries.isEmpty()) {
            grid.addView(
                TextView(this).apply {
                    text = "لا ملفات في هذا المجلد"; textSize = Ink.sp(20f); typeface = Ink.serifBold
                    setTextColor(Color.DKGRAY); gravity = Gravity.CENTER; setPadding(0, dp(80), 0, 0)
                },
                LinearLayout.LayoutParams(-1, -2),
            )
        }
        content.addView(grid, FrameLayout.LayoutParams(-1, -1))
        pager.removeAllViews()
        fun btn(label: String, to: Int, enabled: Boolean) = TextView(this).apply {
            text = label; textSize = Ink.sp(28f); typeface = Ink.serifBold
            setTextColor(if (enabled) Color.BLACK else Color.LTGRAY)
            setPadding(dp(26), dp(8), dp(26), dp(8))
            if (enabled) setOnClickListener { page = to; render() }
        }
        // RTL: the first control sits at the right edge, so "first" is on the right.
        pager.addView(btn("⏭", 0, page > 0))
        pager.addView(btn("▶", page - 1, page > 0))
        pager.addView(
            TextView(this).apply {
                text = "${page + 1} / $pages"; textSize = Ink.sp(20f); typeface = Ink.serifBold
                setTextColor(Color.BLACK); setPadding(dp(18), 0, dp(18), 0)
                textDirection = View.TEXT_DIRECTION_RTL
            },
        )
        pager.addView(btn("◀", page + 1, page < pages - 1))
        pager.addView(btn("⏮", pages - 1, page < pages - 1))
    }

    private fun cell(f: File, cellW: Int, cellH: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(cellW, cellH)
            setOnClickListener { openEntry(f) }
        }
        val artH = (cellH - dp(78)).coerceAtLeast(dp(60))
        val art: View = if (f.isDirectory) folderArt(f) else coverArt(f)
        box.addView(art, LinearLayout.LayoutParams(-1, artH))
        box.addView(
            TextView(this).apply {
                text = if (f.isDirectory) f.name else f.name
                textSize = Ink.sp(15f); typeface = Ink.serifBold; setTextColor(Color.BLACK)
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setPadding(0, dp(6), 0, 0); textDirection = View.TEXT_DIRECTION_ANY_RTL
            },
        )
        box.addView(
            TextView(this).apply {
                text = if (f.isDirectory) "${f.listFiles()?.count { !it.name.startsWith(".") && !it.name.endsWith(".inkread") } ?: 0} عنصر" else progressLabel(f)
                textSize = Ink.sp(13f); typeface = Ink.serifBold; setTextColor(Color.DKGRAY); maxLines = 1
            },
        )
        return box
    }

    private fun progressLabel(f: File): String {
        val id = f.absolutePath
        val pages = Books.metaPages(this, id)
        return if (Books.progress(this, id) <= 0 && Books.metaPage(this, id) <= 0) "غير مقروء"
        else if (pages > 0) "${Books.metaPage(this, id) + 1}/$pages" else "${Books.progress(this, id)}%"
    }

    private fun folderArt(f: File): View = object : View(this) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat(); color = Color.BLACK }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat() - dp(6); val h = height.toFloat() - dp(6)
            val x = dp(3).toFloat(); val y = dp(3).toFloat(); val tab = h * 0.07f
            val path = Path().apply {
                moveTo(x, y + tab); lineTo(x, y + h); lineTo(x + w, y + h); lineTo(x + w, y + tab)
                lineTo(x + w * 0.46f, y + tab); lineTo(x + w * 0.40f, y); lineTo(x, y); close()
            }
            c.drawPath(path, p)
        }
    }

    private fun coverArt(f: File): View {
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.parseColor("#F2F2F2")) }
        val key = f.absolutePath + "@" + f.lastModified()
        covers.get(key)?.let { iv.setImageBitmap(it); return frame(iv) }
        Books.loadThumbnail(this, f.absolutePath)?.let { covers.put(key, it); iv.setImageBitmap(it); return frame(iv) }
        worker.execute {
            val bmp = makeCover(f)
            if (bmp != null) {
                covers.put(key, bmp)
                ui.post { iv.setImageBitmap(bmp) }
            }
        }
        return frame(iv)
    }

    private fun frame(v: View) = FrameLayout(this).apply {
        addView(v, FrameLayout.LayoutParams(-1, -1))
        foreground = android.graphics.drawable.GradientDrawable().apply { setStroke(dp(2), Color.BLACK); setColor(Color.TRANSPARENT) }
    }

    private fun makeCover(f: File): Bitmap? = try {
        when (f.extension.lowercase()) {
            "pdf" -> ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    r.openPage(0).use { p ->
                        val bw = 360
                        val bmp = Bitmap.createBitmap(bw, (bw * p.height.toFloat() / p.width).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
            "epub" -> ZipFile(f).use { z ->
                val e = z.entries().asSequence().firstOrNull {
                    val n = it.name.lowercase()
                    n.contains("cover") && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))
                } ?: z.entries().asSequence().firstOrNull {
                    val n = it.name.lowercase(); n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                }
                e?.let { en ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    z.getInputStream(en).use { BitmapFactory.decodeStream(it, null, o) }
                    val s = (o.outWidth / 400).coerceAtLeast(1)
                    z.getInputStream(en).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
                }
            }
            else -> null
        }
    } catch (e: Throwable) { null }

    private fun openEntry(f: File) {
        if (f.isDirectory) {
            dir = f; page = 0; watch(); reload(); return
        }
        startActivity(
            Intent(this, ReaderActivity::class.java)
                .putExtra(ReaderActivity.EXTRA_BOOK_PATH, f.absolutePath)
                .putExtra(ReaderActivity.EXTRA_BOOK_ID, f.absolutePath),
        )
    }
}

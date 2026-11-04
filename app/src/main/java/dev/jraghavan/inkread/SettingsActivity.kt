package dev.jraghavan.inkread

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * App-level settings, reached from the home gear. A calm, monochrome, scrollable page matching the
 * "Inkwell" home: an **Export** preference (safe `-annotated` copy vs. overwrite the original), a
 * **How it works** cheatsheet of the non-obvious features, and an **About** block. Programmatic
 * Views, like the rest of the shell; backed by [AppSettings].
 */
class SettingsActivity : Activity() {
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(ArabicUi.wrap(newBase))


    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val serif: Typeface get() = Ink.serifBold
    private val serifItalic: Typeface get() = Ink.serifBold
    private val mono: Typeface get() = Ink.serifBold
    private val script by lazy { Typeface.createFromAsset(assets, "fonts/pinyon_script.ttf") }

    private val ink = Color.BLACK
    private val inkSoft = Color.parseColor("#3A3A3A")
    private val textMuted = Color.parseColor("#757575")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
    }

    private fun refresh() = setContentView(buildView())

    private fun buildView(): View {
        val side = dp(28)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(side, dp(24), side, dp(40))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // ── Title bar: back · "Settings" ─────────────────────────────────────────
        column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@SettingsActivity).apply {
                text = ""; isClickable = true; setOnClickListener { finish() }
                setPadding(dp(26), dp(26), dp(26), dp(26))
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_menu_back, 0, 0, 0)
                compoundDrawablePadding = 0
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "الإعدادات"; setTextColor(ink); textSize = 28f; typeface = serif
            })
        })
        column.addView(spacer(dp(28)))

        // ── Export ───────────────────────────────────────────────────────────────
        column.addView(eyebrow("تصدير"))
        column.addView(spacer(dp(12)))
        column.addView(
            toggleRow(
                title = "استبدال الأصل عند التصدير",
                desc = "عند التفعيل يستبدل التصدير ملف PDF الأصلي بالنسخة المحرّرة بدل حفظ نسخة منفصلة بجانبه. لا يمكن التراجع عن ذلك.",
                on = AppSettings.overwriteOnExport(this),
            ) { onExportOverwriteTapped() },
        )

        // ── Reading ────────────────────────────────────────────────────────────────
        column.addView(spacer(dp(34)))
        column.addView(eyebrow("القراءة"))
        column.addView(spacer(dp(12)))
        column.addView(
            toggleRow(
                title = "شريط الأدوات في الأعلى",
                desc = "يعرض أدوات الكتابة أفقيًا أعلى الصفحة بدل عرضها عموديًا على الحافة. يغطي الشريط الجانبي نهاية كل سطر يمرّ عليه، أما العلوي فيغطي جزءًا من سطر واحد. يُفتح مطويًا في الزاوية في كل مرة. يسري عند فتح المستند التالي.",
                on = AppSettings.toolbarHorizontal(this),
            ) {
                AppSettings.setToolbarHorizontal(this, !AppSettings.toolbarHorizontal(this)); refresh()
            },
        )

        column.addView(
            toggleRow(
                title = "تثبيت شريط الأدوات يسارًا",
                desc = "يضع الشريط على الحافة اليسرى بدل اليمنى، أو في الزاوية العلوية اليسرى عند عرضه في الأعلى. يسري عند فتح المستند التالي.",
                on = AppSettings.toolbarOnLeft(this),
            ) {
                AppSettings.setToolbarOnLeft(this, !AppSettings.toolbarOnLeft(this)); refresh()
            },
        )

        column.addView(
            toggleRow(
                title = "تلميحات الأدوات",
                desc = "يعرض تلميحًا قصيرًا عند اختيار أي أداة. أوقفه متى أتقنت الأدوات؛ يشرح قسم «طريقة العمل» أدناه كل أداة.",
                on = AppSettings.toolTips(this),
            ) {
                AppSettings.setToolTips(this, !AppSettings.toolTips(this)); refresh()
            },
        )

        // ── Dictionary ─────────────────────────────────────────────────────────────
        column.addView(spacer(dp(34)))
        column.addView(eyebrow("القاموس"))
        column.addView(spacer(dp(12)))
        column.addView(
            toggleRow(
                title = "البحث عن الكلمات عبر الإنترنت",
                desc = "إذا لم يجد القاموس المحلي الكلمة يجلب تعريفها من ويكاموس، وهذا يستخدم الشبكة ويرسل الكلمة خارج جهازك. متوقف افتراضيًا.",
                on = AppSettings.onlineLookup(this),
            ) {
                AppSettings.setOnlineLookup(this, !AppSettings.onlineLookup(this)); refresh()
            },
        )

        // ── Updates (ADR-INKREAD-0014) ───────────────────────────────────────────────
        column.addView(spacer(dp(34)))
        column.addView(eyebrow("التحديثات"))
        column.addView(spacer(dp(12)))
        column.addView(
            toggleRow(
                title = "التحقق من التحديثات تلقائيًا",
                desc = "عند التشغيل يتحقق من GitHub من وجود إصدار أحدث ويعرض تثبيته. التطبيق مثبّت يدويًا فلا يوجد متجر للتحديث. فحص واحد فقط دون متابعة في الخلفية.",
                on = AppSettings.autoUpdateCheck(this),
            ) { onAutoCheckTapped() },
        )
        column.addView(spacer(dp(18)))
        column.addView(
            toggleRow(
                title = "تثبيت التحديثات تلقائيًا",
                desc = "عند العثور على إصدار أحدث ينزّله ويتحقق منه ثم ينتقل إلى التثبيت مباشرة. يظل أندرويد يطلب تأكيد التثبيت النهائي. متوقف افتراضيًا.",
                on = AppSettings.autoInstallUpdates(this),
            ) { onAutoInstallTapped() },
        )
        column.addView(spacer(dp(18)))
        column.addView(actionRow("التحقق من التحديثات الآن") { UpdateGate.checkNow(this) })

        // ── Library (ADR-INKREAD-0016) ─────────────────────────────────────────────
        column.addView(spacer(dp(34)))
        column.addView(eyebrow("المكتبة"))
        column.addView(spacer(dp(12)))
        column.addView(
            actionRow(
                if (AppSettings.opdsConfigured(this)) AppSettings.opdsUrl(this) else "ربط مكتبة calibre",
            ) { onLibraryTapped() },
        )
        column.addView(TextView(this).apply {
            text = "تصفّح خادم calibre أو Calibre-Web عبر فهرسه ونزّل الكتب مباشرة إلى رفّك. الكتب التي يبنيها calibre من وصفات الأخبار تصل بالطريقة نفسها."
            setTextColor(inkSoft); textSize = 14f; typeface = serif
            setPadding(0, dp(6), 0, 0); setLineSpacing(0f, 1.2f)
        })

        // ── How it works ───────────────────────────────────────────────────────────
        column.addView(spacer(dp(34)))
        column.addView(eyebrow("طريقة العمل"))
        column.addView(spacer(dp(8)))
        for ((title, desc) in HELP) column.addView(helpRow(title, desc))

        // ── Quit (#168) ────────────────────────────────────────────────────────────
        column.addView(spacer(dp(34)))
        column.addView(eyebrow("إغلاق"))
        column.addView(spacer(dp(12)))
        column.addView(actionRow("إغلاق inkread") { quit() })
        column.addView(TextView(this).apply {
            text = "يغلق inkread ويزيله من التطبيقات الأخيرة. يُحفظ موضعك في الكتاب وكل ما كتبته بخط يدك أولًا."
            setTextColor(inkSoft); textSize = 14f; typeface = serif
            setPadding(0, dp(6), 0, 0); setLineSpacing(0f, 1.2f)
        })

        // ── About ──────────────────────────────────────────────────────────────────
        column.addView(spacer(dp(34)))
        column.addView(eyebrow("حول"))
        column.addView(spacer(dp(10)))
        column.addView(TextView(this).apply {
            text = "InkRead"; setTextColor(ink); textSize = 40f; typeface = Typeface.create(script, Typeface.BOLD)
            paint.isFakeBoldText = true; includeFontPadding = false
        })
        column.addView(TextView(this).apply {
            text = "قارئ يعتمد الكتابة بخط اليد للحبر الإلكتروني."
            setTextColor(inkSoft); textSize = 15f; typeface = serifItalic; setPadding(0, dp(4), 0, 0)
        })
        column.addView(TextView(this).apply {
            text = "الإصدار ${versionName()}   ·   AGPL-3.0"
            setTextColor(textMuted); textSize = 13f; typeface = mono; letterSpacing = 0f
            setPadding(0, dp(8), 0, 0)
        })

        return ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            isVerticalScrollBarEnabled = false
            addView(column)
        }
    }

    /**
     * Close inkread outright (#168).
     *
     * There was no way to do this short of force-stop, which readers were resorting to. The device
     * has no system Back and the launcher leaves the task resident, so leaving the app is not the
     * same as closing it.
     *
     * [finishAndRemoveTask] rather than [finish]: it ends every activity in the task and drops it
     * from recents, which is what "closed" has to mean here. Nothing is confirmed and nothing is
     * lost by it — the reader's position and any ink are written in `ReaderActivity.onPause`, which
     * has already run by the time Settings is on screen, and the document is closed in `onDestroy`
     * as the task tears down.
     */
    private fun quit() = finishAndRemoveTask()

    /** Turning overwrite ON is destructive, so confirm; turning it OFF is immediate. */
    private fun onExportOverwriteTapped() {
        if (AppSettings.overwriteOnExport(this)) {
            AppSettings.setOverwriteOnExport(this, false); refresh(); return
        }
        AlertDialog.Builder(this, R.style.InkDialog)
            .setTitle("استبدال الأصول عند التصدير؟")
            .setMessage("سيستبدل التصدير ملف PDF الأصلي بالنسخة المحرّرة بدل حفظ نسخة منفصلة. لا يمكن التراجع عن ذلك.")
            .setPositiveButton("استبدال") { _, _ -> AppSettings.setOverwriteOnExport(this, true); refresh() }
            .setNegativeButton("إبقاء نسخة آمنة", null)
            .show()
    }

    /** Toggling auto-check off also disables auto-install (UPD-FR8) — the latter needs the check. */
    private fun onAutoCheckTapped() {
        val on = !AppSettings.autoUpdateCheck(this)
        AppSettings.setAutoUpdateCheck(this, on)
        if (!on) AppSettings.setAutoInstallUpdates(this, false)
        refresh()
    }

    /** Turning auto-install on implies auto-check on (it has nothing to act on otherwise). */
    private fun onAutoInstallTapped() {
        val on = !AppSettings.autoInstallUpdates(this)
        AppSettings.setAutoInstallUpdates(this, on)
        if (on) AppSettings.setAutoUpdateCheck(this, true)
        refresh()
    }

    // ── Pieces ──────────────────────────────────────────────────────────────────────

    /** A tappable action with a title on the left and an outlined affordance pill on the right. */
    /**
     * Server address + optional login for the OPDS library (ADR-INKREAD-0016). Three plain fields:
     * a bare host is fine, since [OpdsController.catalogRoot] completes the scheme and the `/opds`
     * path. The password note is not boilerplate — it is stored in plain text and the reader is
     * entitled to know that before typing one in (Decision 4).
     */
    private fun onLibraryTapped() {
        val pad = dp(20)
        val url = android.widget.EditText(this).apply {
            hint = "192.168.1.20:8080"
            setText(AppSettings.opdsUrl(this@SettingsActivity)); setSingleLine()
        }
        val user = android.widget.EditText(this).apply {
            hint = "اسم المستخدم (اتركه فارغًا إن لم يوجد)"
            setText(AppSettings.opdsUser(this@SettingsActivity)); setSingleLine()
        }
        val password = android.widget.EditText(this).apply {
            hint = "كلمة المرور"
            setText(AppSettings.opdsPassword(this@SettingsActivity)); setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, 0)
            addView(url); addView(user); addView(password)
            addView(TextView(this@SettingsActivity).apply {
                text = "تُحفظ كلمة المرور على هذا الجهاز كنص صريح. لا يقبل calibre هذا النوع من الدخول إلا عند تشغيله بالخيار --auth-mode=basic، أما Calibre-Web فيعمل كما هو."
                setTextColor(textMuted); textSize = 12f; typeface = mono
                setPadding(0, dp(12), 0, 0); setLineSpacing(0f, 1.25f)
            })
        }
        AlertDialog.Builder(this, R.style.InkDialog)
            .setTitle("مكتبتك")
            .setView(body)
            .setPositiveButton("حفظ") { _, _ ->
                AppSettings.setOpdsUrl(this, url.text.toString())
                AppSettings.setOpdsUser(this, user.text.toString())
                AppSettings.setOpdsPassword(this, password.text.toString())
                refresh()
            }
            .setNeutralButton("نسيان") { _, _ ->
                AppSettings.setOpdsUrl(this, "")
                AppSettings.setOpdsUser(this, "")
                AppSettings.setOpdsPassword(this, "")
                refresh()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun actionRow(title: String, onTap: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true; setOnClickListener { onTap() }
            addView(TextView(this@SettingsActivity).apply {
                text = title; setTextColor(ink); textSize = 17f; typeface = serif
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(16) })
            addView(pill("تحقق", false))
        }

    /** A setting with a title + description on the left and an On/Off pill on the right. */
    private fun toggleRow(title: String, desc: String, on: Boolean, onTap: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true; setOnClickListener { onTap() }
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@SettingsActivity).apply {
                    text = title; setTextColor(ink); textSize = 17f; typeface = serif
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = desc; setTextColor(textMuted); textSize = 13f; typeface = serif
                    setLineSpacing(0f, 1.15f); setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(16) })
            addView(pill(if (on) "تشغيل" else "إيقاف", on))
        }

    /** A bordered status pill — filled (black) when on, outlined when off. */
    private fun pill(label: String, filled: Boolean): View = TextView(this).apply {
        text = label.uppercase(); textSize = 13f; typeface = mono; letterSpacing = 0f
        gravity = Gravity.CENTER; setPadding(dp(18), dp(7), dp(18), dp(7))
        setTextColor(if (filled) Color.WHITE else ink)
        background = GradientDrawable().apply {
            setColor(if (filled) ink else Color.WHITE)
            setStroke(maxOf(1, dp(1)), ink); cornerRadius = dp(40).toFloat()
        }
        minWidth = dp(64)
    }

    private fun helpRow(title: String, desc: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, 0)
        addView(TextView(this@SettingsActivity).apply {
            text = title; setTextColor(ink); textSize = 16f; typeface = Typeface.create(serif, Typeface.BOLD)
        })
        addView(TextView(this@SettingsActivity).apply {
            text = desc; setTextColor(inkSoft); textSize = 14f; typeface = serif
            setLineSpacing(0f, 1.18f); setPadding(0, dp(2), 0, 0)
        })
    }

    private fun eyebrow(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase(); setTextColor(ink); textSize = 12f
        typeface = mono; letterSpacing = 0f
    }

    private fun spacer(h: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
    }

    private fun versionName(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    private companion object {
        /** Concise, accurate cheatsheet of the non-obvious features (title → one line). */
        val HELP = listOf(
            "القلم والتظليل" to "اكتب وظلّل على الصفحة بالقلم؛ ويبدّل شريط الأدوات العائم بينهما. اضغط القلم أو التظليل مرة أخرى لاختيار اللون والسماكة.",
            "الممحاة" to "اختر الممحاة من شريط الأدوات لمسح الخطوط، أو اقلب القلم إن كان له طرف ممحاة. اضغط الممحاة مرة أخرى لاختيار حجمها.",
            "مكتبتك" to "اربط خادم calibre أو Calibre-Web من «المكتبة»، ثم تصفّحه من الرئيسية ونزّل الكتب إلى رفّك.",
            "تحديد بالحلقة" to "أحِط الكتابة أو النص بحلقة لنقله أو نسخه أو حذفه أو البحث عنه. اضغط الحلقة مرة أخرى للتبديل بين الذكي والحر.",
            "تعريف" to "حدّد كلمة واضغط «تعريف» للبحث عنها في القواميس المحلية.",
            "بحث" to "ابحث عن نص في أي موضع من المستند وتنقّل بين النتائج.",
            "العلامات" to "علّم الصفحات وارجع إليها من قائمة العلامات.",
            "تصدير" to "احفظ تعليقاتك بصيغة PDF في مجلد متزامن، أو استبدل الأصل (انظر أعلاه).",
            "العرض والخط" to "اضبط التباين والهوامش واقتصاص الصفحة وخط إعادة التدفق من لوحة الضبط في القارئ.",
        )
    }
}

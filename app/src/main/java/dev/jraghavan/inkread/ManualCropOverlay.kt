package dev.jraghavan.inkread

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** A full-screen layer on which the reader drags the crop rectangle, with confirm / cancel buttons. */
class ManualCropOverlay private constructor(
    private val activity: Activity,
    private val root: ViewGroup,
    private val onDone: (Int, RectF?) -> Unit,
) {
    // Edges, in view pixels. They start 5% inside the page and are moved by dragging them.
    private var x0 = 0f
    private var y0 = 0f
    private var x1 = 0f
    private var y1 = 0f
    private var grabbed = -1 // 0 left, 1 top, 2 right, 3 bottom
    private val line = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = Ink.sdp(7).toFloat(); color = Color.BLACK
    }
    private val shade = Paint().apply { color = Color.argb(70, 0, 0, 0) }

    private val layer = object : View(activity) {
        override fun onDraw(c: Canvas) {
            if (x1 == 0f && y1 == 0f) {
                x0 = width * 0.05f; x1 = width * 0.95f; y0 = height * 0.05f; y1 = height * 0.95f
            }
            val l = minOf(x0, x1); val r = maxOf(x0, x1); val t = minOf(y0, y1); val b = maxOf(y0, y1)
            c.drawRect(0f, 0f, width.toFloat(), t, shade)
            c.drawRect(0f, b, width.toFloat(), height.toFloat(), shade)
            c.drawRect(0f, t, l, b, shade)
            c.drawRect(r, t, width.toFloat(), b, shade)
            c.drawRect(l, t, r, b, line)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val grab = Ink.sdp(48).toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Pick the nearest bold edge line, as on the Supernote trim screen.
                    val d = floatArrayOf(Math.abs(e.x - x0), Math.abs(e.y - y0), Math.abs(e.x - x1), Math.abs(e.y - y1))
                    val i = d.indices.minByOrNull { d[it] } ?: 0
                    grabbed = if (d[i] <= grab * 2) i else -1
                }
                MotionEvent.ACTION_MOVE -> when (grabbed) {
                    0 -> x0 = e.x.coerceIn(0f, x1 - grab)
                    1 -> y0 = e.y.coerceIn(0f, y1 - grab)
                    2 -> x1 = e.x.coerceIn(x0 + grab, width.toFloat())
                    3 -> y1 = e.y.coerceIn(y0 + grab, height.toFloat())
                }
            }
            invalidate()
            return true
        }
    }

    private val holder = FrameLayout(activity)

    private fun button(label: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label; textSize = Ink.sp(18f); typeface = Ink.serifBold; setTextColor(Color.WHITE)
        setBackgroundColor(Color.BLACK); gravity = Gravity.CENTER
        setPadding(Ink.sdp(24), Ink.sdp(12), Ink.sdp(24), Ink.sdp(12))
        setOnClickListener { onClick() }
    }

    private fun close(mode: Int, result: RectF? = null) {
        root.removeView(holder)
        onDone(mode, result)
    }

    private fun build() {
        holder.addView(layer, FrameLayout.LayoutParams(-1, -1))
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(Ink.sdp(12), Ink.sdp(12), Ink.sdp(12), Ink.sdp(12))
            addView(button("موافق") {
                val w = layer.width.toFloat(); val h = layer.height.toFloat()
                close(1, RectF(minOf(x0, x1) / w, minOf(y0, y1) / h, maxOf(x0, x1) / w, maxOf(y0, y1) / h))
            })
            addView(View(activity), LinearLayout.LayoutParams(Ink.sdp(10), 1))
            addView(button("تلقائي") { close(2) })
            addView(View(activity), LinearLayout.LayoutParams(Ink.sdp(10), 1))
            addView(button("استعادة") { close(3) })
            addView(View(activity), LinearLayout.LayoutParams(Ink.sdp(10), 1))
            addView(button("إلغاء") { close(0) })
        }
        holder.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(holder, ViewGroup.LayoutParams(-1, -1))
    }

    companion object {
        /** [onDone]: 0 cancel, 1 apply the rectangle (viewport-normalized), 2 auto-trim, 3 restore. */
        fun show(activity: Activity, root: ViewGroup, onDone: (Int, RectF?) -> Unit) {
            ManualCropOverlay(activity, root, onDone).build()
        }
    }
}

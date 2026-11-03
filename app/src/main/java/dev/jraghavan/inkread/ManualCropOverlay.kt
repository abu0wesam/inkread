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
    private val onDone: (RectF?) -> Unit,
) {
    private var x0 = 0f
    private var y0 = 0f
    private var x1 = 0f
    private var y1 = 0f
    private val line = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = Ink.sdp(3).toFloat(); color = Color.BLACK
        pathEffect = DashPathEffect(floatArrayOf(28f, 16f), 0f)
    }
    private val shade = Paint().apply { color = Color.argb(70, 0, 0, 0) }

    private val layer = object : View(activity) {
        override fun onDraw(c: Canvas) {
            val l = minOf(x0, x1); val r = maxOf(x0, x1); val t = minOf(y0, y1); val b = maxOf(y0, y1)
            if (r - l < 4f || b - t < 4f) return
            c.drawRect(0f, 0f, width.toFloat(), t, shade)
            c.drawRect(0f, b, width.toFloat(), height.toFloat(), shade)
            c.drawRect(0f, t, l, b, shade)
            c.drawRect(r, t, width.toFloat(), b, shade)
            c.drawRect(l, t, r, b, line)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x0 = e.x; y0 = e.y; x1 = e.x; y1 = e.y }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> { x1 = e.x; y1 = e.y }
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

    private fun close(result: RectF?) {
        root.removeView(holder)
        onDone(result)
    }

    private fun build() {
        holder.addView(layer, FrameLayout.LayoutParams(-1, -1))
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(Ink.sdp(12), Ink.sdp(12), Ink.sdp(12), Ink.sdp(12))
            addView(TextView(activity).apply {
                text = "اسحب لتحديد منطقة القراءة"; textSize = Ink.sp(16f); typeface = Ink.serifBold
                setTextColor(Color.BLACK); setPadding(Ink.sdp(12), 0, Ink.sdp(12), 0)
            })
            addView(button("تأكيد") {
                val w = layer.width.toFloat(); val h = layer.height.toFloat()
                val r = RectF(minOf(x0, x1) / w, minOf(y0, y1) / h, maxOf(x0, x1) / w, maxOf(y0, y1) / h)
                close(r)
            })
            addView(View(activity), LinearLayout.LayoutParams(Ink.sdp(10), 1))
            addView(button("إلغاء") { close(null) })
        }
        holder.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(holder, ViewGroup.LayoutParams(-1, -1))
    }

    companion object {
        /** [onDone] gets the viewport-normalized rectangle, or null when cancelled. */
        fun show(activity: Activity, root: ViewGroup, onDone: (RectF?) -> Unit) {
            ManualCropOverlay(activity, root, onDone).build()
        }
    }
}

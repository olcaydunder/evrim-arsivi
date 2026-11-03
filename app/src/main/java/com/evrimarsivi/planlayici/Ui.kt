package com.evrimarsivi.planlayici

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

object Ui {
    val BG = Color.rgb(14, 23, 38)
    val PANEL = Color.rgb(22, 34, 54)
    val LINE = Color.rgb(42, 58, 85)
    val FG = Color.rgb(238, 241, 246)
    val MUTED = Color.rgb(167, 177, 196)
    val GOLD = Color.rgb(244, 186, 64)
    val GREEN = Color.rgb(92, 196, 132)

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, s: String, size: Float = 15f, color: Int = FG, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(0f, 1.2f)
        }

    fun button(c: Context, s: String, primary: Boolean = true, onClick: () -> Unit): Button =
        Button(c).apply {
            text = s; isAllCaps = false; textSize = 15f
            setTextColor(if (primary) BG else FG)
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 24).toFloat()
                if (primary) setColor(GOLD) else { setColor(PANEL); setStroke(dp(c, 1), LINE) }
            }
            setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10))
            setOnClickListener { onClick() }
        }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(c, 12).toFloat(); setColor(PANEL); setStroke(dp(c, 1), LINE) }
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
    }

    fun lp(c: Context, top: Int = 10): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(c, top) }

    fun row(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun spacer(c: Context): View = View(c).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

    fun loadBitmap(f: File, reqW: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= reqW) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}

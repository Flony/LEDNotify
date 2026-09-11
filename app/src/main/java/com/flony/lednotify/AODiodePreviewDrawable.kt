package com.flony.lednotify

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class AODiodePreviewDrawable(
    private var color: Int,
    private var shapeType: Int, // 0 = DOT, 1 = RING, 2 = PILL, 3 = EMPTY_PILL, 4 = ASCII
    private var radiusPx: Float,
    private var pillWidthRatio: Float = 2.5f,
    private var pillHeightRatio: Float = 1.5f,
    private var asciiText: String = "♥"
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = if (shapeType == 1 || shapeType == 3) Paint.Style.STROKE else Paint.Style.FILL
        strokeWidth = 5f
    }

    fun setAsciiText(text: String) {
        asciiText = if (text.isEmpty()) "♥" else text
        invalidateSelf()
    }

    fun setPillRatios(widthRatio: Float, heightRatio: Float) {
        pillWidthRatio = widthRatio.coerceIn(1.0f, 6.0f)
        pillHeightRatio = heightRatio.coerceIn(0.5f, 4.0f)
        invalidateSelf()
    }

    fun updateConfig(newColor: Int, newShapeType: Int, newRadiusPx: Float) {
        color = newColor
        shapeType = newShapeType
        paint.color = color
        paint.style = if (shapeType == 1 || shapeType == 3) Paint.Style.STROKE else Paint.Style.FILL
        radiusPx = newRadiusPx
        invalidateSelf()
    }

    override fun getIntrinsicWidth(): Int = 96
    override fun getIntrinsicHeight(): Int = 96

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val cx = width / 2f
        val cy = height / 2f

        val maxSupportedRadius = 75f
        val availableRadius = Math.min(width, height) / 2f - 4f
        val scale = availableRadius / maxSupportedRadius
        val mappedRadius = (radiusPx * scale).coerceIn(2f, availableRadius)

        paint.strokeWidth = (5f * (radiusPx / 38f)).coerceIn(2f, 10f)

        when (shapeType) {
            0 -> { // DOT
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, mappedRadius, paint)
            }
            1 -> { // RING
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, mappedRadius, paint)
            }
            2 -> { // SOLID PILL
                paint.style = Paint.Style.FILL
                val pillWidth = (mappedRadius * pillWidthRatio).coerceAtMost(width - 8f)
                val pillHeight = (mappedRadius * pillHeightRatio).coerceAtMost(height - 8f)
                val left = cx - (pillWidth / 2f)
                val top = cy - (pillHeight / 2f)
                val right = cx + (pillWidth / 2f)
                val bottom = cy + (pillHeight / 2f)
                val cornerRadius = Math.min(pillWidth, pillHeight) / 2f
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)
            }
            3 -> { // EMPTY PILL
                paint.style = Paint.Style.STROKE
                val pillWidth = (mappedRadius * pillWidthRatio).coerceAtMost(width - 8f)
                val pillHeight = (mappedRadius * pillHeightRatio).coerceAtMost(height - 8f)
                val left = cx - (pillWidth / 2f)
                val top = cy - (pillHeight / 2f)
                val right = cx + (pillWidth / 2f)
                val bottom = cy + (pillHeight / 2f)
                val cornerRadius = Math.min(pillWidth, pillHeight) / 2f
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)
            }
            4 -> { // ASCII / Custom Text
                paint.style = Paint.Style.FILL
                paint.textSize = (mappedRadius * 1.8f).coerceIn(14f, height - 8f)
                paint.textAlign = Paint.Align.CENTER
                val yPos = cy - ((paint.descent() + paint.ascent()) / 2f)
                canvas.drawText(asciiText, cx, yPos, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

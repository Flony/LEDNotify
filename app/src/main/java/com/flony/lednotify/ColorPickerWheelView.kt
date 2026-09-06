package com.flony.lednotify

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ColorPickerWheelView(context: Context, initialColor: Int, private val onColorChanged: (Int) -> Unit) : View(context) {

    private val hsv = FloatArray(3).apply { Color.colorToHSV(initialColor, this) }
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val finalSize = if (size > 0) size else 700
        setMeasuredDimension(finalSize, finalSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = Math.min(cx, cy) - 30f
        val wheelThickness = 45f

        // 1. Hue Ring
        val colors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
        val positions = floatArrayOf(0f, 1f/6f, 2f/6f, 3f/6f, 4f/6f, 5f/6f, 1f)
        wheelPaint.shader = SweepGradient(cx, cy, colors, positions)
        wheelPaint.strokeWidth = wheelThickness
        canvas.drawCircle(cx, cy, radius - wheelThickness / 2f, wheelPaint)

        // 2. Saturation/Value Triangle
        val hueAngle = Math.toRadians(hsv[0].toDouble())
        val innerRadius = radius - wheelThickness - 15f

        val tipX = cx + innerRadius * cos(hueAngle).toFloat()
        val tipY = cy + innerRadius * sin(hueAngle).toFloat()

        val angle1 = hueAngle + Math.PI * 2.0 / 3.0
        val angle2 = hueAngle + Math.PI * 4.0 / 3.0
        val base1X = cx + innerRadius * 0.6f * cos(angle1).toFloat()
        val base1Y = cy + innerRadius * 0.6f * sin(angle1).toFloat()
        val base2X = cx + innerRadius * 0.6f * cos(angle2).toFloat()
        val base2Y = cy + innerRadius * 0.6f * sin(angle2).toFloat()

        val path = Path().apply {
            moveTo(tipX, tipY)
            lineTo(base1X, base1Y)
            lineTo(base2X, base2Y)
            close()
        }

        val pureHue = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        trianglePaint.shader = LinearGradient(
            base1X, base1Y, base2X, base2Y,
            Color.WHITE, pureHue, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, trianglePaint)

        // Hue selector handle
        val wheelR = radius - wheelThickness / 2f
        val selX = cx + wheelR * cos(hueAngle).toFloat()
        val selY = cy + wheelR * sin(hueAngle).toFloat()
        canvas.drawCircle(selX, selY, 18f, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val x = event.x - cx
        val y = event.y - cy
        val dist = hypot(x, y)
        val radius = Math.min(cx, cy) - 30f

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (dist <= radius && dist >= radius - 60f) {
                    val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
                    var hue = angle
                    if (hue < 0) hue += 360f
                    hsv[0] = hue
                    onColorChanged(Color.HSVToColor(hsv))
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

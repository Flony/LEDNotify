package com.flony.lednotify

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.View

class LedView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private var shapeType = 1 // 0 = DOT, 1 = RING, 2 = PILL, 3 = EMPTY_PILL
    private var pillWidthRatio = 2.5f
    private var pillHeightRatio = 1.5f
    private var currentRadius = 38f
    private var customOffsetX = 0f
    private var customOffsetY = 0f
    private var isCheckerboardInverted = false
    private var paintColor = Color.GREEN

    private var brightness = 100
    private var maxAlpha = 255
    private var currentDrawAlpha = 255
    private var onDuration = 300L
    private var offDuration = 3500L
    private var fadeDuration = 2000L
    private var burnoutMethod = 2 // 0: None, 1: Pixel-Shift, 2: Dithering
    private var isShifted = false

    private var isDragging = false

    // Předalokovaná bitmapa, pole pixelů a shader pro nulové alokace v paměti (Zero-Allocation GC optimization)
    private val checkerBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val checkerPixels = IntArray(4)
    private val checkerShader = BitmapShader(checkerBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    private val handler = Handler(Looper.getMainLooper())
    private var currentAnimator: ValueAnimator? = null
    private var isRunning = false

    init {
        setBackgroundColor(Color.BLACK)
        updateShaderAndAlpha()
    }

    fun setPillRatios(widthRatio: Float, heightRatio: Float) {
        pillWidthRatio = widthRatio.coerceIn(1.0f, 6.0f)
        pillHeightRatio = heightRatio.coerceIn(0.5f, 4.0f)
        invalidate()
    }

    fun setConfig(
        color: Int,
        isDot: Boolean,
        radius: Float,
        offsetX: Float,
        offsetY: Float,
        bright: Int,
        onDur: Long,
        offDur: Long,
        fadeDur: Long,
        burnout: Int
    ) {
        setConfig(color, if (isDot) 0 else 1, radius, offsetX, offsetY, bright, onDur, offDur, fadeDur, burnout)
    }

    fun setConfig(
        color: Int,
        shape: Int, // 0 = DOT, 1 = RING, 2 = PILL, 3 = EMPTY_PILL
        radius: Float,
        offsetX: Float,
        offsetY: Float,
        bright: Int,
        onDur: Long,
        offDur: Long,
        fadeDur: Long,
        burnout: Int
    ) {
        paintColor = color
        shapeType = shape
        currentRadius = radius
        customOffsetX = offsetX
        customOffsetY = offsetY
        brightness = bright.coerceIn(0, 100)
        maxAlpha = (255 * (brightness / 100f)).toInt()
        onDuration = onDur.coerceAtLeast(50L)
        offDuration = offDur.coerceAtLeast(1000L)
        fadeDuration = fadeDur.coerceAtLeast(0L)
        burnoutMethod = burnout

        paint.style = if (shapeType == 1 || shapeType == 3) Paint.Style.STROKE else Paint.Style.FILL

        updateShaderAndAlpha()
        restartCycle()
    }

    fun setOffset(x: Float, y: Float) {
        customOffsetX = x
        customOffsetY = y
        invalidate()
    }

    fun setDragging(dragging: Boolean) {
        isDragging = dragging
        if (dragging) {
            currentDrawAlpha = maxAlpha
            updateShaderAndAlpha()
        } else {
            restartCycle()
        }
    }

    private fun restartCycle() {
        if (isDragging) return
        handler.removeCallbacksAndMessages(null)
        currentAnimator?.cancel()
        isRunning = true
        startCycle()
    }

    private fun startCycle() {
        if (!isRunning || !isAttachedToWindow || isDragging) return

        if (fadeDuration > 0) {
            // 1. Fade In
            animateAlpha(0, maxAlpha, fadeDuration) {
                if (isDragging) return@animateAlpha
                // Hold ON
                handler.postDelayed({
                    if (!isRunning || isDragging) return@postDelayed
                    // 2. Fade Out
                    animateAlpha(maxAlpha, 0, fadeDuration) {
                        if (isDragging) return@animateAlpha
                        // Hold OFF (screen black)
                        currentDrawAlpha = 0
                        updateShaderAndAlpha()
                        handler.postDelayed({ startCycle() }, offDuration)
                    }
                }, onDuration)
            }
        } else {
            // Instant ON
            currentDrawAlpha = maxAlpha
            updateShaderAndAlpha()
            handler.postDelayed({
                if (!isRunning || isDragging) return@postDelayed
                // Instant OFF
                currentDrawAlpha = 0
                updateShaderAndAlpha()
                handler.postDelayed({ startCycle() }, offDuration)
            }, onDuration)
        }
    }

    private fun animateAlpha(from: Int, to: Int, duration: Long, onEnd: () -> Unit) {
        currentAnimator?.cancel()
        currentAnimator = ValueAnimator.ofInt(from, to).apply {
            this.duration = duration
            addUpdateListener { anim ->
                if (!isDragging) {
                    currentDrawAlpha = anim.animatedValue as Int
                    updateShaderAndAlpha()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isDragging) {
                        onEnd()
                    }
                }
            })
            start()
        }
    }

    fun shiftPixels() {
        if (isDragging) return
        when (burnoutMethod) {
            1 -> {
                isShifted = !isShifted
            }
            2 -> {
                isCheckerboardInverted = !isCheckerboardInverted
            }
            else -> {}
        }
        updateShaderAndAlpha()
        invalidate()
    }

    private fun updateShaderAndAlpha() {
        val alphaToUse = if (isDragging) maxAlpha else currentDrawAlpha
        val adjustedColor = Color.argb(
            alphaToUse,
            Color.red(paintColor),
            Color.green(paintColor),
            Color.blue(paintColor)
        )

        if (burnoutMethod == 2) {
            val c1 = adjustedColor
            val c2 = Color.TRANSPARENT

            checkerPixels[0] = if (isCheckerboardInverted) c2 else c1
            checkerPixels[1] = if (isCheckerboardInverted) c1 else c2
            checkerPixels[2] = if (isCheckerboardInverted) c1 else c2
            checkerPixels[3] = if (isCheckerboardInverted) c2 else c1

            // Aktualizace pixelů v předalokované bitmapě bez Garbage Collection zátěže
            checkerBitmap.setPixels(checkerPixels, 0, 2, 0, 0, 2, 2)
            paint.shader = checkerShader
        } else {
            paint.shader = null
            paint.color = adjustedColor
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartCycle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentDrawAlpha > 0 || isDragging) {
            val shiftOffset = if (burnoutMethod == 1 && isShifted) 2f else 0f
            val cx = (width / 2f) + customOffsetX + shiftOffset
            val cy = 60f + customOffsetY + shiftOffset

            when (shapeType) {
                0 -> { // DOT
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, currentRadius, paint)
                }
                1 -> { // RING
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, currentRadius, paint)
                }
                2 -> { // SOLID PILL
                    paint.style = Paint.Style.FILL
                    val pillWidth = currentRadius * pillWidthRatio
                    val pillHeight = currentRadius * pillHeightRatio
                    val left = cx - (pillWidth / 2f)
                    val top = cy - (pillHeight / 2f)
                    val right = cx + (pillWidth / 2f)
                    val bottom = cy + (pillHeight / 2f)
                    val cornerRadius = Math.min(pillWidth, pillHeight) / 2f
                    canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)
                }
                3 -> { // EMPTY PILL
                    paint.style = Paint.Style.STROKE
                    val pillWidth = currentRadius * pillWidthRatio
                    val pillHeight = currentRadius * pillHeightRatio
                    val left = cx - (pillWidth / 2f)
                    val top = cy - (pillHeight / 2f)
                    val right = cx + (pillWidth / 2f)
                    val bottom = cy + (pillHeight / 2f)
                    val cornerRadius = Math.min(pillWidth, pillHeight) / 2f
                    canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        currentAnimator?.cancel()
    }
}

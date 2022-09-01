package com.mediasoft.simplegraph

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.text.TextPaint
import android.util.AttributeSet
import android.util.SizeF
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

class GraphView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attributeSet, defStyle) {

    private val axisPaint = Paint(ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B5B5B")
        strokeWidth = 1.dp
    }
    private val pointPaint = Paint(ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E41D94")
        style = Paint.Style.FILL
    }
    private val pointsPathPaint = Paint(ANTI_ALIAS_FLAG).apply {
        color = pointPaint.color
        style = Paint.Style.STROKE
        strokeWidth = 2.dp
        setShadowLayer(8.dp, 0f, 6.dp, Color.GRAY)
    }
    private val netPaint = Paint(ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        strokeWidth = 0.5.dp
    }
    private val toolTipTextPaint = TextPaint(ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        this.textSize = 14.sp
        this.style = Paint.Style.FILL
    }
    private val toolTipBackgroundPaint = Paint(ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E41D94")
        style = Paint.Style.FILL
        setShadowLayer(16.dp, 0f, 6.dp, Color.GRAY)
    }

    private var axisYPointerDrawable = ContextCompat.getDrawable(context, R.drawable.axis_y_pointer)

    // ToolTip
    private var toolTipPadding: Float = 8.dp
    private var toolTipArrowSize: SizeF = SizeF(8.dp, 4.dp)
    private var toolTipTextBounds = Rect()
    private var toolTipPointMargin = 6.dp
    private val toolArrowPath = Path()
    private val toolbarTotalHeight
        get() = toolTipPadding * 2 + toolTipArrowSize.height + toolTipPointMargin +
                if (toolTipTextBounds.isEmpty) toolTipTextPaint.textSize
                else toolTipTextPaint.textSize

    // SelectedPoint
    private var selectedPoint: PointF? = null
    private var selectedPointText: String? = null

    private var pointSize = 6.dp
    private val pointLinePath = Path()

    // Границы для графа
    private var graphPaddingStart = 8.dp
    private var graphPaddingTop = 3.dp
    private var graphPaddingEnd = 0.dp
    private var graphPaddingBottom = 3.dp
    private val graphRect = RectF()

    // границы, в пределах которых рисуются точки
    private var pointsPaddingStart = 16.dp
    private var pointsPaddingTop = 0.dp
    private var pointsPaddingEnd = 16.dp
    private var pointsPaddingBottom = 16.dp
    private val pointsRect = RectF()

    private val points = mutableListOf<Point>()

    // Данные для отображения сетки
    private val netYPoints = mutableListOf<Float>()
    private val netXPoints = mutableListOf<Float>()

    // Данные для отображения точек и линий
    private val mPoints = mutableListOf<PointF>()
    private val conPoints1 = mutableListOf<PointF>()
    private val conPoints2 = mutableListOf<PointF>()

    init {
        setBackgroundColor(Color.GREEN)
        applyAttributes(attributeSet, defStyle)
    }

    private var listener: Listener = Listener.Empty()

    private val touchDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent?): Boolean {
                if (points.isEmpty()) return false
                if (listener is Listener.Empty) return false
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                val x = e?.x ?: return false

                // находим ближайшую точку при нажатии
                val closestPointF = mPoints.minBy { (it.x - x).absoluteValue }
                return handlePointClick(closestPointF)
            }

        }
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = resolveSize(width, widthMeasureSpec)
        val h = resolveSize(height, heightMeasureSpec)

        graphRect.set(
            graphPaddingStart,
            graphPaddingTop,
            w.toFloat() - graphPaddingEnd,
            h - graphPaddingBottom
        )

        pointsRect.set(
            graphRect.left + pointsPaddingStart,
            graphRect.top + toolbarTotalHeight + pointsPaddingTop,
            graphRect.right - pointsPaddingEnd,
            graphRect.bottom - pointsPaddingBottom
        )

        calculatePoints()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) {
            return
        }

        drawNet(canvas)
        drawAxises(canvas)
        drawYAxisPointers(canvas)
        drawPointsLine(canvas)
        drawPoints(canvas)

        val point = selectedPoint
        val toolTipText = selectedPointText
        if (point != null && toolTipText != null) {
            drawTooltip(canvas, point, toolTipText)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return touchDetector.onTouchEvent(event)
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun selectPoint(point: Point) {
        val pointF = mPoints.getOrNull(points.indexOf(point)) ?: return
        handlePointClick(pointF)
    }

    fun setPoints(points: List<Point>) {
        this.points.clear()
        this.points.addAll(points)

        mPoints.clear()
        mPoints.addAll(MutableList(points.size) { PointF() })

        selectedPoint = null
        selectedPointText = null

        conPoints1.clear()
        conPoints2.clear()
        if (points.isNotEmpty()) {
            conPoints1.addAll(MutableList(points.size - 1) { PointF() })
            conPoints2.addAll(MutableList(points.size - 1) { PointF() })
        }

        netYPoints.clear()
        netXPoints.clear()
        graphRect.setEmpty()
        pointsRect.setEmpty()

        requestLayout()
    }

    private fun handlePointClick(closestPointF: PointF): Boolean {
        val point = points[mPoints.indexOf(closestPointF)]

        // если нажали на уже выбранную
        if (closestPointF == selectedPoint) {
            val handleReselection = listener.onPointReselected(point)
            if (handleReselection) {
                selectedPoint = null
                selectedPointText = null
                invalidate()
            }
            return handleReselection
        }

        // получаем текст для тултипа, вызываем перерисовку
        val handleSelection = listener.onPointSelected(point)
        if (handleSelection) {
            selectedPointText = listener.provideToolTipText(point)
            val text = selectedPointText
            if (text != null) {
                selectedPoint = closestPointF
                toolTipTextPaint.getTextBounds(text, 0, text.length, toolTipTextBounds)
                invalidate()
                return true
            }
            toolTipTextBounds.setEmpty()
        } else {
            selectedPointText = null
            selectedPoint = null
        }
        return false
    }

    // вычисление всех, необходимых точек
    private fun calculatePoints() {
        if (points.isEmpty())
            return

        val maxX = points.maxOf { it.x }
        val minX = points.minOf { it.x }
        val xDiff = maxX - minX
        val xUnitRatio = pointsRect.width() / xDiff

        val maxY = points.maxOf { it.y }
        val minY = points.minOf { it.y }
        val yDiff = maxY - minY
        val yUnitRatio = pointsRect.height() / yDiff

        points.forEachIndexed { index, point ->
            mPoints.getOrNull(index)?.apply {
                x = pointsRect.left + (point.x - minX) * xUnitRatio
                y = pointsRect.bottom - (point.y - minY) * yUnitRatio
            }
        }

        calculateBezierConPoints()

        calculateNetPoints(minY, maxY, yDiff, yUnitRatio)
    }

    // вычисление точек для сетки
    private fun calculateNetPoints(
        yMin: Float,
        yMax: Float,
        yDiff: Float,
        yUnitRatio: Float
    ) {
        netXPoints.clear()
        netYPoints.clear()

        val axisXStep = pointsRect.width() / (mPoints.size - 1)
        repeat(mPoints.size) {
            val x = pointsRect.left + (it * axisXStep)
            if (x in graphRect.left..graphRect.right) {
                netXPoints.add(x)
            }
        }

        val maxYLength = yDiff.toLong().toString().length
        var axisYStep = 10f.pow(maxYLength - 1)
        if (floor(yMin / axisYStep).toInt() == 1) {
            axisYStep /= 2
        }
        val yOffset = yMax % axisYStep
        var i = -1
        var y = pointsRect.top + (i++ * axisYStep) * yUnitRatio + (yOffset * yUnitRatio)
        while (y !in graphRect.top..graphRect.bottom) {
            y = pointsRect.top + (i++ * axisYStep) * yUnitRatio + (yOffset * yUnitRatio)
        }
        while (y in graphRect.top..graphRect.bottom) {
            netYPoints.add(y)
            y = pointsRect.top + (i++ * axisYStep) * yUnitRatio + (yOffset * yUnitRatio)
        }
    }

    // вычисление вспомогательных точек для линий Безье
    private fun calculateBezierConPoints() {
        for (i in 1 until mPoints.size) {
            conPoints1.getOrNull(i - 1)?.apply {
                x = (mPoints[i].x + mPoints[i - 1].x) / 2
                y = mPoints[i - 1].y
            }
            conPoints2.getOrNull(i - 1)?.apply {
                x = (mPoints[i].x + mPoints[i - 1].x) / 2
                y = mPoints[i].y
            }
        }
    }

    // линия Безье, соединяющая точки на графике
    private fun drawPointsLine(canvas: Canvas) {
        if (mPoints.isEmpty() || conPoints1.isEmpty() || conPoints2.isEmpty()) {
            return
        }
        pointLinePath.reset()
        pointLinePath.moveTo(mPoints.first().x, mPoints.first().y)
        for (i in 1 until points.size) {
            pointLinePath.cubicTo(
                conPoints1[i - 1].x, conPoints1[i - 1].y,
                conPoints2[i - 1].x, conPoints2[i - 1].y,
                mPoints[i].x, mPoints[i].y
            )
        }
        canvas.drawPath(pointLinePath, pointsPathPaint)
    }

    // точки на графике
    private fun drawPoints(canvas: Canvas) {
        mPoints.forEach { point ->
            drawPoint(canvas, point.x, point.y)
        }
    }

    // точка на графике
    private fun drawPoint(canvas: Canvas, x: Float, y: Float) {
        val pointRadius = pointSize / 2
        canvas.drawOval(
            x - pointRadius,
            y - pointRadius,
            x + pointRadius,
            y + pointRadius,
            pointPaint
        )
    }

    // сетка
    private fun drawNet(canvas: Canvas) {
        netXPoints.forEach { x ->
            canvas.drawLine(x, graphRect.top, x, graphRect.bottom, netPaint)
        }
        netYPoints.forEach { y ->
            canvas.drawLine(graphRect.left, y, graphRect.right, y, netPaint)
        }
    }

    // указатели по оси Y
    private fun drawYAxisPointers(canvas: Canvas) {
        netYPoints.forEach { y ->
            drawYAxisPointer(canvas, y)
        }
    }

    // указатель по оси Y
    private fun drawYAxisPointer(canvas: Canvas, y: Float) {
        val drawable = axisYPointerDrawable ?: return
        drawable.setBounds(
            (graphRect.left - drawable.intrinsicWidth / 2).toInt(),
            (y - drawable.intrinsicHeight / 2).toInt(),
            (graphRect.left + drawable.intrinsicWidth / 2).toInt(),
            (y + drawable.intrinsicHeight / 2).toInt()
        )
        drawable.draw(canvas)
    }

    // оси X и Y
    private fun drawAxises(canvas: Canvas) {
        // axis x
        canvas.drawLine(
            graphRect.left,
            graphRect.bottom,
            width.toFloat(),
            graphRect.bottom,
            axisPaint
        )
        netXPoints.forEach { x ->
            canvas.drawLine(x, graphRect.bottom, x, graphRect.bottom - 6.dp, axisPaint)
        }
        // axis y
        canvas.drawLine(graphRect.left, graphRect.top, graphRect.left, graphRect.bottom, axisPaint)
        netYPoints.forEach { y ->
            canvas.drawLine(graphRect.left, y, graphRect.left + 6.dp, y, axisPaint)
        }
    }

    private fun drawTooltip(canvas: Canvas, point: PointF, text: String) {
        if (toolTipTextBounds.isEmpty) return

        val toolTipVerticalOffset = toolTipPadding + toolTipPointMargin + toolTipArrowSize.height

        var textX = point.x - toolTipTextBounds.width() / 2
        val textY = point.y - toolTipTextBounds.height() / 2 - toolTipVerticalOffset

        var toolTipStart = textX - toolTipPadding
        var toolTipEnd = textX + toolTipTextBounds.width() + toolTipPadding
        val toolTipTop = textY - toolTipTextBounds.height() - toolTipPadding
        val toolTipBottom = textY + toolTipPadding

        // если вылезает за левую границу
        if (toolTipStart < graphRect.left) {
            val offset = graphRect.left - toolTipStart
            textX += offset
            toolTipStart += offset
            toolTipEnd += offset
        }

        // если вылезает за правую границу
        if (toolTipEnd > graphRect.right) {
            val offset = (toolTipEnd - graphRect.right)
            textX -= offset
            toolTipStart -= offset
            toolTipEnd -= offset
        }

        // Path для стрелки тултипа
        toolArrowPath.reset()
        toolArrowPath.moveTo(
            point.x - toolTipArrowSize.width / 2,
            toolTipBottom - 1.dp
        )
        toolArrowPath.lineTo(
            point.x,
            toolTipBottom + toolTipArrowSize.height
        )
        toolArrowPath.lineTo(
            point.x + toolTipArrowSize.width / 2,
            toolTipBottom - 1.dp
        )
        toolArrowPath.close()

        // проямоугольный фон тултипа
        canvas.drawRoundRect(
            toolTipStart,
            toolTipTop,
            toolTipEnd,
            toolTipBottom,
            4.dp,
            4.dp,
            toolTipBackgroundPaint
        )
        // стрелочка вниз тултипа
        canvas.drawPath(toolArrowPath, toolTipBackgroundPaint)
        // текст тултипа
        canvas.drawText(text, textX, textY, toolTipTextPaint)
    }

    private fun applyAttributes(attributeSet: AttributeSet?, defStyle: Int) {
        attributeSet ?: return
        val attrs = context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.GraphView,
            defStyle,
            R.style.GraphView
        )
        attrs.runCatching {
            netPaint.color = getColor(R.styleable.GraphView_netColor, netPaint.color)
            netPaint.strokeWidth = getDimensionPixelOffset(R.styleable.GraphView_netSize, -1)
                .let { if (it == -1) 0.5.dp else it }.toFloat()

            axisPaint.color = getColor(R.styleable.GraphView_axisColor, axisPaint.color)
            axisYPointerDrawable = getDrawable(R.styleable.GraphView_axisYPointerDrawable)

            pointPaint.color = getColor(R.styleable.GraphView_pointerColor, pointPaint.color)
            pointSize = getDimensionPixelOffset(R.styleable.GraphView_pointerSize, -1)
                .let { if (it == -1) 6.dp else it }.toFloat()
            pointsPathPaint.color =
                getColor(R.styleable.GraphView_pointersLineColor, pointsPathPaint.color)
            pointsPathPaint.strokeWidth =
                getDimensionPixelOffset(R.styleable.GraphView_pointerLineWidth, -1)
                    .let { if (it == -1) 2.dp else it }.toFloat()
            getColor(R.styleable.GraphView_pointerLineShadowColor, 0).let { color ->
                if (color == 0) {
                    pointsPathPaint.clearShadowLayer()
                } else {
                    pointsPathPaint.setShadowLayer(16.dp, 0f, 6.dp, color)
                }
            }

            toolTipBackgroundPaint.color =
                getColor(R.styleable.GraphView_toolTipBackgroundColor, toolTipBackgroundPaint.color)
            toolTipPadding = getDimensionPixelOffset(
                R.styleable.GraphView_toolTipTextPadding,
                4.dp.toInt()
            ).toFloat()

            getColor(R.styleable.GraphView_toolTipShadowColor, 0).let { color ->
                if (color == 0) {
                    toolTipBackgroundPaint.clearShadowLayer()
                } else {
                    toolTipBackgroundPaint.setShadowLayer(16.dp, 0f, 6.dp, Color.GRAY)
                }
            }
            toolTipTextPaint.textSize =
                getDimensionPixelSize(R.styleable.GraphView_toolTipTextSize, 0)
                    .let { if (it > 0) it else 12.sp }.toFloat()
            toolTipTextPaint.color =
                getColor(R.styleable.GraphView_toolTipTextColor, toolTipTextPaint.color)
            getString(R.styleable.GraphView_tooltipFontFamily)?.let { name ->
                runCatching { toolTipTextPaint.typeface = Typeface.create(name, Typeface.NORMAL) }
                    .onFailure { it.printStackTrace() }
            }
        }.onFailure { it.printStackTrace() }
        attrs.recycle()
    }

    private val Number.dp: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        )

    private val Number.sp: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this.toFloat(),
            context.resources.displayMetrics
        )

    private val Number.ceil
        get() = ceil(this.toDouble()).toInt()

    interface Point {
        val x: Float
        val y: Float

        companion object {
            fun create(date: Date, price: Int) = object : Point {
                override val x = date.time.toFloat()
                override val y = price.toFloat()
                override fun toString(): String {
                    return "Point[x=$x, y=$y]"
                }
            }

            fun create(date: Int, price: Int) = object : Point {
                override val x = date.toFloat()
                override val y = price.toFloat()
                override fun toString(): String {
                    return "Point[x=$x, y=$y]"
                }
            }
        }

    }

    interface Listener {

        /**
         * Клик по точке
         * return true если клик обарботан, иначе false
         */
        fun onPointSelected(point: Point): Boolean

        /**
         * Клик по уже выбранной точке
         * return true - скрыть, false - ничего не делать
         */
        fun onPointReselected(point: Point): Boolean

        /**
         * return String для показа в тултипе, null если ничего не нужно показывать
         */
        fun provideToolTipText(point: Point): String?

        class Empty : Listener {
            override fun onPointSelected(point: Point): Boolean = false
            override fun onPointReselected(point: Point): Boolean = false
            override fun provideToolTipText(point: Point): String? = null
        }

    }

}
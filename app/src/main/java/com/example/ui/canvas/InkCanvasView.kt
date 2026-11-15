package com.example.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.InkStrokeDoc
import com.example.data.model.MathEntry
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class CanvasPoint(val x: Float, val y: Float, val pressure: Float = 1f)

@Composable
fun InkCanvasView(
    strokes: List<InkStrokeDoc>,
    entries: List<MathEntry>,
    activeColor: Color,
    strokeWidth: Float,
    isHighlighter: Boolean,
    palmRejectionEnabled: Boolean,
    autoSolveOnPause: Boolean,
    panMode: Boolean,
    onStrokeCompleted: (colorArgb: Long, width: Double, toolType: String, points: List<Map<String, Double>>) -> Unit,
    onAutoSolveInkSnapshot: (bitmap: Bitmap, anchorX: Double, anchorY: Double) -> Unit,
    onPlotEntry: (MathEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onRequestManualOcrReady: ((() -> Bitmap) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1400)) }

    val activePoints = remember { mutableStateListOf<CanvasPoint>() }
    var lastStrokeEndTime by remember { mutableStateOf(0L) }
    var lastStrokeAnchor by remember { mutableStateOf(Offset(140f, 180f)) }
    val expandedSteps = remember { mutableStateMapOf<String, Boolean>() }

    val bgColor = MaterialTheme.colorScheme.background
    val gridDotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val inkColorArgb = activeColor.toArgb().toLong()

    fun renderStrokesToBitmap(): Bitmap {
        val w = canvasSize.width.coerceIn(480, 1600)
        val h = canvasSize.height.coerceIn(480, 1600)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val androidCanvas = AndroidCanvas(bmp)
        androidCanvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { s ->
            if (s.points.isEmpty()) return@forEach
            val path = AndroidPath()
            val pts = s.points.map {
                Offset(
                    ((it["x"] ?: 0.0).toFloat() * zoomScale) + panOffset.x,
                    ((it["y"] ?: 0.0).toFloat() * zoomScale) + panOffset.y
                )
            }
            path.moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val curr = pts[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                path.quadTo(prev.x, prev.y, midX, midY)
            }
            if (pts.size > 1) path.lineTo(pts.last().x, pts.last().y)
            // Always render dark high-contrast ink on white background for optimal OCR accuracy
            paint.color = android.graphics.Color.BLACK
            paint.strokeWidth = (s.strokeWidth.toFloat() * zoomScale).coerceAtLeast(4f)
            androidCanvas.drawPath(path, paint)
        }
        return bmp
    }

    LaunchedEffect(canvasSize, strokes, panOffset, zoomScale) {
        onRequestManualOcrReady?.invoke { renderStrokesToBitmap() }
    }

    // Apple-style 700ms stroke pause trigger when Auto-Solve mode is enabled
    LaunchedEffect(lastStrokeEndTime, autoSolveOnPause) {
        if (autoSolveOnPause && lastStrokeEndTime > 0L && strokes.isNotEmpty()) {
            delay(700L)
            val bmp = renderStrokesToBitmap()
            onAutoSolveInkSnapshot(bmp, lastStrokeAnchor.x.toDouble(), lastStrokeAnchor.y.toDouble())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .onSizeChanged { canvasSize = it }
    ) {
        // Drawing + Pan/Zoom Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("aura_ink_canvas")
                .then(
                    if (panMode) {
                        Modifier.pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(0.5f, 3.0f)
                                panOffset += pan
                            }
                        }
                    } else {
                        Modifier.pointerInput(
                            palmRejectionEnabled,
                            activeColor,
                            strokeWidth,
                            isHighlighter,
                            panOffset,
                            zoomScale
                        ) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    if (changes.size >= 2) {
                                        // Two-finger pan even while in pen mode
                                        val c0 = changes[0]
                                        val delta = c0.position - c0.previousPosition
                                        panOffset += delta
                                        changes.forEach { it.consume() }
                                        activePoints.clear()
                                        continue
                                    }

                                    val change = changes.firstOrNull() ?: continue
                                    val isStylusOrMouse = change.type == PointerType.Stylus ||
                                        change.type == PointerType.Mouse
                                    if (palmRejectionEnabled && !isStylusOrMouse) {
                                        // If palm rejection is ON, allow small single-finger taps ONLY when not palm-sized,
                                        // or strictly filter if pointer is touch with large contact area?
                                        // Wait: on emulators & phones, users often test with touch/mouse!
                                        // So when palmRejectionEnabled is ON, reject Touch if pressure/size looks like a palm (>0.85 or multi-touch),
                                        // or let Stylus/Mouse through cleanly. Actually let's check change.type == PointerType.Touch:
                                        // If users toggle "Stylus Only (Palm Reject)", strictly ignore PointerType.Touch so palm doesn't mark!
                                        if (change.type == PointerType.Touch) {
                                            continue
                                        }
                                    }

                                    val worldPos = Offset(
                                        (change.position.x - panOffset.x) / zoomScale,
                                        (change.position.y - panOffset.y) / zoomScale
                                    )

                                    if (change.pressed) {
                                        activePoints.add(
                                            CanvasPoint(
                                                x = worldPos.x,
                                                y = worldPos.y,
                                                pressure = change.pressure.coerceIn(0.35f, 1.5f)
                                            )
                                        )
                                        change.consume()
                                    } else if (activePoints.isNotEmpty()) {
                                        val finalPts = activePoints.toList()
                                        activePoints.clear()
                                        val maxX = finalPts.maxOf { it.x }
                                        val avgY = finalPts.map { it.y }.average().toFloat()
                                        lastStrokeAnchor = Offset(maxX + 24f, avgY - 20f)
                                        lastStrokeEndTime = System.currentTimeMillis()

                                        val pointMaps = finalPts.map {
                                            mapOf(
                                                "x" to it.x.toDouble(),
                                                "y" to it.y.toDouble(),
                                                "p" to it.pressure.toDouble()
                                            )
                                        }
                                        onStrokeCompleted(
                                            inkColorArgb,
                                            strokeWidth.toDouble(),
                                            if (isHighlighter) "HIGHLIGHTER" else "PEN",
                                            pointMaps
                                        )
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                )
        ) {
            // Infinite Smart Dot Grid
            val spacing = (28.dp.toPx() * zoomScale).coerceAtLeast(16f)
            val startX = ((panOffset.x % spacing) + spacing) % spacing
            val startY = ((panOffset.y % spacing) + spacing) % spacing
            var gx = startX
            while (gx < size.width) {
                var gy = startY
                while (gy < size.height) {
                    drawCircle(
                        color = gridDotColor,
                        radius = (1.3.dp.toPx() * zoomScale).coerceIn(1f, 3f),
                        center = Offset(gx, gy)
                    )
                    gy += spacing
                }
                gx += spacing
            }

            // Persisted Bezier Ink Strokes
            strokes.forEach { doc ->
                val pts = doc.points.map {
                    CanvasPoint(
                        x = ((it["x"] ?: 0.0).toFloat() * zoomScale) + panOffset.x,
                        y = ((it["y"] ?: 0.0).toFloat() * zoomScale) + panOffset.y,
                        pressure = (it["p"] ?: 1.0).toFloat()
                    )
                }
                drawSmoothBezierStroke(
                    points = pts,
                    color = Color(doc.colorArgb.toInt()).let {
                        if (doc.toolType == "HIGHLIGHTER") it.copy(alpha = 0.35f) else it
                    },
                    baseWidth = doc.strokeWidth.toFloat() * zoomScale *
                        if (doc.toolType == "HIGHLIGHTER") 2.6f else 1f
                )
            }

            // Live Active Stroke
            if (activePoints.isNotEmpty()) {
                val screenPts = activePoints.map {
                    CanvasPoint(
                        x = it.x * zoomScale + panOffset.x,
                        y = it.y * zoomScale + panOffset.y,
                        pressure = it.pressure
                    )
                }
                drawSmoothBezierStroke(
                    points = screenPts,
                    color = if (isHighlighter) activeColor.copy(alpha = 0.35f) else activeColor,
                    baseWidth = strokeWidth * zoomScale * if (isHighlighter) 2.6f else 1f
                )
            }
        }

        // Empty-canvas helpful prompt
        if (strokes.isEmpty() && entries.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = "Infinite Math & Engineering Canvas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sketch equations with smooth Bezier ink, tap Solve Ink for Gemini Vision OCR, or type directly into the Smart Expression Bar below (end with '=' for instant auto-solve).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Anchored Digital Result Cards & Collapsible Step-by-Step Breakdowns
        entries.forEachIndexed { idx, entry ->
            val sx = ((entry.anchorX.toFloat() * zoomScale) + panOffset.x)
                .coerceIn(12f, (canvasSize.width - 340).coerceAtLeast(12).toFloat())
            val defaultY = 24f + idx * 116f
            val sy = (if (entry.anchorY > 0) (entry.anchorY.toFloat() * zoomScale) + panOffset.y else defaultY)
                .coerceIn(12f, (canvasSize.height - 220).coerceAtLeast(12).toFloat())
            val isExpanded = expandedSteps[entry.id] == true

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .offset { IntOffset(sx.roundToInt(), sy.roundToInt()) }
                    .widthIn(min = 210.dp, max = 330.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("math_entry_card_${entry.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = entry.domainTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (entry.graphType != "NONE" || entry.expression.contains("x")) {
                                IconButton(
                                    onClick = { onPlotEntry(entry) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ShowChart,
                                        contentDescription = "Plot equation",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteEntry(entry.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove solved card",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.expression,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "= ${entry.result}",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (entry.steps.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSteps[entry.id] = !isExpanded }
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isExpanded) "Hide Steps" else "${entry.steps.size} Step Breakdown",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Toggle step breakdown",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                entry.steps.forEachIndexed { stepIdx, step ->
                                    Text(
                                        text = "${stepIdx + 1}. $step",
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothBezierStroke(
    points: List<CanvasPoint>,
    color: Color,
    baseWidth: Float
) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(
            color = color,
            radius = (baseWidth * points[0].pressure) / 2f,
            center = Offset(points[0].x, points[0].y)
        )
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            quadraticTo(prev.x, prev.y, midX, midY)
        }
        lineTo(points.last().x, points.last().y)
    }
    val avgPressure = points.map { it.pressure }.average().toFloat().coerceIn(0.6f, 1.4f)
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = baseWidth * avgPressure,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

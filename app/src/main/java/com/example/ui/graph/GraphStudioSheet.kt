package com.example.ui.graph

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.MathAstParser
import com.example.ui.theme.AmberMath
import com.example.ui.theme.CyanAura
import com.example.ui.theme.EmeraldSolved
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseCurve
import com.example.ui.theme.VioletMatrix
import com.example.ui.viewmodel.GraphCurve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val CurvePalette = listOf(CyanAura, AmberMath, RoseCurve, EmeraldSolved, VioletMatrix)

@Composable
fun GraphStudioSheet(
    curves: List<GraphCurve>,
    active3DSurface: String?,
    variables: Map<String, Double>,
    onAddCurve: (String) -> Unit,
    onToggleDerivative: (String) -> Unit,
    onRemoveCurve: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputExpr by remember { mutableStateOf("") }
    var is3DMode by remember(active3DSurface) { mutableStateOf(active3DSurface != null) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
        modifier = modifier
            .fillMaxSize()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (is3DMode) Icons.Rounded.ViewInAr else Icons.Rounded.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (is3DMode) "3D Surface Studio" else "2D Cartesian Graph",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !is3DMode,
                        onClick = { is3DMode = false },
                        label = { Text("2D") }
                    )
                    FilterChip(
                        selected = is3DMode,
                        onClick = { is3DMode = true },
                        label = { Text("3D") }
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close graph panel"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputExpr,
                    onValueChange = { inputExpr = it },
                    placeholder = {
                        Text(
                            if (is3DMode) "z = sin(x) * cos(y)" else "y = x^2 - 4x + 3",
                            fontFamily = JetBrainsMonoFamily
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("graph_expr_input")
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (inputExpr.isNotBlank()) {
                                onAddCurve(inputExpr.trim())
                                inputExpr = ""
                            }
                        },
                        modifier = Modifier.testTag("graph_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Plot function",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            if (!is3DMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    curves.forEachIndexed { idx, curve ->
                        val color = CurvePalette[idx % CurvePalette.size]
                        Surface(
                            color = color.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(color, RoundedCornerShape(50))
                                )
                                Text(
                                    text = curve.label,
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (curve.derivativeExpr != null) {
                                    FilterChip(
                                        selected = curve.showDerivative,
                                        onClick = { onToggleDerivative(curve.id) },
                                        label = { Text("f'", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveCurve(curve.id) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove curve",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Cartesian2DGraphCanvas(
                    curves = curves,
                    variables = variables,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                val expr3d = active3DSurface ?: "sin(x) * cos(y)"
                Surface3DGraphCanvas(
                    expression = expr3d,
                    variables = variables,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Cartesian2DGraphCanvas(
    curves: List<GraphCurve>,
    variables: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    var centerX by remember { mutableFloatStateOf(0f) }
    var centerY by remember { mutableFloatStateOf(0f) }
    var pixelsPerUnit by remember { mutableFloatStateOf(46f) }
    var tangentX by remember { mutableFloatStateOf(1.0f) }

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val primaryCurve = curves.firstOrNull()
    val primaryAst = remember(primaryCurve?.expression) {
        primaryCurve?.let { runCatching { MathAstParser.parse(it.expression) }.getOrNull() }
    }
    val primaryDerivAst = remember(primaryCurve?.derivativeExpr) {
        primaryCurve?.derivativeExpr?.let { runCatching { MathAstParser.parse(it) }.getOrNull() }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("cartesian_2d_canvas")
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newPpu = (pixelsPerUnit * zoom).coerceIn(14f, 220f)
                            pixelsPerUnit = newPpu
                            centerX -= pan.x / newPpu
                            centerY += pan.y / newPpu
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                fun toScreenX(mx: Float): Float = w / 2f + (mx - centerX) * pixelsPerUnit
                fun toScreenY(my: Float): Float = h / 2f - (my - centerY) * pixelsPerUnit
                fun toMathX(sx: Float): Float = centerX + (sx - w / 2f) / pixelsPerUnit

                val minMathX = toMathX(0f)
                val maxMathX = toMathX(w)
                val minMathY = centerY - (h / 2f) / pixelsPerUnit
                val maxMathY = centerY + (h / 2f) / pixelsPerUnit

                val step = when {
                    pixelsPerUnit > 90f -> 0.5f
                    pixelsPerUnit > 30f -> 1f
                    else -> 2f
                }

                val textPaint = Paint().apply {
                    color = labelArgb
                    textSize = 26f
                    isAntiAlias = true
                }

                var gx = floor(minMathX / step) * step
                while (gx <= maxMathX) {
                    val sx = toScreenX(gx)
                    drawLine(gridColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1f)
                    if (kotlin.math.abs(gx) > 0.01f) {
                        drawContext.canvas.nativeCanvas.drawText(
                            MathAstParser.formatNumber(gx.toDouble()),
                            sx + 4f,
                            toScreenY(0f).coerceIn(28f, h - 12f),
                            textPaint
                        )
                    }
                    gx += step
                }

                var gy = floor(minMathY / step) * step
                while (gy <= maxMathY) {
                    val sy = toScreenY(gy)
                    drawLine(gridColor, Offset(0f, sy), Offset(w, sy), strokeWidth = 1f)
                    if (kotlin.math.abs(gy) > 0.01f) {
                        drawContext.canvas.nativeCanvas.drawText(
                            MathAstParser.formatNumber(gy.toDouble()),
                            toScreenX(0f).coerceIn(10f, w - 50f) + 6f,
                            sy - 4f,
                            textPaint
                        )
                    }
                    gy += step
                }

                // Axes
                val originX = toScreenX(0f)
                val originY = toScreenY(0f)
                drawLine(axisColor, Offset(0f, originY), Offset(w, originY), strokeWidth = 2.5f)
                drawLine(axisColor, Offset(originX, 0f), Offset(originX, h), strokeWidth = 2.5f)

                // Plot every active curve + optional derivative + roots
                curves.forEachIndexed { idx, curve ->
                    val curveColor = CurvePalette[idx % CurvePalette.size]
                    val ast = runCatching { MathAstParser.parse(curve.expression) }.getOrNull() ?: return@forEachIndexed
                    val path = Path()
                    var started = false
                    val samples = 260
                    for (i in 0..samples) {
                        val sx = (i.toFloat() / samples) * w
                        val mx = toMathX(sx)
                        val my = runCatching { MathAstParser.eval(ast, variables + ("x" to mx.toDouble())) }.getOrNull()
                        if (my != null && my.isFinite() && kotlin.math.abs(my) < 1e4) {
                            val sy = toScreenY(my.toFloat())
                            if (!started) {
                                path.moveTo(sx, sy)
                                started = true
                            } else {
                                path.lineTo(sx, sy)
                            }
                        } else {
                            started = false
                        }
                    }
                    drawPath(path, curveColor, style = Stroke(width = 4.5f, cap = StrokeCap.Round))

                    // Derivative curve (dashed)
                    if (curve.showDerivative && curve.derivativeExpr != null) {
                        val dAst = runCatching { MathAstParser.parse(curve.derivativeExpr) }.getOrNull()
                        if (dAst != null) {
                            val dPath = Path()
                            var dStarted = false
                            for (i in 0..samples) {
                                val sx = (i.toFloat() / samples) * w
                                val mx = toMathX(sx)
                                val dy = runCatching { MathAstParser.eval(dAst, variables + ("x" to mx.toDouble())) }.getOrNull()
                                if (dy != null && dy.isFinite() && kotlin.math.abs(dy) < 1e4) {
                                    val sy = toScreenY(dy.toFloat())
                                    if (!dStarted) {
                                        dPath.moveTo(sx, sy)
                                        dStarted = true
                                    } else {
                                        dPath.lineTo(sx, sy)
                                    }
                                } else {
                                    dStarted = false
                                }
                            }
                            drawPath(
                                dPath,
                                curveColor.copy(alpha = 0.65f),
                                style = Stroke(
                                    width = 3f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                                )
                            )
                        }
                    }

                    // Roots markers
                    curve.roots.forEach { rx ->
                        val sx = toScreenX(rx.toFloat())
                        val sy = toScreenY(0f)
                        drawCircle(Color.White, radius = 9f, center = Offset(sx, sy))
                        drawCircle(curveColor, radius = 6f, center = Offset(sx, sy))
                    }
                    // Y-intercept marker
                    curve.yIntercept?.let { yi ->
                        val sx = toScreenX(0f)
                        val sy = toScreenY(yi.toFloat())
                        drawCircle(AmberMath, radius = 7f, center = Offset(sx, sy))
                    }
                }

                // Interactive Tangent Line for primary curve
                if (primaryAst != null && primaryDerivAst != null) {
                    val tx = tangentX.toDouble()
                    val ty = runCatching { MathAstParser.eval(primaryAst, variables + ("x" to tx)) }.getOrNull()
                    val slope = runCatching { MathAstParser.eval(primaryDerivAst, variables + ("x" to tx)) }.getOrNull()
                    if (ty != null && slope != null && ty.isFinite() && slope.isFinite()) {
                        val x1 = tx - 2.5
                        val y1 = ty + slope * (x1 - tx)
                        val x2 = tx + 2.5
                        val y2 = ty + slope * (x2 - tx)
                        drawLine(
                            color = AmberMath,
                            start = Offset(toScreenX(x1.toFloat()), toScreenY(y1.toFloat())),
                            end = Offset(toScreenX(x2.toFloat()), toScreenY(y2.toFloat())),
                            strokeWidth = 3.5f
                        )
                        drawCircle(
                            color = AmberMath,
                            radius = 8f,
                            center = Offset(toScreenX(tx.toFloat()), toScreenY(ty.toFloat()))
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    centerX = 0f
                    centerY = 0f
                    pixelsPerUnit = 46f
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reset graph viewport")
            }
        }

        if (primaryAst != null && primaryDerivAst != null) {
            val ty = runCatching { MathAstParser.eval(primaryAst, variables + ("x" to tangentX.toDouble())) }.getOrNull()
            val slope = runCatching { MathAstParser.eval(primaryDerivAst, variables + ("x" to tangentX.toDouble())) }.getOrNull()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tangent x=${MathAstParser.formatNumber(tangentX.toDouble())} | y=${ty?.let { MathAstParser.formatNumber(it) } ?: "?"} | f'(x)=${slope?.let { MathAstParser.formatNumber(it) } ?: "?"}",
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                Slider(
                    value = tangentX,
                    onValueChange = { tangentX = it },
                    valueRange = -6f..6f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Surface3DGraphCanvas(
    expression: String,
    variables: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    var yaw by remember { mutableFloatStateOf(0.65f) }
    var pitch by remember { mutableFloatStateOf(0.55f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val ast = remember(expression) { runCatching { MathAstParser.parse(expression) }.getOrNull() }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("surface_3d_canvas")
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yaw += dragAmount.x * 0.01f
                        pitch = (pitch + dragAmount.y * 0.01f).coerceIn(-1.3f, 1.3f)
                    }
                }
        ) {
            if (ast == null) return@Canvas
            val n = 22
            val minRange = -PI.toFloat() * 1.2f
            val maxRange = PI.toFloat() * 1.2f
            val step = (maxRange - minRange) / n

            val cosY = cos(yaw)
            val sinY = sin(yaw)
            val cosP = cos(pitch)
            val sinP = sin(pitch)
            val scale = (minOf(size.width, size.height) / 10f) * zoom
            val cx = size.width / 2f
            val cy = size.height / 2f

            data class Projected(val sx: Float, val sy: Float, val depth: Float, val zVal: Float)

            fun project(x: Float, y: Float, z: Float): Projected {
                val rx = x * cosY - y * sinY
                val ry = x * sinY + y * cosY
                val rz = z
                val fy = ry * cosP - rz * sinP
                val fz = ry * sinP + rz * cosP
                val persp = 14f / (14f - fy).coerceAtLeast(2f)
                return Projected(
                    sx = cx + rx * scale * persp,
                    sy = cy - fz * scale * persp,
                    depth = fy,
                    zVal = z
                )
            }

            val grid = Array(n + 1) { i ->
                Array(n + 1) { j ->
                    val x = minRange + i * step
                    val y = minRange + j * step
                    val z = runCatching {
                        MathAstParser.eval(ast, variables + mapOf("x" to x.toDouble(), "y" to y.toDouble()))
                    }.getOrNull()?.takeIf { it.isFinite() }?.toFloat()?.coerceIn(-4f, 4f) ?: 0f
                    project(x, y, z)
                }
            }

            data class Quad(val p1: Projected, val p2: Projected, val p3: Projected, val p4: Projected, val avgDepth: Float, val avgZ: Float)

            val quads = ArrayList<Quad>(n * n)
            for (i in 0 until n) {
                for (j in 0 until n) {
                    val p1 = grid[i][j]
                    val p2 = grid[i + 1][j]
                    val p3 = grid[i + 1][j + 1]
                    val p4 = grid[i][j + 1]
                    quads.add(
                        Quad(
                            p1, p2, p3, p4,
                            avgDepth = (p1.depth + p2.depth + p3.depth + p4.depth) / 4f,
                            avgZ = (p1.zVal + p2.zVal + p3.zVal + p4.zVal) / 4f
                        )
                    )
                }
            }
            quads.sortBy { it.avgDepth }

            quads.forEach { q ->
                val normZ = ((q.avgZ + 2f) / 4f).coerceIn(0f, 1f)
                val fillColor = Color(
                    red = 0.08f + normZ * 0.85f,
                    green = 0.45f + (1f - kotlin.math.abs(normZ - 0.5f) * 2f) * 0.35f,
                    blue = 0.95f - normZ * 0.7f,
                    alpha = 0.72f
                )
                val path = Path().apply {
                    moveTo(q.p1.sx, q.p1.sy)
                    lineTo(q.p2.sx, q.p2.sy)
                    lineTo(q.p3.sx, q.p3.sy)
                    lineTo(q.p4.sx, q.p4.sy)
                    close()
                }
                drawPath(path, fillColor)
                drawPath(path, Color.White.copy(alpha = 0.28f), style = Stroke(width = 1.2f))
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
        ) {
            Text(
                text = "z = $expression (drag to orbit)",
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

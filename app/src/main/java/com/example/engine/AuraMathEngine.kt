package com.example.engine

import kotlin.math.*

data class GraphSpec(
    val is3D: Boolean,
    val expression: String,
    val label: String,
    val derivativeExpr: String? = null,
    val roots: List<Double> = emptyList(),
    val yIntercept: Double? = null
)

data class MathEvalResult(
    val expression: String,
    val result: String,
    val steps: List<String>,
    val domainTag: String,
    val graphSpec: GraphSpec? = null,
    val assignedVarName: String? = null,
    val assignedVarValue: Double? = null
)

object AuraMathEngine {

    fun evaluateSheet(
        expressions: List<String>
    ): Pair<List<MathEvalResult>, Map<String, Double>> {
        val env = linkedMapOf<String, Double>()
        val results = mutableListOf<MathEvalResult>()
        for (raw in expressions) {
            val res = evaluate(raw, env)
            if (res.assignedVarName != null && res.assignedVarValue != null) {
                env[res.assignedVarName] = res.assignedVarValue
            }
            results.add(res)
        }
        return results to env
    }

    fun evaluate(rawInput: String, variables: Map<String, Double> = emptyMap()): MathEvalResult {
        val cleaned = rawInput.trim()
            .removeSuffix("=")
            .removePrefix("Graph:")
            .removePrefix("graph:")
            .trim()
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("Empty mathematical expression")
        }

        // 1. Check 3D Surface: z = f(x, y)
        val surfaceMatch = Regex("^z\\s*=\\s*(.+)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (surfaceMatch != null) {
            val rhs = surfaceMatch.groupValues[1].trim()
            val ast = MathAstParser.parse(rhs)
            val testEnv = variables + mapOf("x" to 0.5, "y" to 0.5)
            val sampleVal = MathAstParser.eval(ast, testEnv)
            return MathEvalResult(
                expression = "z = $rhs",
                result = "3D Surface Ready (z(0.5, 0.5) = ${MathAstParser.formatNumber(sampleVal)})",
                steps = listOf(
                    "Parsed bivariate scalar field z(x, y) = $rhs",
                    "Ready for interactive 3D wireframe & elevation mesh rendering"
                ),
                domainTag = "Graph 3D",
                graphSpec = GraphSpec(is3D = true, expression = rhs, label = "z = $rhs")
            )
        }

        // 2. Check 2D Function / Graph: y = f(x) or f(x) = ... or explicit graph(...)
        val fn2dMatch = Regex("^(?:y|f\\(x\\)|g\\(x\\))\\s*=\\s*(.+)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        val explicitGraphMatch = Regex("^(?:graph|plot)\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (fn2dMatch != null || explicitGraphMatch != null) {
            val rhs = (fn2dMatch?.groupValues?.get(1) ?: explicitGraphMatch!!.groupValues[1]).trim()
            val ast = MathAstParser.parse(rhs)
            val derivAst = MathAstParser.differentiate(ast, "x")
            val derivStr = MathAstParser.format(derivAst)
            val roots = findRoots(ast, variables)
            val yInt = runCatching { MathAstParser.eval(ast, variables + ("x" to 0.0)) }.getOrNull()
                ?.takeIf { it.isFinite() }

            val steps = mutableListOf(
                "Parsed f(x) = $rhs",
                "Symbolic derivative f'(x) = $derivStr"
            )
            if (yInt != null) steps += "y-intercept: (0, ${MathAstParser.formatNumber(yInt)})"
            if (roots.isNotEmpty()) {
                steps += "Real roots in [-10, 10]: ${roots.joinToString(", ") { MathAstParser.formatNumber(it) }}"
            }

            val summary = buildString {
                append("Plotted 2D Curve")
                if (roots.isNotEmpty()) {
                    append(" • Roots: ${roots.joinToString(", ") { MathAstParser.formatNumber(it) }}")
                }
            }
            return MathEvalResult(
                expression = cleaned,
                result = summary,
                steps = steps,
                domainTag = "Graph 2D",
                graphSpec = GraphSpec(
                    is3D = false,
                    expression = rhs,
                    label = cleaned,
                    derivativeExpr = derivStr,
                    roots = roots,
                    yIntercept = yInt
                )
            )
        }

        // 3. Check Calculus: Higher-order and first derivatives
        val d2Match = Regex("^(?:d\\^2/dx\\^2|d²/dx²|diff2)\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (d2Match != null) {
            val inner = d2Match.groupValues[1].trim()
            val ast = MathAstParser.parse(inner)
            val d1 = MathAstParser.differentiate(ast, "x")
            val d2 = MathAstParser.differentiate(d1, "x")
            val d1Str = MathAstParser.format(d1)
            val d2Str = MathAstParser.format(d2)
            return MathEvalResult(
                expression = cleaned,
                result = d2Str,
                steps = listOf(
                    "First derivative d/dx($inner) = $d1Str",
                    "Differentiate again: d/dx($d1Str) = $d2Str"
                ),
                domainTag = "Calculus",
                graphSpec = if (MathAstParser.containsVar(d2, "x")) {
                    GraphSpec(is3D = false, expression = d2Str, label = "f''(x) = $d2Str")
                } else null
            )
        }

        val d1Match = Regex("^(?:d/dx|dy/dx|diff|derivative)\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (d1Match != null) {
            val inner = d1Match.groupValues[1].trim()
            val ast = MathAstParser.parse(inner)
            val d1 = MathAstParser.differentiate(ast, "x")
            val d1Str = MathAstParser.format(d1)
            return MathEvalResult(
                expression = cleaned,
                result = d1Str,
                steps = listOf(
                    "Apply symbolic differentiation rules (power, product, quotient, chain) with respect to x",
                    "Simplified result: $d1Str"
                ),
                domainTag = "Calculus",
                graphSpec = GraphSpec(
                    is3D = false,
                    expression = inner,
                    label = "f(x) = $inner",
                    derivativeExpr = d1Str
                )
            )
        }

        // 4. Integrals: integrate(expr, a, b) or integrate(expr) or ∫(...)
        val intDefMatch = Regex("^(?:integrate|int|∫)\\(([^,]+),\\s*([^,]+),\\s*([^)]+)\\)$", RegexOption.IGNORE_CASE)
            .matchEntire(cleaned)
        if (intDefMatch != null) {
            val exprStr = intDefMatch.groupValues[1].trim()
            val a = MathAstParser.eval(MathAstParser.parse(intDefMatch.groupValues[2].trim()), variables)
            val b = MathAstParser.eval(MathAstParser.parse(intDefMatch.groupValues[3].trim()), variables)
            val ast = MathAstParser.parse(exprStr)
            val area = simpsonIntegrate(ast, variables, a, b)
            val antideriv = MathAstParser.integrateSymbolic(ast, "x")
            val steps = mutableListOf(
                "Definite integral of $exprStr over [${MathAstParser.formatNumber(a)}, ${MathAstParser.formatNumber(b)}]"
            )
            if (antideriv != null) {
                steps += "Antiderivative F(x) = ${MathAstParser.format(antideriv)}"
            }
            steps += "Composite Simpson's rule (n = 512) evaluates to ${MathAstParser.formatNumber(area)}"
            return MathEvalResult(
                expression = cleaned,
                result = MathAstParser.formatNumber(area),
                steps = steps,
                domainTag = "Calculus",
                graphSpec = GraphSpec(is3D = false, expression = exprStr, label = "∫ $exprStr dx")
            )
        }

        val intIndefMatch = Regex("^(?:integrate|int|∫)\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(cleaned)
        if (intIndefMatch != null) {
            val exprStr = intIndefMatch.groupValues[1].trim()
            val ast = MathAstParser.parse(exprStr)
            val anti = MathAstParser.integrateSymbolic(ast, "x")
            return if (anti != null) {
                val formatted = "${MathAstParser.format(anti)} + C"
                MathEvalResult(
                    expression = cleaned,
                    result = formatted,
                    steps = listOf(
                        "Integrate term-by-term with respect to x",
                        "Result: $formatted"
                    ),
                    domainTag = "Calculus"
                )
            } else {
                val sampleArea = simpsonIntegrate(ast, variables, 0.0, 1.0)
                MathEvalResult(
                    expression = cleaned,
                    result = "Definite [0,1] ≈ ${MathAstParser.formatNumber(sampleArea)}",
                    steps = listOf(
                        "Expression has non-elementary symbolic antiderivative",
                        "Use integrate($exprStr, a, b) for arbitrary definite bounds. Over [0, 1] ≈ ${MathAstParser.formatNumber(sampleArea)}"
                    ),
                    domainTag = "Calculus"
                )
            }
        }

        // 5. Limits: limit(expr, x -> a) or lim(x->a, expr)
        val limMatch1 = Regex("^(?:limit|lim)\\(([^,]+),\\s*x\\s*->\\s*([^)]+)\\)$", RegexOption.IGNORE_CASE)
            .matchEntire(cleaned)
        val limMatch2 = Regex("^(?:limit|lim)\\(x\\s*->\\s*([^,]+),\\s*(.+)\\)$", RegexOption.IGNORE_CASE)
            .matchEntire(cleaned)
        if (limMatch1 != null || limMatch2 != null) {
            val exprStr = (limMatch1?.groupValues?.get(1) ?: limMatch2!!.groupValues[2]).trim()
            val targetStr = (limMatch1?.groupValues?.get(2) ?: limMatch2!!.groupValues[1]).trim()
            val ast = MathAstParser.parse(exprStr)
            if (targetStr.equals("inf", true) || targetStr == "∞") {
                val v1 = MathAstParser.eval(ast, variables + ("x" to 1e5))
                val v2 = MathAstParser.eval(ast, variables + ("x" to 1e7))
                val res = if (abs(v2) > 1e12) "∞" else MathAstParser.formatNumber(v2)
                return MathEvalResult(
                    expression = cleaned,
                    result = res,
                    steps = listOf(
                        "Evaluated asymptotic behavior as x → ∞",
                        "f(10⁵) = ${MathAstParser.formatNumber(v1)}, f(10⁷) = ${MathAstParser.formatNumber(v2)}"
                    ),
                    domainTag = "Calculus"
                )
            } else {
                val a = MathAstParser.eval(MathAstParser.parse(targetStr), variables)
                val left = MathAstParser.eval(ast, variables + ("x" to (a - 1e-7)))
                val right = MathAstParser.eval(ast, variables + ("x" to (a + 1e-7)))
                val exists = left.isFinite() && right.isFinite() && abs(left - right) < 1e-3
                val ans = if (exists) MathAstParser.formatNumber((left + right) / 2.0) else "DNE (L=${MathAstParser.formatNumber(left)}, R=${MathAstParser.formatNumber(right)})"
                return MathEvalResult(
                    expression = cleaned,
                    result = ans,
                    steps = listOf(
                        "Left-hand limit (x → ${MathAstParser.formatNumber(a)}⁻) = ${MathAstParser.formatNumber(left)}",
                        "Right-hand limit (x → ${MathAstParser.formatNumber(a)}⁺) = ${MathAstParser.formatNumber(right)}"
                    ),
                    domainTag = "Calculus"
                )
            }
        }

        // 6. Linear Algebra (Matrices up to 4x4)
        if (isMatrixOperation(cleaned)) {
            return evaluateMatrixExpression(cleaned)
        }

        // 7. Discrete Math: Boolean Algebra & Truth Tables
        if (DiscreteMathEngine.isBooleanExpression(cleaned)) {
            val (res, steps) = DiscreteMathEngine.evaluateBoolean(cleaned)
            return MathEvalResult(
                expression = cleaned,
                result = res,
                steps = steps,
                domainTag = "Boolean Algebra"
            )
        }

        // 8. Discrete Math: Base Conversions & Mixed Radix Arithmetic
        if (DiscreteMathEngine.isBaseExpression(cleaned)) {
            val (res, steps) = DiscreteMathEngine.evaluateBaseExpression(cleaned)
            return MathEvalResult(
                expression = cleaned,
                result = res,
                steps = steps,
                domainTag = "Discrete Math"
            )
        }

        // 9. Complex Numbers (Rectangular a + bi and Polar r∠θ)
        if (isComplexExpression(cleaned)) {
            return evaluateComplexExpression(cleaned)
        }

        // 10. Variable Assignment (e.g. a = 10 or Hypotenuse = sqrt(a^2 + b^2))
        val assignMatch = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)$").matchEntire(cleaned)
        if (assignMatch != null) {
            val varName = assignMatch.groupValues[1]
            val rhs = assignMatch.groupValues[2].trim()
            val ast = MathAstParser.parse(rhs)
            val value = MathAstParser.eval(ast, variables)
            val formatted = MathAstParser.formatNumber(value)
            val usedVars = variables.filterKeys { Regex("\\b$it\\b").containsMatchIn(rhs) }
            val steps = mutableListOf<String>()
            if (usedVars.isNotEmpty()) {
                steps += "Substituted variables: ${usedVars.entries.joinToString(", ") { "${it.key} = ${MathAstParser.formatNumber(it.value)}" }}"
            }
            steps += "Assigned $varName ← $formatted"
            return MathEvalResult(
                expression = "$varName = $rhs",
                result = formatted,
                steps = steps,
                domainTag = "Variables",
                assignedVarName = varName,
                assignedVarValue = value
            )
        }

        // 11. Standard / Scientific / Variable-cascaded Expression
        val ast = MathAstParser.parse(cleaned)
        if (MathAstParser.containsVar(ast, "x") && !variables.containsKey("x")) {
            val deriv = MathAstParser.format(MathAstParser.differentiate(ast, "x"))
            val roots = findRoots(ast, variables)
            return MathEvalResult(
                expression = cleaned,
                result = "Plotted f(x) • d/dx = $deriv",
                steps = listOf(
                    "Detected free variable x — switched to 2D function mode",
                    "Derivative f'(x) = $deriv"
                ),
                domainTag = "Graph 2D",
                graphSpec = GraphSpec(
                    is3D = false,
                    expression = cleaned,
                    label = "f(x) = $cleaned",
                    derivativeExpr = deriv,
                    roots = roots
                )
            )
        }

        val value = MathAstParser.eval(ast, variables)
        val formatted = MathAstParser.formatNumber(value)
        val usedVars = variables.filterKeys { Regex("\\b$it\\b").containsMatchIn(cleaned) }
        val steps = mutableListOf<String>()
        if (usedVars.isNotEmpty()) {
            steps += "Cascaded variables: ${usedVars.entries.joinToString(", ") { "${it.key} = ${MathAstParser.formatNumber(it.value)}" }}"
        }
        steps += "Evaluated $cleaned = $formatted"

        return MathEvalResult(
            expression = cleaned,
            result = formatted,
            steps = steps,
            domainTag = "Algebra"
        )
    }

    private fun simpsonIntegrate(
        ast: MathExpr,
        env: Map<String, Double>,
        a: Double,
        b: Double,
        n: Int = 512
    ): Double {
        if (a == b) return 0.0
        val steps = if (n % 2 == 0) n else n + 1
        val h = (b - a) / steps
        var sum = safeEval(ast, env + ("x" to a)) + safeEval(ast, env + ("x" to b))
        for (i in 1 until steps) {
            val x = a + i * h
            val coeff = if (i % 2 == 0) 2.0 else 4.0
            sum += coeff * safeEval(ast, env + ("x" to x))
        }
        return sum * h / 3.0
    }

    private fun safeEval(ast: MathExpr, env: Map<String, Double>): Double {
        val v = MathAstParser.eval(ast, env)
        return if (v.isFinite()) v else 0.0
    }

    fun findRoots(ast: MathExpr, env: Map<String, Double>, minX: Double = -10.0, maxX: Double = 10.0): List<Double> {
        val roots = mutableListOf<Double>()
        val samples = 200
        val dx = (maxX - minX) / samples
        var prevX = minX
        var prevY = runCatching { MathAstParser.eval(ast, env + ("x" to prevX)) }.getOrNull()
        for (i in 1..samples) {
            val currX = minX + i * dx
            val currY = runCatching { MathAstParser.eval(ast, env + ("x" to currX)) }.getOrNull()
            val py = prevY
            val cy = currY
            if (py != null && cy != null && py.isFinite() && cy.isFinite()) {
                if (abs(cy) < 1e-6) {
                    if (roots.none { abs(it - currX) < 1e-2 }) roots += currX
                } else if (py * cy < 0) {
                    var lo = prevX
                    var hi = currX
                    var fLo: Double = py
                    repeat(30) {
                        val mid = (lo + hi) / 2.0
                        val fMid = MathAstParser.eval(ast, env + ("x" to mid))
                        if (fLo * fMid <= 0) {
                            hi = mid
                        } else {
                            lo = mid
                            fLo = fMid
                        }
                    }
                    val root = (lo + hi) / 2.0
                    if (roots.none { abs(it - root) < 1e-2 }) roots += root
                }
            }
            prevX = currX
            prevY = currY
        }
        return roots.take(6)
    }

    private fun isMatrixOperation(expr: String): Boolean {
        val lower = expr.lowercase()
        return lower.startsWith("det(") || lower.startsWith("inv(") ||
            lower.startsWith("eigen(") || lower.startsWith("transpose(") ||
            (expr.startsWith("|") && expr.endsWith("|") && expr.contains("[")) ||
            expr.contains("[")
    }

    private fun evaluateMatrixExpression(expr: String): MathEvalResult {
        val trimmed = expr.trim()
        val detWrap = Regex("^det\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        val absDetWrap = if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else null
        if (detWrap != null || absDetWrap != null) {
            val matStr = detWrap?.groupValues?.get(1) ?: absDetWrap!!
            val m = LinearAlgebraEngine.parseMatrix(matStr)
            val (det, steps) = LinearAlgebraEngine.determinant(m)
            return MathEvalResult(
                expression = trimmed,
                result = MathAstParser.formatNumber(det),
                steps = steps,
                domainTag = "Linear Algebra"
            )
        }

        val invWrap = Regex("^inv\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (invWrap != null) {
            val m = LinearAlgebraEngine.parseMatrix(invWrap.groupValues[1])
            val (inv, steps) = LinearAlgebraEngine.inverse(m)
            return MathEvalResult(
                expression = trimmed,
                result = LinearAlgebraEngine.formatMatrix(inv),
                steps = steps,
                domainTag = "Linear Algebra"
            )
        }

        val eigenWrap = Regex("^eigen\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (eigenWrap != null) {
            val m = LinearAlgebraEngine.parseMatrix(eigenWrap.groupValues[1])
            val (res, steps) = LinearAlgebraEngine.eigenvalues(m)
            return MathEvalResult(
                expression = trimmed,
                result = res,
                steps = steps,
                domainTag = "Linear Algebra"
            )
        }

        val transWrap = Regex("^transpose\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (transWrap != null) {
            val m = LinearAlgebraEngine.parseMatrix(transWrap.groupValues[1])
            val t = LinearAlgebraEngine.transpose(m)
            return MathEvalResult(
                expression = trimmed,
                result = LinearAlgebraEngine.formatMatrix(t),
                steps = listOf("Swapped rows and columns: (${m.size}×${m[0].size}) → (${t.size}×${t[0].size})"),
                domainTag = "Linear Algebra"
            )
        }

        // Binary matrix operation: [A] * [B], [A] + [B], [A] - [B]
        val binMatch = Regex("^(\\[.+?\\])\\s*([+*\\-])\\s*(\\[.+?\\])$").matchEntire(trimmed)
        if (binMatch != null) {
            val a = LinearAlgebraEngine.parseMatrix(binMatch.groupValues[1])
            val op = binMatch.groupValues[2]
            val b = LinearAlgebraEngine.parseMatrix(binMatch.groupValues[3])
            val res = when (op) {
                "*" -> LinearAlgebraEngine.multiply(a, b)
                "+" -> LinearAlgebraEngine.add(a, b, 1.0)
                "-" -> LinearAlgebraEngine.add(a, b, -1.0)
                else -> error("Unsupported op")
            }
            return MathEvalResult(
                expression = trimmed,
                result = LinearAlgebraEngine.formatMatrix(res),
                steps = listOf("Applied matrix $op operation on ${a.size}×${a[0].size} and ${b.size}×${b[0].size} matrices"),
                domainTag = "Linear Algebra"
            )
        }

        val single = LinearAlgebraEngine.parseMatrix(trimmed)
        val (det, _) = if (single.size == single[0].size) LinearAlgebraEngine.determinant(single) else 0.0 to emptyList()
        val note = if (single.size == single[0].size) "det = ${MathAstParser.formatNumber(det)}" else "${single.size}×${single[0].size} Matrix"
        return MathEvalResult(
            expression = trimmed,
            result = "${LinearAlgebraEngine.formatMatrix(single)} ($note)",
            steps = listOf("Parsed ${single.size}×${single[0].size} matrix"),
            domainTag = "Linear Algebra"
        )
    }

    private fun isComplexExpression(expr: String): Boolean {
        val lower = expr.lowercase()
        return lower.startsWith("polar(") || lower.startsWith("rect(") ||
            lower.startsWith("conj(") || expr.contains("∠") ||
            Regex("(^|[+\\-*/(\\s])\\d*(?:\\.\\d+)?i\\b").containsMatchIn(lower)
    }

    private fun evaluateComplexExpression(expr: String): MathEvalResult {
        val trimmed = expr.trim()
        val polarWrap = Regex("^polar\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (polarWrap != null) {
            val z = parseComplex(polarWrap.groupValues[1])
            return MathEvalResult(
                expression = trimmed,
                result = z.formatPolar(),
                steps = listOf(
                    "r = √(${fmt(z.re)}² + ${fmt(z.im)}²) = ${fmt(z.magnitude())}",
                    "θ = atan2(${fmt(z.im)}, ${fmt(z.re)}) = ${fmt(z.angleDeg())}°"
                ),
                domainTag = "Complex"
            )
        }
        val rectWrap = Regex("^rect\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (rectWrap != null) {
            val z = parseComplex(rectWrap.groupValues[1])
            return MathEvalResult(
                expression = trimmed,
                result = z.formatRect(),
                steps = listOf(
                    "a = r·cos(θ) = ${fmt(z.re)}",
                    "b = r·sin(θ) = ${fmt(z.im)}"
                ),
                domainTag = "Complex"
            )
        }
        val conjWrap = Regex("^conj\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (conjWrap != null) {
            val z = parseComplex(conjWrap.groupValues[1])
            val c = z.conjugate()
            return MathEvalResult(
                expression = trimmed,
                result = c.formatRect(),
                steps = listOf("Negated imaginary component: ${z.formatRect()} → ${c.formatRect()}"),
                domainTag = "Complex"
            )
        }

        val z = evalComplexBin(trimmed)
        return MathEvalResult(
            expression = trimmed,
            result = "${z.formatRect()}  (${z.formatPolar()})",
            steps = listOf(
                "Rectangular: ${z.formatRect()}",
                "Polar: ${z.formatPolar()}"
            ),
            domainTag = "Complex"
        )
    }

    private fun evalComplexBin(raw: String): ComplexNum {
        val bin = Regex("^\\(([^)]+)\\)\\s*([+*\\-/])\\s*\\(([^)]+)\\)$").matchEntire(raw.trim())
        if (bin != null) {
            val a = parseComplex(bin.groupValues[1])
            val b = parseComplex(bin.groupValues[3])
            return when (bin.groupValues[2]) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> a / b
                else -> a
            }
        }
        return parseComplex(raw)
    }

    private fun parseComplex(raw: String): ComplexNum {
        val s = raw.trim().replace(" ", "").replace("°", "")
        if (s.contains("∠")) {
            val parts = s.split("∠")
            val r = parts[0].toDouble()
            val deg = parts[1].toDouble()
            val rad = Math.toRadians(deg)
            return ComplexNum(r * cos(rad), r * sin(rad))
        }
        // Match a+bi or a-bi or bi or a
        val full = Regex("^([+-]?(?:\\d+(?:\\.\\d+)?))([+-](?:\\d*(?:\\.\\d+)?))i$").matchEntire(s)
        if (full != null) {
            val re = full.groupValues[1].toDouble()
            val imToken = full.groupValues[2]
            val im = when (imToken) {
                "+" -> 1.0
                "-" -> -1.0
                else -> imToken.toDouble()
            }
            return ComplexNum(re, im)
        }
        val pureIm = Regex("^([+-]?(?:\\d*(?:\\.\\d+)?))i$").matchEntire(s)
        if (pureIm != null) {
            val imToken = pureIm.groupValues[1]
            val im = when (imToken) {
                "", "+" -> 1.0
                "-" -> -1.0
                else -> imToken.toDouble()
            }
            return ComplexNum(0.0, im)
        }
        return ComplexNum(s.toDouble(), 0.0)
    }

    private fun fmt(v: Double) = MathAstParser.formatNumber(v)
}

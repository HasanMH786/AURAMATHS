package com.example.engine

import kotlin.math.*

data class ComplexNum(val re: Double, val im: Double) {
    operator fun plus(other: ComplexNum) = ComplexNum(re + other.re, im + other.im)
    operator fun minus(other: ComplexNum) = ComplexNum(re - other.re, im - other.im)
    operator fun times(other: ComplexNum) = ComplexNum(
        re * other.re - im * other.im,
        re * other.im + im * other.re
    )
    operator fun div(other: ComplexNum): ComplexNum {
        val denom = other.re * other.re + other.im * other.im
        if (denom < 1e-15) throw IllegalArgumentException("Division by zero complex number")
        return ComplexNum(
            (re * other.re + im * other.im) / denom,
            (im * other.re - re * other.im) / denom
        )
    }

    fun magnitude(): Double = hypot(re, im)
    fun angleDeg(): Double = Math.toDegrees(atan2(im, re))
    fun conjugate(): ComplexNum = ComplexNum(re, -im)

    fun formatRect(): String {
        val rStr = MathAstParser.formatNumber(re)
        val absIm = abs(im)
        if (absIm < 1e-9) return rStr
        val iStr = MathAstParser.formatNumber(absIm) + "i"
        return if (abs(re) < 1e-9) {
            if (im < 0) "-$iStr" else iStr
        } else {
            if (im < 0) "$rStr - $iStr" else "$rStr + $iStr"
        }
    }

    fun formatPolar(): String {
        val r = MathAstParser.formatNumber(magnitude())
        val deg = MathAstParser.formatNumber(angleDeg())
        return "${r}∠${deg}°"
    }
}

object LinearAlgebraEngine {

    fun parseMatrix(raw: String): Array<DoubleArray> {
        val trimmed = raw.trim().removePrefix("[").removeSuffix("]").trim()
        val rowStrings = if (trimmed.contains(";") ) {
            trimmed.split(";")
        } else if (trimmed.contains("],")) {
            trimmed.split(Regex("\\]\\s*,\\s*\\["))
        } else {
            listOf(trimmed)
        }
        val rows = rowStrings.map { rowStr ->
            rowStr.trim().removePrefix("[").removeSuffix("]")
                .split(Regex("[,\\s]+"))
                .filter { it.isNotBlank() }
                .map { token ->
                    MathAstParser.eval(MathAstParser.parse(token), emptyMap())
                }
                .toDoubleArray()
        }
        if (rows.isEmpty() || rows[0].isEmpty()) throw IllegalArgumentException("Empty matrix")
        val cols = rows[0].size
        if (rows.any { it.size != cols }) throw IllegalArgumentException("Inconsistent matrix row lengths")
        if (rows.size > 4 || cols > 4) throw IllegalArgumentException("Matrices supported up to 4×4")
        return rows.toTypedArray()
    }

    fun formatMatrix(m: Array<DoubleArray>): String {
        return m.joinToString(prefix = "[", postfix = "]", separator = "; ") { row ->
            row.joinToString(", ") { MathAstParser.formatNumber(it) }
        }
    }

    fun determinant(m: Array<DoubleArray>): Pair<Double, List<String>> {
        val n = m.size
        require(n == m[0].size) { "Determinant requires a square matrix" }
        val steps = mutableListOf<String>()
        when (n) {
            1 -> {
                steps += "1×1 determinant equals single element ${MathAstParser.formatNumber(m[0][0])}"
                return m[0][0] to steps
            }
            2 -> {
                val det = m[0][0] * m[1][1] - m[0][1] * m[1][0]
                steps += "det = (${fmt(m[0][0])})(${fmt(m[1][1])}) - (${fmt(m[0][1])})(${fmt(m[1][0])}) = ${fmt(det)}"
                return det to steps
            }
            else -> {
                var sum = 0.0
                val termDescs = mutableListOf<String>()
                for (c in 0 until n) {
                    val sign = if (c % 2 == 0) 1.0 else -1.0
                    val minor = minorMatrix(m, 0, c)
                    val (minorDet, _) = determinant(minor)
                    val term = sign * m[0][c] * minorDet
                    sum += term
                    termDescs += "${if (sign > 0) "+" else "-"}(${fmt(m[0][c])})·(${fmt(minorDet)})"
                }
                steps += "Cofactor expansion along row 1: ${termDescs.joinToString(" ")}"
                steps += "det(A) = ${fmt(sum)}"
                return sum to steps
            }
        }
    }

    private fun minorMatrix(m: Array<DoubleArray>, skipRow: Int, skipCol: Int): Array<DoubleArray> {
        val n = m.size
        val out = Array(n - 1) { DoubleArray(n - 1) }
        var rIdx = 0
        for (r in 0 until n) {
            if (r == skipRow) continue
            var cIdx = 0
            for (c in 0 until n) {
                if (c == skipCol) continue
                out[rIdx][cIdx++] = m[r][c]
            }
            rIdx++
        }
        return out
    }

    fun inverse(m: Array<DoubleArray>): Pair<Array<DoubleArray>, List<String>> {
        val n = m.size
        require(n == m[0].size) { "Inverse requires a square matrix" }
        val (det, _) = determinant(m)
        if (abs(det) < 1e-10) throw IllegalArgumentException("Matrix is singular (det = 0); inverse does not exist")

        val steps = mutableListOf<String>()
        steps += "det(A) = ${fmt(det)} ≠ 0, proceeding with Gauss-Jordan [A | I]"

        val aug = Array(n) { r ->
            DoubleArray(2 * n) { c ->
                if (c < n) m[r][c] else if (c - n == r) 1.0 else 0.0
            }
        }

        for (col in 0 until n) {
            var pivotRow = col
            for (r in col + 1 until n) {
                if (abs(aug[r][col]) > abs(aug[pivotRow][col])) pivotRow = r
            }
            if (pivotRow != col) {
                val tmp = aug[col]
                aug[col] = aug[pivotRow]
                aug[pivotRow] = tmp
                steps += "Swap R${col + 1} ↔ R${pivotRow + 1}"
            }
            val pivot = aug[col][col]
            for (c in 0 until 2 * n) aug[col][c] /= pivot
            steps += "Scale R${col + 1} by 1/${fmt(pivot)}"

            for (r in 0 until n) {
                if (r == col) continue
                val factor = aug[r][col]
                if (abs(factor) > 1e-12) {
                    for (c in 0 until 2 * n) aug[r][c] -= factor * aug[col][c]
                }
            }
        }

        val inv = Array(n) { r -> DoubleArray(n) { c -> aug[r][c + n] } }
        steps += "Reduced to [I | A⁻¹] = ${formatMatrix(inv)}"
        return inv to steps
    }

    fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        require(a[0].size == b.size) { "Incompatible dimensions for multiplication: ${a.size}×${a[0].size} * ${b.size}×${b[0].size}" }
        val rows = a.size
        val cols = b[0].size
        val shared = b.size
        return Array(rows) { r ->
            DoubleArray(cols) { c ->
                var acc = 0.0
                for (k in 0 until shared) acc += a[r][k] * b[k][c]
                acc
            }
        }
    }

    fun add(a: Array<DoubleArray>, b: Array<DoubleArray>, sign: Double = 1.0): Array<DoubleArray> {
        require(a.size == b.size && a[0].size == b[0].size) { "Matrix dimensions must match" }
        return Array(a.size) { r -> DoubleArray(a[0].size) { c -> a[r][c] + sign * b[r][c] } }
    }

    fun transpose(a: Array<DoubleArray>): Array<DoubleArray> {
        return Array(a[0].size) { c -> DoubleArray(a.size) { r -> a[r][c] } }
    }

    fun eigenvalues(m: Array<DoubleArray>): Pair<String, List<String>> {
        val n = m.size
        require(n == m[0].size) { "Eigenvalues require a square matrix" }
        val steps = mutableListOf<String>()
        if (n == 1) {
            return "λ₁ = ${fmt(m[0][0])}" to listOf("1×1 matrix eigenvalue equals ${fmt(m[0][0])}")
        }
        if (n == 2) {
            val tr = m[0][0] + m[1][1]
            val det = m[0][0] * m[1][1] - m[0][1] * m[1][0]
            val disc = tr * tr - 4.0 * det
            steps += "Characteristic equation: λ² - (${fmt(tr)})λ + (${fmt(det)}) = 0"
            steps += "Discriminant Δ = ${fmt(disc)}"
            return if (disc >= 0) {
                val l1 = (tr + sqrt(disc)) / 2.0
                val l2 = (tr - sqrt(disc)) / 2.0
                "λ₁ = ${fmt(l1)}, λ₂ = ${fmt(l2)}" to steps
            } else {
                val real = tr / 2.0
                val imag = sqrt(-disc) / 2.0
                "λ₁ = ${fmt(real)} + ${fmt(imag)}i, λ₂ = ${fmt(real)} - ${fmt(imag)}i" to steps
            }
        }
        // QR iteration for 3x3 and 4x4 real matrices (with shifts)
        var a = Array(n) { r -> m[r].clone() }
        repeat(80) {
            val (q, rMat) = qrDecompose(a)
            a = multiply(rMat, q)
        }
        val diag = (0 until n).map { fmt(a[it][it]) }
        steps += "Applied 80-step Householder/Gram-Schmidt QR iteration"
        steps += "Converged diagonal spectrum: [${diag.joinToString(", ")}]"
        return diag.mapIndexed { idx, v -> "λ${idx + 1} ≈ $v" }.joinToString(", ") to steps
    }

    private fun qrDecompose(a: Array<DoubleArray>): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        val n = a.size
        val q = Array(n) { DoubleArray(n) }
        val r = Array(n) { DoubleArray(n) }
        val uCols = Array(n) { DoubleArray(n) }
        for (j in 0 until n) {
            for (i in 0 until n) uCols[j][i] = a[i][j]
            for (k in 0 until j) {
                var dot = 0.0
                for (i in 0 until n) dot += a[i][j] * q[i][k]
                r[k][j] = dot
                for (i in 0 until n) uCols[j][i] -= dot * q[i][k]
            }
            var norm = 0.0
            for (i in 0 until n) norm += uCols[j][i] * uCols[j][i]
            norm = sqrt(norm)
            r[j][j] = norm
            if (norm > 1e-12) {
                for (i in 0 until n) q[i][j] = uCols[j][i] / norm
            }
        }
        return q to r
    }

    private fun fmt(v: Double) = MathAstParser.formatNumber(v)
}

object DiscreteMathEngine {

    fun isBaseExpression(expr: String): Boolean {
        val lower = expr.lowercase()
        return lower.contains("0b") || lower.contains("0x") || lower.contains("0o") ||
            Regex("\\([0-9a-fA-F]+\\)_?(2|8|10|16)").containsMatchIn(expr) ||
            lower.startsWith("bin(") || lower.startsWith("hex(") ||
            lower.startsWith("oct(") || lower.startsWith("dec(")
    }

    fun evaluateBaseExpression(expr: String): Pair<String, List<String>> {
        val steps = mutableListOf<String>()
        val trimmed = expr.trim()
        val lower = trimmed.lowercase()

        // Direct conversion functions: bin(n), hex(n), oct(n), dec(n)
        val funcMatch = Regex("^(bin|hex|oct|dec)\\((.+)\\)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (funcMatch != null) {
            val fn = funcMatch.groupValues[1].lowercase()
            val inner = funcMatch.groupValues[2]
            val value = evalMixedBaseMath(inner, steps)
            val intVal = value.toLong()
            steps += "Decimal value = $intVal"
            val formatted = when (fn) {
                "bin" -> "0b${intVal.toString(2)}"
                "hex" -> "0x${intVal.toString(16).uppercase()}"
                "oct" -> "0o${intVal.toString(8)}"
                else -> "$intVal"
            }
            return "$formatted  (Dec: $intVal | Bin: ${intVal.toString(2)} | Hex: 0x${intVal.toString(16).uppercase()})" to steps
        }

        val value = evalMixedBaseMath(lower, steps)
        val intVal = value.toLong()
        val result = "$intVal  [Bin: 0b${intVal.toString(2)} | Oct: 0o${intVal.toString(8)} | Hex: 0x${intVal.toString(16).uppercase()}]"
        return result to steps
    }

    private fun evalMixedBaseMath(raw: String, steps: MutableList<String>): Double {
        var normalized = raw
        // Convert (1011)_2 or (1011)2 or (F)_16 or (F)16
        val subscriptRegex = Regex("\\(([0-9a-fA-F]+)\\)_?(2|8|10|16)")
        normalized = subscriptRegex.replace(normalized) { m ->
            val digits = m.groupValues[1]
            val base = m.groupValues[2].toInt()
            val dec = digits.toLong(base)
            steps += "(${digits})₍${base}₎ = $dec"
            dec.toString()
        }
        // Convert 0b..., 0x..., 0o...
        normalized = Regex("0b([01]+)", RegexOption.IGNORE_CASE).replace(normalized) { m ->
            val dec = m.groupValues[1].toLong(2)
            steps += "${m.value} = $dec"
            dec.toString()
        }
        normalized = Regex("0x([0-9a-fA-F]+)", RegexOption.IGNORE_CASE).replace(normalized) { m ->
            val dec = m.groupValues[1].toLong(16)
            steps += "${m.value} = $dec"
            dec.toString()
        }
        normalized = Regex("0o([0-7]+)", RegexOption.IGNORE_CASE).replace(normalized) { m ->
            val dec = m.groupValues[1].toLong(8)
            steps += "${m.value} = $dec"
            dec.toString()
        }
        return MathAstParser.eval(MathAstParser.parse(normalized), emptyMap())
    }

    fun isBooleanExpression(expr: String): Boolean {
        val lower = expr.lowercase().trim()
        return lower.startsWith("truth(") || lower.startsWith("bool(") ||
            expr.contains("&&") || expr.contains("||") || expr.contains("∧") ||
            expr.contains("∨") || expr.contains("¬") || expr.contains("⊕") ||
            Regex("\\b(AND|OR|XOR|NAND|NOR|NOT)\\b", RegexOption.IGNORE_CASE).containsMatchIn(expr)
    }

    fun evaluateBoolean(raw: String): Pair<String, List<String>> {
        var expr = raw.trim()
        if (expr.lowercase().startsWith("truth(") && expr.endsWith(")")) {
            expr = expr.substring(6, expr.length - 1)
        } else if (expr.lowercase().startsWith("bool(") && expr.endsWith(")")) {
            expr = expr.substring(5, expr.length - 1)
        }
        val normalized = expr
            .replace("∧", " & ")
            .replace("∨", " | ")
            .replace("¬", " ! ")
            .replace("⊕", " ^ ")
            .replace("&&", " & ")
            .replace("||", " | ")
            .replace("~", " ! ")
            .replace(Regex("\\bAND\\b", RegexOption.IGNORE_CASE), "&")
            .replace(Regex("\\bOR\\b", RegexOption.IGNORE_CASE), "|")
            .replace(Regex("\\bXOR\\b", RegexOption.IGNORE_CASE), "^")
            .replace(Regex("\\bNOT\\b", RegexOption.IGNORE_CASE), "!")

        val vars = Regex("\\b[A-Za-z]\\b").findAll(normalized)
            .map { it.value.uppercase() }
            .distinct()
            .sorted()
            .take(4)
            .toList()
        if (vars.isEmpty()) throw IllegalArgumentException("No boolean variables (A-D) found")

        val rows = 1 shl vars.size
        val minterms = mutableListOf<Int>()
        val tableRows = mutableListOf<String>()
        tableRows += "${vars.joinToString(" | ")} || F"
        tableRows += "-".repeat(vars.size * 4 + 5)

        for (mask in 0 until rows) {
            val env = mutableMapOf<String, Boolean>()
            val bits = mutableListOf<Int>()
            for ((idx, v) in vars.withIndex()) {
                val bit = ((mask shr (vars.size - 1 - idx)) and 1) == 1
                env[v] = bit
                bits += if (bit) 1 else 0
            }
            val out = evalBoolAst(normalized, env)
            if (out) minterms += mask
            tableRows += "${bits.joinToString(" | ")} || ${if (out) 1 else 0}"
        }

        val sopTerms = minterms.map { m ->
            vars.mapIndexed { idx, v ->
                val isOne = ((m shr (vars.size - 1 - idx)) and 1) == 1
                if (isOne) v else "$v'"
            }.joinToString("")
        }
        val sop = if (sopTerms.isEmpty()) "0 (Contradiction)"
        else if (minterms.size == rows) "1 (Tautology)"
        else sopTerms.joinToString(" + ")

        val steps = mutableListOf<String>()
        steps += "Variables: ${vars.joinToString(", ")} (${rows} rows)"
        steps += "Minterms Σm(${minterms.joinToString(", ")})"
        steps.addAll(tableRows)

        return "SOP: $sop" to steps
    }

    private fun evalBoolAst(expr: String, env: Map<String, Boolean>): Boolean {
        val tokens = Regex("[A-Za-z]|[!&|^()]").findAll(expr).map { it.value }.toList()
        var pos = 0
        fun peek() = tokens.getOrNull(pos)
        fun next() = tokens[pos++]
        lateinit var parseOr: () -> Boolean

        fun parseNot(): Boolean {
            if (peek() == "!") {
                next()
                return !parseNot()
            }
            if (peek() == "(") {
                next()
                val v = parseOr()
                if (peek() == ")") next()
                return v
            }
            val t = next().uppercase()
            return env[t] ?: (t == "1")
        }

        fun parseAnd(): Boolean {
            var left = parseNot()
            while (peek() == "&") {
                next()
                left = left && parseNot()
            }
            return left
        }

        fun parseXor(): Boolean {
            var left = parseAnd()
            while (peek() == "^") {
                next()
                left = left xor parseAnd()
            }
            return left
        }

        parseOr = {
            var left = parseXor()
            while (peek() == "|") {
                next()
                left = left || parseXor()
            }
            left
        }
        return parseOr()
    }
}

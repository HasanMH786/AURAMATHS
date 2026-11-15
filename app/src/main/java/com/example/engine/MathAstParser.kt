package com.example.engine

import kotlin.math.*

sealed interface MathExpr {
    data class Num(val value: Double) : MathExpr
    data class Var(val name: String) : MathExpr
    data class Neg(val inner: MathExpr) : MathExpr
    data class BinOp(val op: Char, val left: MathExpr, val right: MathExpr) : MathExpr
    data class Call(val name: String, val args: List<MathExpr>) : MathExpr
}

object MathAstParser {
    private class Scanner(private val input: String) {
        var pos = 0

        fun peek(): Char? = input.getOrNull(pos)
        fun skipSpaces() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        fun nextToken(): String? {
            skipSpaces()
            if (pos >= input.length) return null
            val ch = input[pos]
            if (ch.isDigit() || (ch == '.' && input.getOrNull(pos + 1)?.isDigit() == true)) {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E') &&
                    (input.getOrNull(pos + 1)?.isDigit() == true ||
                        ((input.getOrNull(pos + 1) == '+' || input.getOrNull(pos + 1) == '-') &&
                            input.getOrNull(pos + 2)?.isDigit() == true))
                ) {
                    pos++
                    if (input[pos] == '+' || input[pos] == '-') pos++
                    while (pos < input.length && input[pos].isDigit()) pos++
                }
                return input.substring(start, pos)
            }
            if (ch.isLetter() || ch == '_' || ch == 'π') {
                val start = pos
                if (ch == 'π') {
                    pos++
                    return "pi"
                }
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
                return input.substring(start, pos)
            }
            pos++
            return ch.toString()
        }
    }

    fun tokenize(raw: String): List<String> {
        val cleaned = raw
            .replace("×", "*")
            .replace("·", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("²", "^2")
            .replace("³", "^3")
        val scanner = Scanner(cleaned)
        val rawTokens = mutableListOf<String>()
        while (true) {
            val tok = scanner.nextToken() ?: break
            rawTokens.add(tok)
        }
        // Insert implicit multiplication (e.g., 2x -> 2 * x, 2(x+1) -> 2 * (x+1), (x)(y) -> (x) * (y))
        val out = mutableListOf<String>()
        for (i in rawTokens.indices) {
            val curr = rawTokens[i]
            if (i > 0) {
                val prev = rawTokens[i - 1]
                val prevIsValue = prev[0].isDigit() || prev == ")" || (prev[0].isLetter() && curr == "(" && !isKnownFunction(prev)) || (prev[0].isLetter() && prev != "(" && !isKnownFunction(prev))
                val currStartsValue = curr[0].isDigit() || curr[0].isLetter() || curr == "("
                val prevIsPlainIdent = prev[0].isLetter() && !isKnownFunction(prev)
                if ((prev[0].isDigit() && currStartsValue) ||
                    (prev == ")" && currStartsValue) ||
                    (prevIsPlainIdent && (curr == "(" || curr[0].isDigit() || curr[0].isLetter()))
                ) {
                    out.add("*")
                } else if (prevIsValue && curr == "(" && prev[0].isDigit()) {
                    out.add("*")
                }
            }
            out.add(curr)
        }
        return out
    }

    private fun isKnownFunction(name: String): Boolean {
        return name.lowercase() in setOf(
            "sin", "cos", "tan", "asin", "acos", "atan",
            "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs",
            "ln", "log", "log10", "log2", "exp", "floor", "ceil", "round", "fact"
        )
    }

    fun parse(expr: String): MathExpr {
        val tokens = tokenize(expr)
        if (tokens.isEmpty()) throw IllegalArgumentException("Empty expression")
        var idx = 0

        fun peek(): String? = tokens.getOrNull(idx)
        fun consume(): String = tokens[idx++]

        lateinit var parseExpr: () -> MathExpr

        fun parsePrimary(): MathExpr {
            val tok = peek() ?: throw IllegalArgumentException("Unexpected end of expression")
            return when {
                tok == "(" -> {
                    consume()
                    val inner = parseExpr()
                    if (peek() != ")") throw IllegalArgumentException("Missing closing parenthesis")
                    consume()
                    inner
                }
                tok[0].isDigit() || tok[0] == '.' -> {
                    consume()
                    MathExpr.Num(tok.toDouble())
                }
                tok[0].isLetter() || tok[0] == '_' -> {
                    consume()
                    if (peek() == "(") {
                        consume()
                        val args = mutableListOf<MathExpr>()
                        if (peek() != ")") {
                            args.add(parseExpr())
                            while (peek() == ",") {
                                consume()
                                args.add(parseExpr())
                            }
                        }
                        if (peek() != ")") throw IllegalArgumentException("Missing ')' after function call $tok")
                        consume()
                        MathExpr.Call(tok.lowercase(), args)
                    } else {
                        MathExpr.Var(tok)
                    }
                }
                else -> throw IllegalArgumentException("Unexpected token: $tok")
            }
        }

        fun parseFactorial(): MathExpr {
            var base = parsePrimary()
            while (peek() == "!") {
                consume()
                base = MathExpr.Call("fact", listOf(base))
            }
            return base
        }

        fun parseUnary(): MathExpr {
            return when (peek()) {
                "-" -> {
                    consume()
                    MathExpr.Neg(parseUnary())
                }
                "+" -> {
                    consume()
                    parseUnary()
                }
                else -> parseFactorial()
            }
        }

        fun parsePower(): MathExpr {
            val base = parseUnary()
            if (peek() == "^") {
                consume()
                val exp = parsePower()
                return MathExpr.BinOp('^', base, exp)
            }
            return base
        }

        fun parseTerm(): MathExpr {
            var left = parsePower()
            while (peek() == "*" || peek() == "/" || peek() == "%") {
                val op = consume()[0]
                val right = parsePower()
                left = MathExpr.BinOp(op, left, right)
            }
            return left
        }

        parseExpr = {
            var left = parseTerm()
            while (peek() == "+" || peek() == "-") {
                val op = consume()[0]
                val right = parseTerm()
                left = MathExpr.BinOp(op, left, right)
            }
            left
        }

        val root = parseExpr()
        if (idx < tokens.size) {
            throw IllegalArgumentException("Unexpected token '${tokens[idx]}'")
        }
        return root
    }

    fun eval(expr: MathExpr, env: Map<String, Double>): Double {
        return when (expr) {
            is MathExpr.Num -> expr.value
            is MathExpr.Var -> {
                val key = expr.name
                env[key] ?: env[key.lowercase()] ?: when (key.lowercase()) {
                    "pi" -> PI
                    "e" -> E
                    "tau" -> 2.0 * PI
                    "phi" -> (1.0 + sqrt(5.0)) / 2.0
                    else -> throw IllegalArgumentException("Undefined variable '$key'")
                }
            }
            is MathExpr.Neg -> -eval(expr.inner, env)
            is MathExpr.BinOp -> {
                val l = eval(expr.left, env)
                val r = eval(expr.right, env)
                when (expr.op) {
                    '+' -> l + r
                    '-' -> l - r
                    '*' -> l * r
                    '/' -> l / r
                    '%' -> l % r
                    '^' -> l.pow(r)
                    else -> error("Unknown op ${expr.op}")
                }
            }
            is MathExpr.Call -> {
                val args = expr.args.map { eval(it, env) }
                when (expr.name) {
                    "sin" -> sin(args[0])
                    "cos" -> cos(args[0])
                    "tan" -> tan(args[0])
                    "asin" -> asin(args[0])
                    "acos" -> acos(args[0])
                    "atan" -> atan(args[0])
                    "sinh" -> sinh(args[0])
                    "cosh" -> cosh(args[0])
                    "tanh" -> tanh(args[0])
                    "sqrt" -> sqrt(args[0])
                    "cbrt" -> cbrt(args[0])
                    "abs" -> abs(args[0])
                    "ln" -> ln(args[0])
                    "log10" -> log10(args[0])
                    "log2" -> log2(args[0])
                    "log" -> if (args.size >= 2) log(args[1], args[0]) else log10(args[0])
                    "exp" -> exp(args[0])
                    "floor" -> floor(args[0])
                    "ceil" -> ceil(args[0])
                    "round" -> round(args[0])
                    "fact" -> factorial(args[0])
                    else -> throw IllegalArgumentException("Unknown function '${expr.name}'")
                }
            }
        }
    }

    private fun factorial(x: Double): Double {
        val n = x.toInt()
        if (x < 0 || abs(x - n) > 1e-9 || n > 170) {
            throw IllegalArgumentException("Factorial defined for integers 0..170")
        }
        var res = 1.0
        for (i in 2..n) res *= i
        return res
    }

    fun differentiate(expr: MathExpr, variable: String): MathExpr {
        return simplify(rawDiff(expr, variable))
    }

    private fun rawDiff(expr: MathExpr, v: String): MathExpr {
        return when (expr) {
            is MathExpr.Num -> MathExpr.Num(0.0)
            is MathExpr.Var -> if (expr.name.equals(v, ignoreCase = true)) MathExpr.Num(1.0) else MathExpr.Num(0.0)
            is MathExpr.Neg -> MathExpr.Neg(rawDiff(expr.inner, v))
            is MathExpr.BinOp -> when (expr.op) {
                '+' -> MathExpr.BinOp('+', rawDiff(expr.left, v), rawDiff(expr.right, v))
                '-' -> MathExpr.BinOp('-', rawDiff(expr.left, v), rawDiff(expr.right, v))
                '*' -> MathExpr.BinOp(
                    '+',
                    MathExpr.BinOp('*', rawDiff(expr.left, v), expr.right),
                    MathExpr.BinOp('*', expr.left, rawDiff(expr.right, v))
                )
                '/' -> MathExpr.BinOp(
                    '/',
                    MathExpr.BinOp(
                        '-',
                        MathExpr.BinOp('*', rawDiff(expr.left, v), expr.right),
                        MathExpr.BinOp('*', expr.left, rawDiff(expr.right, v))
                    ),
                    MathExpr.BinOp('^', expr.right, MathExpr.Num(2.0))
                )
                '^' -> {
                    val u = expr.left
                    val n = expr.right
                    if (!containsVar(n, v)) {
                        // Power + chain rule: n * u^(n-1) * u'
                        MathExpr.BinOp(
                            '*',
                            MathExpr.BinOp('*', n, MathExpr.BinOp('^', u, MathExpr.BinOp('-', n, MathExpr.Num(1.0)))),
                            rawDiff(u, v)
                        )
                    } else if (!containsVar(u, v)) {
                        // Exponential rule: a^v * ln(a) * v'
                        MathExpr.BinOp(
                            '*',
                            MathExpr.BinOp('*', expr, MathExpr.Call("ln", listOf(u))),
                            rawDiff(n, v)
                        )
                    } else {
                        // Generalized u^v: u^v * (v' * ln(u) + v * u'/u)
                        MathExpr.BinOp(
                            '*',
                            expr,
                            MathExpr.BinOp(
                                '+',
                                MathExpr.BinOp('*', rawDiff(n, v), MathExpr.Call("ln", listOf(u))),
                                MathExpr.BinOp('*', n, MathExpr.BinOp('/', rawDiff(u, v), u))
                            )
                        )
                    }
                }
                else -> MathExpr.Num(0.0)
            }
            is MathExpr.Call -> {
                val u = expr.args.firstOrNull() ?: return MathExpr.Num(0.0)
                val du = rawDiff(u, v)
                val outerDeriv = when (expr.name) {
                    "sin" -> MathExpr.Call("cos", listOf(u))
                    "cos" -> MathExpr.Neg(MathExpr.Call("sin", listOf(u)))
                    "tan" -> MathExpr.BinOp('/', MathExpr.Num(1.0), MathExpr.BinOp('^', MathExpr.Call("cos", listOf(u)), MathExpr.Num(2.0)))
                    "exp" -> MathExpr.Call("exp", listOf(u))
                    "ln" -> MathExpr.BinOp('/', MathExpr.Num(1.0), u)
                    "log10" -> MathExpr.BinOp('/', MathExpr.Num(1.0), MathExpr.BinOp('*', u, MathExpr.Call("ln", listOf(MathExpr.Num(10.0)))))
                    "sqrt" -> MathExpr.BinOp('/', MathExpr.Num(1.0), MathExpr.BinOp('*', MathExpr.Num(2.0), MathExpr.Call("sqrt", listOf(u))))
                    "asin" -> MathExpr.BinOp('/', MathExpr.Num(1.0), MathExpr.Call("sqrt", listOf(MathExpr.BinOp('-', MathExpr.Num(1.0), MathExpr.BinOp('^', u, MathExpr.Num(2.0))))))
                    "acos" -> MathExpr.Neg(MathExpr.BinOp('/', MathExpr.Num(1.0), MathExpr.Call("sqrt", listOf(MathExpr.BinOp('-', MathExpr.Num(1.0), MathExpr.BinOp('^', u, MathExpr.Num(2.0)))))))
                    "atan" -> MathExpr.BinOp('/', MathExpr.Num(1.0), MathExpr.BinOp('+', MathExpr.Num(1.0), MathExpr.BinOp('^', u, MathExpr.Num(2.0))))
                    "sinh" -> MathExpr.Call("cosh", listOf(u))
                    "cosh" -> MathExpr.Call("sinh", listOf(u))
                    else -> MathExpr.Num(0.0)
                }
                MathExpr.BinOp('*', outerDeriv, du)
            }
        }
    }

    fun containsVar(expr: MathExpr, v: String): Boolean {
        return when (expr) {
            is MathExpr.Num -> false
            is MathExpr.Var -> expr.name.equals(v, ignoreCase = true)
            is MathExpr.Neg -> containsVar(expr.inner, v)
            is MathExpr.BinOp -> containsVar(expr.left, v) || containsVar(expr.right, v)
            is MathExpr.Call -> expr.args.any { containsVar(it, v) }
        }
    }

    fun simplify(expr: MathExpr): MathExpr {
        return when (expr) {
            is MathExpr.Num, is MathExpr.Var -> expr
            is MathExpr.Neg -> {
                val inner = simplify(expr.inner)
                when (inner) {
                    is MathExpr.Num -> MathExpr.Num(-inner.value)
                    is MathExpr.Neg -> inner.inner
                    else -> MathExpr.Neg(inner)
                }
            }
            is MathExpr.Call -> MathExpr.Call(expr.name, expr.args.map { simplify(it) })
            is MathExpr.BinOp -> {
                val l = simplify(expr.left)
                val r = simplify(expr.right)
                if (l is MathExpr.Num && r is MathExpr.Num) {
                    val v = when (expr.op) {
                        '+' -> l.value + r.value
                        '-' -> l.value - r.value
                        '*' -> l.value * r.value
                        '/' -> if (abs(r.value) > 1e-12) l.value / r.value else return MathExpr.BinOp('/', l, r)
                        '^' -> l.value.pow(r.value)
                        else -> return MathExpr.BinOp(expr.op, l, r)
                    }
                    return MathExpr.Num(v)
                }
                when (expr.op) {
                    '+' -> when {
                        l is MathExpr.Num && l.value == 0.0 -> r
                        r is MathExpr.Num && r.value == 0.0 -> l
                        r is MathExpr.Neg -> simplify(MathExpr.BinOp('-', l, r.inner))
                        else -> MathExpr.BinOp('+', l, r)
                    }
                    '-' -> when {
                        r is MathExpr.Num && r.value == 0.0 -> l
                        l is MathExpr.Num && l.value == 0.0 -> simplify(MathExpr.Neg(r))
                        else -> MathExpr.BinOp('-', l, r)
                    }
                    '*' -> when {
                        (l is MathExpr.Num && l.value == 0.0) || (r is MathExpr.Num && r.value == 0.0) -> MathExpr.Num(0.0)
                        l is MathExpr.Num && l.value == 1.0 -> r
                        r is MathExpr.Num && r.value == 1.0 -> l
                        l is MathExpr.Num && l.value == -1.0 -> simplify(MathExpr.Neg(r))
                        r is MathExpr.Num && r.value == -1.0 -> simplify(MathExpr.Neg(l))
                        r is MathExpr.Num -> simplify(MathExpr.BinOp('*', r, l))
                        l is MathExpr.Num && r is MathExpr.BinOp && r.op == '*' && r.left is MathExpr.Num ->
                            simplify(MathExpr.BinOp('*', MathExpr.Num(l.value * r.left.value), r.right))
                        else -> MathExpr.BinOp('*', l, r)
                    }
                    '/' -> when {
                        l is MathExpr.Num && l.value == 0.0 -> MathExpr.Num(0.0)
                        r is MathExpr.Num && r.value == 1.0 -> l
                        else -> MathExpr.BinOp('/', l, r)
                    }
                    '^' -> when {
                        r is MathExpr.Num && r.value == 0.0 -> MathExpr.Num(1.0)
                        r is MathExpr.Num && r.value == 1.0 -> l
                        l is MathExpr.Num && l.value == 0.0 -> MathExpr.Num(0.0)
                        l is MathExpr.Num && l.value == 1.0 -> MathExpr.Num(1.0)
                        else -> MathExpr.BinOp('^', l, r)
                    }
                    else -> MathExpr.BinOp(expr.op, l, r)
                }
            }
        }
    }

    fun integrateSymbolic(expr: MathExpr, v: String): MathExpr? {
        val s = simplify(expr)
        return when (s) {
            is MathExpr.Num -> simplify(MathExpr.BinOp('*', s, MathExpr.Var(v)))
            is MathExpr.Var -> if (s.name.equals(v, true)) {
                MathExpr.BinOp('*', MathExpr.Num(0.5), MathExpr.BinOp('^', MathExpr.Var(v), MathExpr.Num(2.0)))
            } else {
                MathExpr.BinOp('*', s, MathExpr.Var(v))
            }
            is MathExpr.Neg -> integrateSymbolic(s.inner, v)?.let { simplify(MathExpr.Neg(it)) }
            is MathExpr.BinOp -> when (s.op) {
                '+' -> {
                    val il = integrateSymbolic(s.left, v) ?: return null
                    val ir = integrateSymbolic(s.right, v) ?: return null
                    simplify(MathExpr.BinOp('+', il, ir))
                }
                '-' -> {
                    val il = integrateSymbolic(s.left, v) ?: return null
                    val ir = integrateSymbolic(s.right, v) ?: return null
                    simplify(MathExpr.BinOp('-', il, ir))
                }
                '*' -> {
                    if (!containsVar(s.left, v)) {
                        integrateSymbolic(s.right, v)?.let { simplify(MathExpr.BinOp('*', s.left, it)) }
                    } else if (!containsVar(s.right, v)) {
                        integrateSymbolic(s.left, v)?.let { simplify(MathExpr.BinOp('*', s.right, it)) }
                    } else null
                }
                '^' -> {
                    if (s.left is MathExpr.Var && s.left.name.equals(v, true) && !containsVar(s.right, v)) {
                        val n = s.right
                        if (n is MathExpr.Num && abs(n.value + 1.0) < 1e-9) {
                            MathExpr.Call("ln", listOf(MathExpr.Call("abs", listOf(MathExpr.Var(v)))))
                        } else {
                            val nPlus1 = simplify(MathExpr.BinOp('+', n, MathExpr.Num(1.0)))
                            simplify(MathExpr.BinOp('/', MathExpr.BinOp('^', MathExpr.Var(v), nPlus1), nPlus1))
                        }
                    } else null
                }
                '/' -> {
                    if (s.left is MathExpr.Num && s.left.value == 1.0 && s.right is MathExpr.Var && s.right.name.equals(v, true)) {
                        MathExpr.Call("ln", listOf(MathExpr.Call("abs", listOf(MathExpr.Var(v)))))
                    } else if (!containsVar(s.right, v)) {
                        integrateSymbolic(s.left, v)?.let { simplify(MathExpr.BinOp('/', it, s.right)) }
                    } else null
                }
                else -> null
            }
            is MathExpr.Call -> {
                val arg = s.args.firstOrNull()
                if (arg is MathExpr.Var && arg.name.equals(v, true)) {
                    when (s.name) {
                        "sin" -> MathExpr.Neg(MathExpr.Call("cos", listOf(arg)))
                        "cos" -> MathExpr.Call("sin", listOf(arg))
                        "exp" -> MathExpr.Call("exp", listOf(arg))
                        else -> null
                    }
                } else null
            }
        }
    }

    fun format(expr: MathExpr): String {
        return when (expr) {
            is MathExpr.Num -> formatNumber(expr.value)
            is MathExpr.Var -> expr.name
            is MathExpr.Neg -> {
                val inner = format(expr.inner)
                if (expr.inner is MathExpr.BinOp && (expr.inner.op == '+' || expr.inner.op == '-')) "-($inner)" else "-$inner"
            }
            is MathExpr.BinOp -> {
                val lStr = format(expr.left)
                val rStr = format(expr.right)
                when (expr.op) {
                    '+' -> "$lStr + $rStr"
                    '-' -> {
                        val rhs = if (expr.right is MathExpr.BinOp && (expr.right.op == '+' || expr.right.op == '-')) "($rStr)" else rStr
                        "$lStr - $rhs"
                    }
                    '*' -> {
                        val lhs = if (expr.left is MathExpr.BinOp && (expr.left.op == '+' || expr.left.op == '-')) "($lStr)" else lStr
                        val rhs = if (expr.right is MathExpr.BinOp && (expr.right.op == '+' || expr.right.op == '-')) "($rStr)" else rStr
                        if (expr.left is MathExpr.Num && (expr.right is MathExpr.Var || expr.right is MathExpr.Call || (expr.right is MathExpr.BinOp && expr.right.op == '^'))) {
                            "$lhs$rhs"
                        } else {
                            "$lhs * $rhs"
                        }
                    }
                    '/' -> {
                        val lhs = if (expr.left is MathExpr.BinOp && (expr.left.op == '+' || expr.left.op == '-')) "($lStr)" else lStr
                        val rhs = if (expr.right is MathExpr.BinOp) "($rStr)" else rStr
                        "$lhs / $rhs"
                    }
                    '^' -> {
                        val lhs = if (expr.left is MathExpr.BinOp || expr.left is MathExpr.Neg) "($lStr)" else lStr
                        val rhs = if (expr.right is MathExpr.BinOp || expr.right is MathExpr.Neg) "($rStr)" else rStr
                        "$lhs^$rhs"
                    }
                    else -> "$lStr ${expr.op} $rStr"
                }
            }
            is MathExpr.Call -> "${expr.name}(${expr.args.joinToString(", ") { format(it) }})"
        }
    }

    fun formatNumber(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "∞" else "-∞"
        val rounded = round(v)
        if (abs(v - rounded) < 1e-9 && abs(rounded) < 1e15) {
            return rounded.toLong().toString()
        }
        return "%.6f".format(v).trimEnd('0').trimEnd('.')
    }
}

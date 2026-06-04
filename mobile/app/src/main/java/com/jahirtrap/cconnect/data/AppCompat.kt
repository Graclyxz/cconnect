package com.jahirtrap.cconnect.data

import com.jahirtrap.cconnect.BuildConfig

object AppCompat {
    val SUPPORTED_SERVER: String = BuildConfig.SUPPORTED_SERVER

    private fun parse(version: String): List<Int> =
        version.trim().removePrefix("v").split('.').map { part ->
            part.takeWhile(Char::isDigit).toIntOrNull() ?: 0
        }

    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun satisfies(version: String?, range: String?): Boolean {
        if (version.isNullOrBlank() || range.isNullOrBlank()) return true
        return range.trim().split(Regex("\\s+")).all { token ->
            val (op, target) = when {
                token.startsWith(">=") -> ">=" to token.drop(2)
                token.startsWith("<=") -> "<=" to token.drop(2)
                token.startsWith(">") -> ">" to token.drop(1)
                token.startsWith("<") -> "<" to token.drop(1)
                token.startsWith("=") -> "=" to token.drop(1)
                else -> "=" to token
            }
            if (target.isBlank()) return@all true
            val c = compare(version, target)
            when (op) {
                ">=" -> c >= 0
                "<=" -> c <= 0
                ">" -> c > 0
                "<" -> c < 0
                else -> c == 0
            }
        }
    }
}

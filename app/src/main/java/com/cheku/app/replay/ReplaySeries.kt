package com.cheku.app.replay

object ReplaySeries {
    fun movingAverage(values: List<Double>, radius: Int = 2): List<Double> {
        if (values.isEmpty()) return emptyList()
        val safeRadius = radius.coerceAtLeast(0)
        return values.indices.map { index ->
            val left = (index - safeRadius).coerceAtLeast(0)
            val right = (index + safeRadius).coerceAtMost(values.lastIndex)
            values.subList(left, right + 1).average()
        }
    }
}

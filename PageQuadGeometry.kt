package com.scanpro.pdfdocscanner.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Validation and output sizing for normalized page corners in TL, TR, BR, BL order.
 * Kept free of Android classes so geometry can be covered by local JVM unit tests.
 */
object PageQuadGeometry {
    fun isValid(
        points: List<Float>?,
        minArea: Float = 0.005f,
        minEdge: Float = 0.01f,
    ): Boolean {
        if (points == null || points.size != 8) return false
        if (!minArea.isFinite() || !minEdge.isFinite() || minArea < 0f || minEdge < 0f) return false
        if (points.any { !it.isFinite() || it !in 0f..1f }) return false

        var twiceArea = 0f
        var winding = 0f
        for (i in 0..3) {
            val j = (i + 1) % 4
            val k = (i + 2) % 4
            val ax = points[j * 2] - points[i * 2]
            val ay = points[j * 2 + 1] - points[i * 2 + 1]
            val bx = points[k * 2] - points[j * 2]
            val by = points[k * 2 + 1] - points[j * 2 + 1]
            val cross = ax * by - ay * bx
            if (abs(cross) < 0.00001f) return false
            if (i == 0) winding = if (cross > 0f) 1f else -1f
            else if (cross * winding <= 0f) return false

            val edge = sqrt(ax * ax + ay * ay)
            if (edge < minEdge) return false

            twiceArea += points[i * 2] * points[j * 2 + 1] -
                points[j * 2] * points[i * 2 + 1]
        }
        return abs(twiceArea) / 2f >= minArea
    }

    fun outputSize(
        sourceWidth: Int,
        sourceHeight: Int,
        points: List<Float>?,
        maxDimension: Int,
    ): Pair<Int, Int>? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || maxDimension <= 0 || !isValid(points)) return null
        val q = requireNotNull(points)
        fun distance(a: Int, b: Int): Float {
            val dx = (q[a * 2] - q[b * 2]) * sourceWidth
            val dy = (q[a * 2 + 1] - q[b * 2 + 1]) * sourceHeight
            return sqrt(dx * dx + dy * dy)
        }
        var width = max(distance(0, 1), distance(3, 2))
        var height = max(distance(0, 3), distance(1, 2))
        val longest = max(width, height)
        if (longest <= 0f || !longest.isFinite()) return null
        if (longest > maxDimension) {
            val scale = maxDimension / longest
            width *= scale
            height *= scale
        }
        return width.toInt().coerceAtLeast(1) to height.toInt().coerceAtLeast(1)
    }
}

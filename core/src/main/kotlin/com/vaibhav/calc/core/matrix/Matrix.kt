package com.vaibhav.calc.core.matrix

import com.vaibhav.calc.core.num.ArithFault
import com.vaibhav.calc.core.num.Num
import com.vaibhav.calc.core.num.div
import com.vaibhav.calc.core.num.minus
import com.vaibhav.calc.core.num.plus
import com.vaibhav.calc.core.num.times
import com.vaibhav.calc.core.num.unaryMinus

/**
 * A dense matrix over the exact [Num] tower (spec D7). Every operation —
 * determinant, inverse, rank — is done with **exact rational arithmetic** (no
 * `Double`, no rounding), so `det` and `A⁻¹` are exact whenever the entries are.
 * Row reduction uses rational pivots; because the field is exact, no pivoting
 * strategy is needed for numerical stability.
 */
class Matrix private constructor(
    val rows: Int,
    val cols: Int,
    val data: List<List<Num>>,
) {
    val isSquare: Boolean get() = rows == cols

    fun get(r: Int, c: Int): Num = data[r][c]

    operator fun plus(o: Matrix): Matrix {
        requireSameShape(o)
        return Matrix(rows, cols, List(rows) { r -> List(cols) { c -> data[r][c] + o.data[r][c] } })
    }

    operator fun minus(o: Matrix): Matrix {
        requireSameShape(o)
        return Matrix(rows, cols, List(rows) { r -> List(cols) { c -> data[r][c] - o.data[r][c] } })
    }

    operator fun times(o: Matrix): Matrix {
        if (cols != o.rows) throw ArithFault.Domain("matrix dimensions do not match for multiplication", "matrix")
        val result = List(rows) { r ->
            List(o.cols) { c ->
                var acc: Num = Num.ZERO
                for (k in 0 until cols) acc = acc + data[r][k] * o.data[k][c]
                acc
            }
        }
        return Matrix(rows, o.cols, result)
    }

    fun scalarTimes(s: Num): Matrix = Matrix(rows, cols, List(rows) { r -> List(cols) { c -> data[r][c] * s } })

    fun transpose(): Matrix = Matrix(cols, rows, List(cols) { c -> List(rows) { r -> data[r][c] } })

    /** Exact determinant via rational Gaussian elimination. */
    fun determinant(): Num {
        requireSquare()
        val n = rows
        val m = data.map { it.toMutableList() }.toMutableList()
        var det: Num = Num.ONE
        for (col in 0 until n) {
            var pivot = col
            while (pivot < n && m[pivot][col].isZero) pivot++
            if (pivot == n) return Num.ZERO
            if (pivot != col) {
                val t = m[pivot]; m[pivot] = m[col]; m[col] = t; det = -det
            }
            val p = m[col][col]
            det = det * p
            for (r in col + 1 until n) {
                if (!m[r][col].isZero) {
                    val factor = m[r][col] / p
                    for (c in col until n) m[r][c] = m[r][c] - factor * m[col][c]
                }
            }
        }
        return det
    }

    /** Exact inverse via Gauss–Jordan; throws if singular or non-square. */
    fun inverse(): Matrix {
        requireSquare()
        val n = rows
        val a = data.map { it.toMutableList() }.toMutableList()
        val inv = MutableList(n) { r -> MutableList(n) { c -> if (r == c) Num.ONE as Num else Num.ZERO as Num } }
        for (col in 0 until n) {
            var pivot = col
            while (pivot < n && a[pivot][col].isZero) pivot++
            if (pivot == n) throw ArithFault.Domain("matrix is singular (not invertible)", "inverse")
            if (pivot != col) {
                val ta = a[pivot]; a[pivot] = a[col]; a[col] = ta
                val ti = inv[pivot]; inv[pivot] = inv[col]; inv[col] = ti
            }
            val p = a[col][col]
            for (c in 0 until n) { a[col][c] = a[col][c] / p; inv[col][c] = inv[col][c] / p }
            for (r in 0 until n) {
                if (r != col && !a[r][col].isZero) {
                    val factor = a[r][col]
                    for (c in 0 until n) {
                        a[r][c] = a[r][c] - factor * a[col][c]
                        inv[r][c] = inv[r][c] - factor * inv[col][c]
                    }
                }
            }
        }
        return Matrix(n, n, inv.map { it.toList() })
    }

    /** Rank via exact row reduction. */
    fun rank(): Int {
        val m = data.map { it.toMutableList() }.toMutableList()
        var rank = 0
        var row = 0
        for (col in 0 until cols) {
            if (row >= rows) break
            var pivot = row
            while (pivot < rows && m[pivot][col].isZero) pivot++
            if (pivot == rows) continue
            val t = m[pivot]; m[pivot] = m[row]; m[row] = t
            val p = m[row][col]
            for (c in col until cols) m[row][c] = m[row][c] / p
            for (r in 0 until rows) {
                if (r != row && !m[r][col].isZero) {
                    val factor = m[r][col]
                    for (c in col until cols) m[r][c] = m[r][c] - factor * m[row][c]
                }
            }
            row++; rank++
        }
        return rank
    }

    private fun requireSquare() {
        if (!isSquare) throw ArithFault.Domain("operation requires a square matrix ($rows×$cols)", "matrix")
    }

    private fun requireSameShape(o: Matrix) {
        if (rows != o.rows || cols != o.cols) {
            throw ArithFault.Domain("matrix shapes differ (${rows}×$cols vs ${o.rows}×${o.cols})", "matrix")
        }
    }

    override fun equals(other: Any?): Boolean =
        other is Matrix && rows == other.rows && cols == other.cols &&
            (0 until rows).all { r -> (0 until cols).all { c -> data[r][c].compareTo(other.data[r][c]) == 0 } }

    // Consistent with the value-based equals above: entries that compareTo-equal
    // hash the same (via their canonical decimal), so equal matrices hash equal.
    override fun hashCode(): Int {
        var h = 31 * rows + cols
        for (row in data) for (x in row) {
            h = 31 * h + x.toBigDecimal(Num.MC).stripTrailingZeros().hashCode()
        }
        return h
    }

    override fun toString(): String = data.joinToString("; ") { row -> row.joinToString(" ") { it.toString() } }

    companion object {
        fun of(rows: List<List<Num>>): Matrix {
            require(rows.isNotEmpty()) { "matrix must have at least one row" }
            val cols = rows[0].size
            require(cols > 0) { "matrix must have at least one column" }
            require(rows.all { it.size == cols }) { "all rows must have the same length" }
            return Matrix(rows.size, cols, rows.map { it.toList() })
        }

        fun identity(n: Int): Matrix =
            Matrix(n, n, List(n) { r -> List(n) { c -> if (r == c) Num.ONE else Num.ZERO } })
    }
}

package com.example.lean.orientation

/**
 * 3x3 Matrix representation stored in row-major order:
 * m00 m01 m02
 * m10 m11 m12
 * m20 m21 m22
 */
class Matrix3x3(val data: FloatArray) {

    init {
        require(data.size == 9) { "Matrix3x3 requires array of size 9" }
    }

    operator fun get(row: Int, col: Int): Float = data[row * 3 + col]

    /**
     * Transpose of rotation matrix is its inverse.
     */
    fun transpose(): Matrix3x3 {
        return Matrix3x3(
            floatArrayOf(
                data[0], data[3], data[6],
                data[1], data[4], data[7],
                data[2], data[5], data[8]
            )
        )
    }

    fun multiply(other: Matrix3x3): Matrix3x3 {
        val result = FloatArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                var sum = 0f
                for (k in 0..2) {
                    sum += this[i, k] * other[k, j]
                }
                result[i * 3 + j] = sum
            }
        }
        return Matrix3x3(result)
    }

    fun multiply(vector: Vector3D): Vector3D {
        val rx = data[0] * vector.x + data[1] * vector.y + data[2] * vector.z
        val ry = data[3] * vector.x + data[4] * vector.y + data[5] * vector.z
        val rz = data[6] * vector.x + data[7] * vector.y + data[8] * vector.z
        return Vector3D(rx, ry, rz)
    }

    companion object {
        val IDENTITY = Matrix3x3(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
            )
        )
    }
}

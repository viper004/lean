package com.example.lean.orientation

import kotlin.math.sqrt

data class Vector3D(val x: Float, val y: Float, val z: Float) {

    fun dot(other: Vector3D): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3D): Vector3D = Vector3D(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalize(): Vector3D {
        val len = length()
        return if (len > 1e-6f) {
            Vector3D(x / len, y / len, z / len)
        } else {
            Vector3D(0f, 0f, 0f)
        }
    }

    fun minus(other: Vector3D): Vector3D = Vector3D(x - other.x, y - other.y, z - other.z)

    fun plus(other: Vector3D): Vector3D = Vector3D(x + other.x, y + other.y, z + other.z)

    fun scale(factor: Float): Vector3D = Vector3D(x * factor, y * factor, z * factor)

    companion object {
        val ZERO = Vector3D(0f, 0f, 0f)
        val UNIT_X = Vector3D(1f, 0f, 0f)
        val UNIT_Y = Vector3D(0f, 1f, 0f)
        val UNIT_Z = Vector3D(0f, 0f, 1f)
    }
}

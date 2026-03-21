package jez.lastfleetprotocol.prototype.components.game.utils

class PidController(val kp: Float = 0.4f, val ki: Float = 0.21f, val kd: Float = 0.9f) {
    private var lastError = 0f
    private var derivative = 0f
    private var integral = 0f

    fun getControl(error: Float): Float {
        val e = error * -1f
        lastError = e
        derivative = e - lastError
        integral += e
        return kp * e + ki * integral + kd * derivative
    }
}
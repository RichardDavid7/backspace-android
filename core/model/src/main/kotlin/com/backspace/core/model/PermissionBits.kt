package com.backspace.core.model

import java.math.BigInteger

object PermissionBits {
    val ADMINISTRATOR: BigInteger = BigInteger.ONE.shiftLeft(0)
    val VIEW_CHANNEL: BigInteger = BigInteger.ONE.shiftLeft(1)
    val CONNECT: BigInteger = BigInteger.ONE.shiftLeft(20)
    val SPEAK: BigInteger = BigInteger.ONE.shiftLeft(21)
    val MUTE_MEMBERS: BigInteger = BigInteger.ONE.shiftLeft(22)
    val DEAFEN_MEMBERS: BigInteger = BigInteger.ONE.shiftLeft(23)
    val MOVE_MEMBERS: BigInteger = BigInteger.ONE.shiftLeft(24)
    val DISCONNECT_MEMBERS: BigInteger = BigInteger.ONE.shiftLeft(26)
    val USE_SOUNDBOARD: BigInteger = BigInteger.ONE.shiftLeft(27)
    val MANAGE_SOUNDBOARD: BigInteger = BigInteger.ONE.shiftLeft(28)

    fun hasPermission(decimalBitmask: String?, bit: BigInteger): Boolean {
        if (decimalBitmask.isNullOrBlank()) return true // fallback permissivo quando omitido
        val mask = decimalBitmask.toBigIntegerOrNull() ?: return false
        if (mask.and(ADMINISTRATOR) != BigInteger.ZERO) return true
        return mask.and(bit) != BigInteger.ZERO
    }
}

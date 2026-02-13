package net.lasertag.model

import java.io.Serializable

abstract class WirelessMessage(
    open val type: Byte
): Serializable {
    open fun getBytes(): ByteArray {
        return byteArrayOf(type)
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WirelessMessage
        return type == other.type
    }

    override fun hashCode(): Int {
        return type.toInt()
    }
}

data class SignalMessage (
    override val type: Byte = Messaging.PING
): WirelessMessage(type)

data class TimeMessage (
    override val type: Byte,
    val minutes: Byte,
    val seconds: Byte
): WirelessMessage(type)

data class EventMessageIn (
    override val type: Byte,
    val payload: Byte
): WirelessMessage(type)

data class MockEventMessageFromDevice (
    override val type: Byte,
    val mockContent: ByteArray,
): WirelessMessage(type)

data class GameStartMessageIn (
    override val type: Byte,
    val teamPlay: Boolean,
    val gameTimeMinutes: Int
): WirelessMessage(type)

data class StatsMessageIn (
    override val type: Byte,
    val isGameRunning: Boolean,
    val isTeamPlay: Boolean,
    val gameTimerSeconds: Short,
    var players: Array<Player>
) : WirelessMessage(type)

data class EventMessageToServer (
    override val type: Byte,
    val playerId: Byte,
    val extraValue: Byte,
    val health: Byte
): WirelessMessage(type) {
    override fun getBytes(): ByteArray {
        return byteArrayOf(type, playerId, extraValue, health)
    }
    constructor(
        type: Byte,
        player: Player,
        extraValue: Int
    ): this(type, player.id.toByte(), extraValue.toByte(), player.health.toByte())
}

data class MessageToDevice (
    override val type: Byte,
    val playerId: Byte,
    val playerTeam: Byte,
    val playerState: Byte,
    val bulletsLeft: Byte
): WirelessMessage(type) {
    override fun getBytes(): ByteArray {
        return byteArrayOf(type, playerId, playerTeam, playerState, bulletsLeft)
    }
}

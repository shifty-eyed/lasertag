package net.lasertag.model

import net.lasertag.Config
import java.io.Serializable

data class Player(
    var id: Int,
    var health: Int,
    var score: Int,
    var teamId: Int,
    var damage: Int,
    var bulletsInMagazine: Int,
    var bulletsTotal: Int,
    var bulletsMax: Int,
    var assignedRespawnPoint: Int,
    var flagCarrier: Boolean,
    var name: String
) : Serializable, Comparable<Player> {
    constructor(id: Int) : this(id, 100, 0, 0, 0, 0, 0, 0, 0, false,"NoName")

    fun isAlive(): Boolean {
        return health > 0
    }

    fun decreaseHealth(damage: Int) {
        health -= damage
        if (health < 0) {
            health = 0
        }
    }

    fun increaseHealth(amount: Int) {
        health += amount
        if (health > Config.MAX_HEALTH) {
            health = Config.MAX_HEALTH
        }
    }

    fun decreaseBullets() {
        bulletsInMagazine--
        if (bulletsInMagazine < 0) {
            bulletsInMagazine = 0
        }
    }

    fun increaseBullets(amount: Int) {
        bulletsTotal += amount
        if (bulletsTotal > bulletsMax) {
            bulletsTotal = bulletsMax
        }
    }

    fun respawn() {
        assignedRespawnPoint = -1
        health = 50//Config.MAX_HEALTH
        bulletsInMagazine = Config.MAGAZINE_SIZE
        bulletsTotal = bulletsMax
    }

    fun reload(): Boolean {
        if (bulletsTotal <= 0 || bulletsInMagazine >= Config.MAGAZINE_SIZE) {
            return false
        }
        val bulletsToReload = Math.min(Config.MAGAZINE_SIZE - bulletsInMagazine, bulletsTotal)
        bulletsTotal -= bulletsToReload
        bulletsInMagazine += bulletsToReload
        return true
    }

    fun copyPlayerValuesFrom(player: Player) {
        health = player.health
        score = player.score
        teamId = player.teamId
        damage = player.damage
        bulletsMax = player.bulletsMax
        assignedRespawnPoint = player.assignedRespawnPoint

        if (player.name.isNotEmpty()) {
            name = player.name
        }
    }

    override fun compareTo(other: Player): Int {
        return other.score - score
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Player
        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }
}
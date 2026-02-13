package net.lasertag.lasertagserver.model;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Player extends Actor {

	@Setter
	private String name;
	@Setter
	private int health;
	@Setter
	private int score;
	@Setter
	private int teamId;
	@Setter
	private int damage;
	@Setter
	private int bulletsMax;
	@Setter
	private int assignedRespawnPoint;
	@Setter
	private boolean flagCarrier;

	private int maxHealth;

	public Player(int id,  String name, int maxHealth) {
		super(id, Type.PLAYER);
		this.maxHealth = maxHealth;
		this.name = name;
		this.health = maxHealth;
		this.score = 0;
		this.teamId = Messaging.TEAM_YELLOW;
		this.damage = 10;
		this.bulletsMax = 40;
		this.assignedRespawnPoint = -1;
		this.flagCarrier = false;
	}

	public boolean updateHealth(int health) {
		if (this.health == health) {
			return false; // no change
		}
		this.health = health;
		return true;
	}

}

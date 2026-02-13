package net.lasertag.lasertagserver.model;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Dispenser extends Actor {

	public static final int DEFAULT_DISPENSE_TIMEOUT = 60;
	public static final int DEFAULT_AMOUNT = 40;

	@Setter
	private int amount;

	@Setter
	private int dispenseTimeoutSec;

	public Dispenser(int id, Actor.Type type) {
		super(id, type);
		this.amount = DEFAULT_AMOUNT;
		this.dispenseTimeoutSec = DEFAULT_DISPENSE_TIMEOUT;
	}


}

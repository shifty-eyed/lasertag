package net.lasertag.lasertagserver.model;

import lombok.Getter;
import lombok.Setter;

import java.net.InetAddress;
import java.util.Objects;

@Getter
public abstract class Actor {

	public enum Type {
		PLAYER,
		HEALTH,
		AMMO
	}

	private final int id;
	private final Type type;

	@Setter
	private InetAddress clientIp;

	public Actor(int id, Type type) {
		this.id = id;
		this.type = type;
	}

	public boolean isOnline() {
		return getClientIp() != null;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		Actor actor = (Actor) o;
		return getId() == actor.getId() && getType() == actor.getType();
	}

	@Override
	public int hashCode() {
		return Objects.hash(getId(), getType());
	}

	@Override
	public String toString() {
		return "Actor[" + type.name() + "-" + id + "]";
	}
}

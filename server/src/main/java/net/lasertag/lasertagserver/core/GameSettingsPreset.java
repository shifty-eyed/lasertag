package net.lasertag.lasertagserver.core;

import lombok.Data;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Messaging;

import java.util.HashMap;
import java.util.Map;

@Data
public class GameSettingsPreset {

    @Data
	public static class PlayerSettings {
		private String name;
		private Integer bulletsMax;
		private Integer damage;
		private Integer teamId;

		public PlayerSettings() {}

		public PlayerSettings(String name, Integer bulletsMax, Integer damage, Integer teamId) {
			this.name = name;
			this.bulletsMax = bulletsMax;
			this.damage = damage;
			this.teamId = teamId;
		}
	}

	@Data
	public static class DispenserSettings {
		private int timeout;
		private int amount;

		public DispenserSettings() {}

		public DispenserSettings(int timeout, int amount) {
			this.timeout = timeout;
			this.amount = amount;
		}
	}

	private int fragLimit = 10;
	private GameType gameType = GameType.DM;
	private int timeLimitMinutes = 15;

	public boolean isTeamPlay() {
		return gameType.isTeamBased();
	}

	private Map<Integer, PlayerSettings> playerSettings = new HashMap<>();

	private DispenserSettings healthDispenserSettings = new DispenserSettings(60, 40);
	private DispenserSettings ammoDispenserSettings = new DispenserSettings(60, 40);

	public GameSettingsPreset() {
		initDefaults();
	}

	public void initDefaults() {
		for (int i = 0; i < ActorRegistry.PLAYER_COUNT; i++) {
			PlayerSettings settings = new PlayerSettings();
			settings.setName("Player-%d".formatted(i));
			settings.setDamage(10);
			settings.setBulletsMax(40);
			settings.setTeamId(Messaging.TEAM_YELLOW);
			playerSettings.put(i, settings);
		}
	}

	public Map<String, Object> getAllSettings() {
		Map<String, Object> allSettings = new HashMap<>();
        Map<String, Object> general = new HashMap<>();
		general.put("fragLimit", fragLimit);
		general.put("gameType", gameType.name());
		general.put("timeLimitMinutes", timeLimitMinutes);
		allSettings.put("general", general);
		allSettings.put("players", getAllPlayerSettings());
		allSettings.put("dispensers", Map.of(
			"health", Map.of(
				"timeout", healthDispenserSettings.getTimeout(),
				"amount", healthDispenserSettings.getAmount()
			),
			"ammo", Map.of(
				"timeout", ammoDispenserSettings.getTimeout(),
				"amount", ammoDispenserSettings.getAmount()
			)
		));
		return allSettings;
	}

	// Player settings methods
	public PlayerSettings getPlayerSettings(int playerId) {
		return playerSettings.get(playerId);
	}

	public Map<Integer, PlayerSettings> getAllPlayerSettings() {
		return new HashMap<>(playerSettings);
	}

	public void setPlayerSettings(int playerId, PlayerSettings newSettings) {
		if (newSettings == null) {
			throw new IllegalArgumentException("Player settings cannot be null");
		}

		PlayerSettings storedSettings = playerSettings.computeIfAbsent(playerId, id -> new PlayerSettings());
		storedSettings.setName(newSettings.getName());
		storedSettings.setBulletsMax(newSettings.getBulletsMax());
		storedSettings.setDamage(newSettings.getDamage());
		storedSettings.setTeamId(newSettings.getTeamId());
	}

	// Dispenser settings methods
	public DispenserSettings getDispenserSettings(Actor.Type type) {
		if (type == Actor.Type.HEALTH) {
			return healthDispenserSettings;
		} else if (type == Actor.Type.AMMO) {
			return ammoDispenserSettings;
		}
		throw new IllegalArgumentException("Unknown dispenser type: " + type);
	}

	public void setDispenserTimeout(Actor.Type type, int timeout) {
		getDispenserSettings(type).setTimeout(timeout);
	}

	public void setDispenserAmount(Actor.Type type, int amount) {
		getDispenserSettings(type).setAmount(amount);
	}

}

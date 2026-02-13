package net.lasertag.lasertagserver.core;

import lombok.Getter;
import net.lasertag.lasertagserver.model.*;
import net.lasertag.lasertagserver.web.SseEventService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Getter
public class Game implements GameEventsListener {
	private static final Logger log = LoggerFactory.getLogger(Game.class);

	public static final int MAX_HEALTH = 100;

	private final ActorRegistry actorRegistry;
	private final UdpServer udpServer;
	private final SseEventService sseEventService;
	private final GameSettings gameSettings;
	private final ScheduledExecutorService scheduler =
		Executors.newScheduledThreadPool(2, new DaemonThreadFactory("DaemonScheduler"));

		
	private volatile boolean isGamePlaying = false;
	private int timeLeftSeconds = 0;

	public Game(ActorRegistry actorRegistry, UdpServer udpServer, 
				SseEventService sseEventService, GameSettings gameSettings) {
		this.actorRegistry = actorRegistry;
		this.udpServer = udpServer;
		this.sseEventService = sseEventService;
		this.gameSettings = gameSettings;
		udpServer.setGameEventsListener(this);
		
	}

	@Override
	public void onMessageFromPlayer(Player player, Messaging.MessageFromClient message) {
		player.updateHealth(message.getHealth());
		
		var type = message.getTypeId();
		if (type == MessageType.GOT_HIT.id() || type == MessageType.YOU_KILLED.id())  {
			var hitByPlayer = actorRegistry.getPlayerById(message.getExtraValue());
			if (type == MessageType.YOU_KILLED.id()) {
				onPlayerKilled(player, hitByPlayer);
			} else {
				udpServer.sendEventToClient(MessageType.YOU_HIT_SOMEONE, hitByPlayer, (byte)player.getId());
			}
		} else if (type == MessageType.GOT_HEALTH.id()) {
			useDispenser(player, Actor.Type.HEALTH, message.getExtraValue(), MessageType.GIVE_HEALTH_TO_PLAYER);
		} else if (type == MessageType.GOT_AMMO.id()) {
			useDispenser(player, Actor.Type.AMMO, message.getExtraValue(), MessageType.GIVE_AMMO_TO_PLAYER);
		} else if (type == MessageType.FLAG_TAKEN.id()) {
			player.setFlagCarrier(true);
			broadcastFlagEvent(MessageType.FLAG_TAKEN, player);
		} else if (type == MessageType.FLAG_CAPTURED.id()) {
			actorRegistry.incrementTeamScore(player.getTeamId());
			player.setFlagCarrier(false);
			broadcastFlagEvent(MessageType.FLAG_CAPTURED, player);

			var teamScore = actorRegistry.getTeamScores().get(player.getTeamId());
			if (teamScore >= getSettings().getFragLimit()) {
				eventConsoleEndGame();
			}
		}

		if (type != MessageType.GOT_AMMO.id() && type != MessageType.GOT_HEALTH.id()) {
			sendPlayerValuesSnapshotToAll(false);
		}
		refreshConsoleUI(isGamePlaying);
	}

	private void onPlayerKilled(Player player, Player hitByPlayer) {
		hitByPlayer.setScore(hitByPlayer.getScore() + 1);
		if (getGameType() == GameType.TEAM_DM) {
			actorRegistry.incrementTeamScore(hitByPlayer.getTeamId());
		}
		udpServer.sendEventToClient(MessageType.YOU_SCORED, hitByPlayer, (byte)player.getId());
		player.setAssignedRespawnPoint(actorRegistry.getRandomRespawnPointId());

		// CTF flag carrier killed, drop the flag
		if (getGameType() == GameType.CTF && player.isFlagCarrier()) {
			player.setFlagCarrier(false);
			broadcastFlagEvent(MessageType.FLAG_LOST, player);
		}

		var vitalScore = isTeamPlay() ? actorRegistry.getTeamScores().get(hitByPlayer.getTeamId()) : hitByPlayer.getScore();
		if (vitalScore >= getSettings().getFragLimit()) {
			eventConsoleEndGame();
		}
	}

	private void useDispenser(Player player, Actor.Type dispenserType, int dispenserId, MessageType messageToPlayerType) {
		var dispenser = (Dispenser) actorRegistry.getActorByTypeAndId(dispenserType, dispenserId);
		udpServer.sendEventToClient(MessageType.DISPENSER_USED, dispenser);
		udpServer.sendEventToClient(messageToPlayerType, player, (byte)dispenser.getAmount());
	}

	@Override
	public void eventConsoleStartGame(int timeMinutes, int fragLimit, GameType gameType) {
		log.info("Starting game with timeLimitMinutes={}, fragLimit={}, gameType={}", timeMinutes, fragLimit, gameType);
		getSettings().setTimeLimitMinutes(timeMinutes);
		getSettings().setFragLimit(fragLimit);
		getSettings().setGameType(gameType);
		timeLeftSeconds = getSettings().getTimeLimitMinutes() * 60;

		actorRegistry.resetTeamScores();
		var respawnPointsIt = actorRegistry.shuffledRespawnPointIds().iterator();
		actorRegistry.streamPlayers().forEach(player -> {
			player.setScore(0);
			player.setHealth(0);
			player.setFlagCarrier(false);
			player.setAssignedRespawnPoint(respawnPointsIt.next());
		});

		setIsGamePlaying(true);
		sendPlayerValuesSnapshotToAll(true);
		actorRegistry.streamPlayers().forEach(player -> {
			if (player.isOnline()) {
				udpServer.sendEventToClient(MessageType.GAME_START, player, (byte) getGameType().ordinal(), (byte) getSettings().getTimeLimitMinutes());
			}
		});
		
	}

	@Override
	public void eventConsoleEndGame() {
		
		log.info("Ending game");
		setIsGamePlaying(false);

		Player leadPlayer = actorRegistry.getLeadPlayer();
		int leadTeam = actorRegistry.getLeadTeam();
		int winner = isTeamPlay() ? leadTeam : Optional.ofNullable(leadPlayer).map(Player::getId).orElse(-1);
		scheduler.schedule(() -> {
			for (Player player : actorRegistry.getPlayers()) {
				udpServer.sendEventToClient(MessageType.GAME_OVER, player, (byte)winner);
			}
		}, 1, TimeUnit.SECONDS);
	}

	@Override
	public void refreshConsoleTable() {
		refreshConsoleUI(isGamePlaying);
	}

	@Override
	public void onPlayerJoinedOrLeft() {
		sendPlayerValuesSnapshotToAll(true);
	}

	@Override
	public void onPlayerDataUpdated(Player player, boolean isNameUpdated) {
		sendPlayerValuesSnapshotToAll(isNameUpdated);
	}

	@Scheduled(fixedDelay = 1000, initialDelay = 1000)
	public void updateGameTime() {
		if (isGamePlaying) {
			timeLeftSeconds--;
			if (timeLeftSeconds <= 0) {
				eventConsoleEndGame();
				return;
			}
			updateConsoleGameTime(timeLeftSeconds);
		}
	}

	private void setIsGamePlaying(boolean newState) {
		if (isGamePlaying != newState) {
			isGamePlaying = newState;
			refreshConsoleUI(newState);
		}
	}

	private void sendPlayerValuesSnapshotToAll(boolean includeNames) {
		udpServer.sendStatsToAll(includeNames, isGamePlaying, getGameType().ordinal(), timeLeftSeconds);
	}


	private void broadcastFlagEvent(MessageType flagEventType, Player player) {
		for (Player toPlayer : actorRegistry.getPlayers()) {
			if (toPlayer.isOnline()) {
				udpServer.sendEventToClient(flagEventType, toPlayer, (byte) player.getId());
			}
		}
	}

	private void refreshConsoleUI(boolean isPlaying) {
		sseEventService.refreshUI(isPlaying);
	}

	private void updateConsoleGameTime(int timeLeft) {
		sseEventService.sendGameTimeLeft(timeLeft);
	}

	private GameType getGameType() {
		return gameSettings.getCurrent().getGameType();
	}

	private boolean isTeamPlay() {
		return gameSettings.getCurrent().getGameType().isTeamBased();
	}

	private GameSettingsPreset getSettings() {
		return gameSettings.getCurrent();
	}

}

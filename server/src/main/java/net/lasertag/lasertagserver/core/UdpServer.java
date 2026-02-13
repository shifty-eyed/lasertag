package net.lasertag.lasertagserver.core;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import net.lasertag.lasertagserver.LanIpUtils;
import net.lasertag.lasertagserver.model.*;

import static net.lasertag.lasertagserver.model.Messaging.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.stream.Stream;

@Component
public class UdpServer {
	private static final Logger log = LoggerFactory.getLogger(UdpServer.class);

	@Setter
	private GameEventsListener gameEventsListener;

	private final ActorRegistry actorRegistry;
	private final GameSettings gameSettings;
	private final Map<Actor, Long> lastPingTime;

	private final long pingTimeout = 10000;

	private volatile boolean running = true;
	private final int port;
	private final int devicePort;

	private final ThreadPoolTaskExecutor daemonExecutor;

	public UdpServer(ActorRegistry actorRegistry, GameSettings gameSettings, ThreadPoolTaskExecutor daemonExecutor) {
		this.port = 9878;
		this.devicePort = 1234;
		this.actorRegistry = actorRegistry;
		this.gameSettings = gameSettings;
		this.daemonExecutor = daemonExecutor;
		this.lastPingTime = new HashMap<>();
	}

	@org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
	public void logLanIp() {
		String webConsoleUrl = "http://" + LanIpUtils.findLanIp().orElse("<unknown>") + ":8080";
		log.info("Web console is available at: {}", webConsoleUrl);
	}

	@PostConstruct
	public void init() {
		daemonExecutor.execute(this::startUdpServer);
		Runtime.getRuntime().addShutdownHook(new Thread(this::stopUdpServer));
		
		
	}

	private void startUdpServer() {
		try (DatagramSocket serverSocket = new DatagramSocket(port)) {
			serverSocket.setSoTimeout(1000);
			log.info("Game Server started on port: {} thread: {}", port, Thread.currentThread().getName());
			byte[] receiveBuffer = new byte[64];
			while (running) {
				DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
				try {
					serverSocket.receive(receivePacket);
					processPacketFromClient(receivePacket);
				} catch (SocketTimeoutException ignored) {}
			}
			log.info("Game Server stopped");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendAckToClient(InetAddress ip) {
		try (DatagramSocket clientSocket = new DatagramSocket()) {
			byte[] sendData = new byte[] {MessageType.PING.id()};
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, devicePort);
			clientSocket.send(sendPacket);
		} catch (Exception e) {
			log.error("Error sending command to client: {}", e.getMessage(), e);
		}
	}

	private void sendBytesToClient(InetAddress ip, byte[] bytes) {
		if (ip == null) {
			return;
		}
		try (DatagramSocket clientSocket = new DatagramSocket()) {
			DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, ip, devicePort);
			clientSocket.send(sendPacket);
			log.debug("Bytes to {}:{} len={}, data: {}", sendPacket.getAddress(), sendPacket.getPort(), sendPacket.getLength(), Arrays.toString(sendPacket.getData()));
		} catch (Exception e) {
			log.error("Error sending command to client: {}", e.getMessage(), e);
		}
	}

	public void stopUdpServer() {
		running = false;
		daemonExecutor.shutdown();
		log.info("{}: Stopping UDP Server...", this.getClass().getSimpleName());
	}

	private void processPacketFromClient(DatagramPacket packet) {
		try {
			var message = new MessageFromClient(packet.getData(), packet.getLength());
			var actor = actorRegistry.getActorByMessage(message);
			if (actor.getClientIp() == null || message.isFirstEverMessage()) {
				actor.setClientIp(packet.getAddress());
				log.info("Connected {} ip = {} ", actor, actor.getClientIp());
				gameEventsListener.refreshConsoleTable();
				if (actor.getType() == Actor.Type.PLAYER) {
					gameEventsListener.onPlayerJoinedOrLeft();
				}
				if (actor.getType() == Actor.Type.HEALTH || actor.getType() == Actor.Type.AMMO) {
					sendSettingsToAllDispensers();
				}
			}
			lastPingTime.put(actor, System.currentTimeMillis());

			if (PING_GROUP.contains(message.getTypeId())) {
				sendAckToClient(actor.getClientIp());
			} else {
				log.info("Event {} from {} len={}, data: {}", message.getType().name(), actor, packet.getLength(), message);
				gameEventsListener.onMessageFromPlayer((Player)actor, message);
			}
		} catch (Exception e) {
			log.error("Error parsing message from {}: {}", packet.getAddress().getHostAddress(), e.getMessage(), e);
		}
	}

	@Scheduled(fixedDelay = 1000)
	private void checkConnectedClients() {
		var currentTime = System.currentTimeMillis();
		actorRegistry.getActors().forEach(actor -> {
			var lastPing = lastPingTime.getOrDefault(actor, 0L);
			if (currentTime - lastPing > pingTimeout) {
				if (actor.getClientIp() != null) {
					log.warn("Lost connection to {}", actor);
					actor.setClientIp(null);
					if (gameEventsListener != null) {
						gameEventsListener.refreshConsoleTable();
						if (actor.getType() == Actor.Type.PLAYER) {
							gameEventsListener.onPlayerJoinedOrLeft();
						}
					}
				}
			}
		});
	}

	public void sendEventToClient(MessageType type, Actor actor, byte... values) {
		log.info("Event to {}: type={}, data: {}", actor.toString(), type.name(), Arrays.toString(values));
		var bytes = Messaging.eventToBytes(type.id(), values);
		sendBytesToClient(actor.getClientIp(), bytes);
	}

	public void sendStatsToAll(boolean includeNames, boolean isGameRunning, int gameTypeOrdinal, int timeSeconds) {
		var players = actorRegistry.getPlayersSortedByScore();
		var onlinePlayers = players.stream().filter(Player::isOnline).toList();
		log.info("Stats to players: {}, withNames={}, isGameRunning={}, gameType={}, timeSeconds={}",
			Arrays.toString(onlinePlayers.stream().map(p -> p.getId()).toArray()), includeNames, isGameRunning, gameTypeOrdinal, timeSeconds);
		var bytes = Messaging.playerStatsToBytes(includeNames, players, isGameRunning, gameTypeOrdinal, timeSeconds);
		onlinePlayers.forEach(player -> sendBytesToClient(player.getClientIp(), bytes));
	}

	public void sendSettingsToAllDispensers() {
		Stream.concat(actorRegistry.streamByType(Actor.Type.AMMO), actorRegistry.streamByType(Actor.Type.HEALTH))
		.filter(actor -> actor.isOnline())
		.forEach(actor -> {
			int timeout = gameSettings.getCurrent().getDispenserSettings(actor.getType()).getTimeout();
			sendEventToClient(MessageType.DISPENSER_SET_TIMEOUT, actor, (byte)(timeout / 10));// to pack as 1 byte
		});
	}

}

package net.lasertag.lasertagserver.web;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.lasertag.lasertagserver.core.ActorRegistry;
import net.lasertag.lasertagserver.core.Game;
import net.lasertag.lasertagserver.core.GameEventsListener;
import net.lasertag.lasertagserver.core.GameSettingsPreset;
import net.lasertag.lasertagserver.core.GameSettings;
import net.lasertag.lasertagserver.core.GameType;
import net.lasertag.lasertagserver.core.UdpServer;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.MessageType;
import net.lasertag.lasertagserver.model.Player;
import net.lasertag.lasertagserver.model.RespawnPointColor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api")
@Slf4j
public class GameController {

	private final ActorRegistry actorRegistry;
	private final GameEventsListener gameEventsListener;
	private final UdpServer udpServer;
	private final SseEventService sseEventService;
	private final GameSettings gameSettings;
	private final Game game;

	public GameController(ActorRegistry actorRegistry, GameEventsListener gameEventsListener, 
						  SseEventService sseEventService, GameSettings gameSettings, UdpServer udpServer, Game game) {
		this.actorRegistry = actorRegistry;
		this.gameEventsListener = gameEventsListener;
		this.sseEventService = sseEventService;
		this.gameSettings = gameSettings;
		this.udpServer = udpServer;
		this.game = game;
	}

	@GetMapping("/events")
	public SseEmitter initEventStreaming() {
		SseEmitter emitter = sseEventService.createEmitter();
		
		try {
			sseEventService.sendPlayersUpdate(actorRegistry.getPlayers());
			sseEventService.sendDispensersUpdate(
				actorRegistry.getDispensersForUi(game.isGamePlaying(), gameSettings.getCurrent().getGameType())
			);
			sseEventService.sendSettingsUpdate(gameSettings.getAllSettingsWithMetadata());
		} catch (Exception e) {}
		
		return emitter;
	}

	@GetMapping("/game/snapshot")
	public GameSnapshotResponse getGameSnapshot() {
		GameStateResponse gameState = new GameStateResponse(
			game.isGamePlaying(),
			game.getTimeLeftSeconds(),
			actorRegistry.getTeamScores()
		);

		return new GameSnapshotResponse(
			gameState,
			actorRegistry.getPlayers(),
			actorRegistry.getDispensersForUi(game.isGamePlaying(), gameSettings.getCurrent().getGameType()),
			gameSettings.getAllSettingsWithMetadata()
		);
	}

	@PostMapping("/game/start")
	public ResponseEntity<Map<String, String>> startGame(@RequestBody GeneralSettingsRequest request) {
		applyGeneralSettings(request);
		gameEventsListener.eventConsoleStartGame(
			request.getTimeLimit(),
			request.getFragLimit(),
			GameType.valueOf(request.getGameType())
		);
		return ResponseEntity.ok(Map.of("status", "Game started"));
	}

	@PutMapping("/settings/general")
	public ResponseEntity<Map<String, String>> updateGeneralSettings(@RequestBody GeneralSettingsRequest request) {
		applyGeneralSettings(request);
		return ResponseEntity.ok(Map.of("status", "Settings updated"));
	}

	private void applyGeneralSettings(GeneralSettingsRequest request) {
		gameSettings.getCurrent().setTimeLimitMinutes(request.getTimeLimit());
		gameSettings.getCurrent().setFragLimit(request.getFragLimit());
		gameSettings.getCurrent().setGameType(GameType.valueOf(request.getGameType()));
		gameSettings.syncToActors();
	}

	@PostMapping("/game/end")
	public ResponseEntity<Map<String, String>> endGame() {
		gameEventsListener.eventConsoleEndGame();
		return ResponseEntity.ok(Map.of("status", "Game ended"));
	}

	@PutMapping("/players/{id}")
	public ResponseEntity<Player> updatePlayer(@PathVariable int id, @RequestBody GameSettingsPreset.PlayerSettings request) {
		GameSettingsPreset.PlayerSettings existingSettings = gameSettings.getCurrent().getPlayerSettings(id);
		boolean nameUpdated = existingSettings != null && !Objects.equals(existingSettings.getName(), request.getName());

		gameSettings.getCurrent().setPlayerSettings(id, request);
		gameSettings.syncToActors();

		Player player = actorRegistry.getPlayerById(id);
		gameEventsListener.onPlayerDataUpdated(player, nameUpdated);
		
		return ResponseEntity.ok(player);
	}

	@PostMapping("/players/{id}/devevent")
	public ResponseEntity<Map<String, String>> sendDevEvent(
		@PathVariable int id,
		@RequestParam int type,
		@RequestParam int payload
	) {
		Player player = actorRegistry.getPlayerById(id);
		if (player == null || !player.isOnline()) {
			return ResponseEntity.badRequest().body(Map.of("error", "Player not found or offline"));
		}
		udpServer.sendEventToClient(MessageType.MOCK_DEVICE_EVENT, player, (byte) type, (byte) payload);
		return ResponseEntity.ok(Map.of("status", "Mock device event sent"));
	}

	@PutMapping("/settings/respawn-points")
	public ResponseEntity<Map<String, String>> updateRespawnPoints(@RequestBody UpdateRespawnPointsRequest request) {
		gameSettings.getCurrent().setRespawnPoints(request.getColors());
		sseEventService.sendSettingsUpdate(gameSettings.getAllSettingsWithMetadata());
		return ResponseEntity.ok(Map.of("status", "Respawn points updated"));
	}

	@PutMapping("/dispensers/{type}")
	public ResponseEntity<Map<String, String>> updateDispensers(
		@PathVariable String type, 
		@RequestBody UpdateDispenserRequest request
	) {
		Actor.Type dispenserType = Actor.Type.valueOf(type);
		gameSettings.getCurrent().setDispenserTimeout(dispenserType, request.getTimeout());
		gameSettings.getCurrent().setDispenserAmount(dispenserType, request.getAmount());
		
		gameSettings.syncToActors();
		udpServer.sendSettingsToAllDispensers();
		
		return ResponseEntity.ok(Map.of("status", "Dispensers updated"));
	}

	@GetMapping("/presets")
	public List<String> listPresets() throws IOException {
		return gameSettings.listPresets();
	}

	@PostMapping("/presets/{name}")
	public ResponseEntity<Map<String, String>> savePreset(@PathVariable String name) throws IOException {
		gameSettings.savePreset(name);
		sseEventService.sendSettingsUpdate(gameSettings.getAllSettingsWithMetadata());
		return ResponseEntity.ok(Map.of("status", "Preset saved"));
	}

	@PostMapping("/presets/{name}/load")
	public ResponseEntity<Map<String, String>> loadPreset(@PathVariable String name) throws IOException {
		gameSettings.loadPreset(name);
		sseEventService.sendSettingsUpdate(gameSettings.getAllSettingsWithMetadata());
		sseEventService.sendPlayersUpdate(actorRegistry.getPlayers());
		sseEventService.sendDispensersUpdate(
			actorRegistry.getDispensersForUi(game.isGamePlaying(), gameSettings.getCurrent().getGameType())
		);
		return ResponseEntity.ok(Map.of("status", "Preset loaded"));
	}

	@ExceptionHandler(IOException.class)
	public void handleIOException(IOException e) {
		log.warn("Client disconnected: {}", e.getMessage());
	}

	@Getter
	@Setter
	public static class GeneralSettingsRequest {
		private int timeLimit;
		private int fragLimit;
		private String gameType;
	}

	@Getter
	@Setter
	public static class UpdateDispenserRequest {
		private Integer timeout;
		private Integer amount;
	}

	@Getter
	@Setter
	public static class UpdateRespawnPointsRequest {
		private List<RespawnPointColor> colors;
	}

	public record GameSnapshotResponse(
		GameStateResponse gameState,
		List<Player> players,
		Map<String, Object> dispensers,
		Map<String, Object> settings
	) {}

	public record GameStateResponse(
		boolean playing,
		int timeLeftSeconds,
		Map<Integer, Integer> teamScores
	) {}

}


package net.lasertag.lasertagserver.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import net.lasertag.lasertagserver.model.Messaging;
import net.lasertag.lasertagserver.model.Player;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

@Component
@Slf4j
public class GameSettings {

    private static final String PRESETS_DIR = "presets";
    private static final String JSON_EXTENSION = ".json";
    private static final String STATE_FILE = "server-state.json";
    private static final String NEW_PRESET_NAME = "New...";

    private final ObjectMapper objectMapper;
    private final ActorRegistry actorRegistry;
    private final Random random = new Random();

    @Getter
    private GameSettingsPreset current;

    @Getter
    private String currentPresetName = NEW_PRESET_NAME;

    public Map<String, Object> getAllSettingsWithMetadata() {
        Map<String, Object> settings = new HashMap<>(current.getAllSettings());
        settings.put("presetName", currentPresetName);
        return settings;
    }

    public GameSettings(ActorRegistry actorRegistry) {
        this.actorRegistry = actorRegistry;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.current = new GameSettingsPreset();
    }

    @PostConstruct
    public void init() {
        loadState();
    }

    public void savePreset(String fileName) throws IOException {
        Path presetsPath = Paths.get(PRESETS_DIR);
        if (!Files.exists(presetsPath)) {
            Files.createDirectories(presetsPath);
            log.info("Created presets directory: {}", presetsPath.toAbsolutePath());
        }

        String normalizedFileName = normalizeFileName(fileName);
        Path filePath = presetsPath.resolve(normalizedFileName);
        objectMapper.writeValue(filePath.toFile(), current);
        log.info("Saved preset to: {}", filePath.toAbsolutePath());

        currentPresetName = fileName;
        saveState();
    }

    public void loadPreset(String fileName) throws IOException {
        String normalizedFileName = normalizeFileName(fileName);
        Path filePath = Paths.get(PRESETS_DIR).resolve(normalizedFileName);

        if (!Files.exists(filePath)) {
            throw new IOException("Preset file not found: " + filePath.toAbsolutePath());
        }

        current = objectMapper.readValue(filePath.toFile(), GameSettingsPreset.class);
        log.info("Loaded preset from: {}", filePath.toAbsolutePath());

        currentPresetName = fileName;
        saveState();
        syncToActors();
    }

    public List<String> listPresets() throws IOException {
        Path presetsPath = Paths.get(PRESETS_DIR);
        if (!Files.exists(presetsPath)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(presetsPath)) {
            return files
                .filter(path -> path.toString().endsWith(JSON_EXTENSION))
                .map(path -> path.getFileName().toString().replace(JSON_EXTENSION, ""))
                .toList();
        }
    }

    private String normalizeFileName(String fileName) {
        if (!fileName.endsWith(JSON_EXTENSION)) {
            return fileName + JSON_EXTENSION;
        }
        return fileName;
    }

    private void saveState() {
        try {
            Path statePath = Paths.get(STATE_FILE);
            objectMapper.writeValue(statePath.toFile(), Map.of("currentPresetName", currentPresetName));
            log.info("Saved server state to: {}", statePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to save server state: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadState() {
        Path statePath = Paths.get(STATE_FILE);
        if (!Files.exists(statePath)) {
            log.info("No server state file found, using defaults");
            return;
        }

        try {
            Map<String, Object> state = objectMapper.readValue(statePath.toFile(), Map.class);
            String presetName = (String) state.get("currentPresetName");
            if (presetName != null && !presetName.equals(NEW_PRESET_NAME)) {
                loadPreset(presetName);
                log.info("Restored preset '{}' from server state", presetName);
            }
        } catch (IOException e) {
            log.warn("Failed to load server state: {}", e.getMessage());
        }
    }

    public void assignInitialRespawnPoints() {
        boolean teamPlay = current.getGameType().isTeamBased();
        if (teamPlay) {
            Map<Integer, Iterator<Integer>> teamQueues = new HashMap<>();
            for (int teamId : new int[] { Messaging.TEAM_RED, Messaging.TEAM_BLUE }) {
                long count = actorRegistry.streamPlayers().filter(p -> p.getTeamId() == teamId).count();
                List<Integer> shuffled = new ArrayList<>(candidatePoints(teamId, true));
                Collections.shuffle(shuffled);
                teamQueues.put(teamId, cycleToFit(shuffled, count).iterator());
            }
            actorRegistry.streamPlayers().forEach(player -> {
                Iterator<Integer> queue = teamQueues.get(player.getTeamId());
                if (queue != null && queue.hasNext()) {
                    player.setAssignedRespawnPoint(queue.next());
                } else {
                    log.warn("No respawn point candidates for player {} (team {})", player.getId(), player.getTeamId());
                }
            });
        } else {
            List<Integer> pool = new ArrayList<>(candidatePoints(0, false));
            Collections.shuffle(pool);
            long count = actorRegistry.streamPlayers().count();
            Iterator<Integer> it = cycleToFit(pool, count).iterator();
            actorRegistry.streamPlayers().forEach(player -> {
                if (it.hasNext()) {
                    player.setAssignedRespawnPoint(it.next());
                } else {
                    log.warn("No respawn point candidates for player {}", player.getId());
                }
            });
        }
    }

    public void assignRespawnPoint(Player player) {
        boolean teamPlay = current.getGameType().isTeamBased();
        List<Integer> pool = candidatePoints(player.getTeamId(), teamPlay);
        if (pool.isEmpty()) {
            log.warn("No respawn point candidates for player {} (team {})", player.getId(), player.getTeamId());
            return;
        }
        player.setAssignedRespawnPoint(pool.get(random.nextInt(pool.size())));
    }

    private List<Integer> candidatePoints(int teamId, boolean teamPlay) {
        List<RespawnPointColor> colors = current.getRespawnPoints();
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < ActorRegistry.RESPAWN_POINT_COUNT; i++) {
            RespawnPointColor color = i < colors.size() ? colors.get(i) : RespawnPointColor.ANY;
            if (color == RespawnPointColor.OFF) {
                continue;
            }
            if (!teamPlay
                || color == RespawnPointColor.ANY
                || (color == RespawnPointColor.RED && teamId == Messaging.TEAM_RED)
                || (color == RespawnPointColor.BLUE && teamId == Messaging.TEAM_BLUE)) {
                result.add(i);
            }
        }
        return result;
    }

    private List<Integer> cycleToFit(List<Integer> base, long count) {
        if (base.isEmpty() || count <= base.size()) {
            return base;
        }
        List<Integer> result = new ArrayList<>(base);
        long extra = count - base.size();
        for (int i = 0; i < extra; i++) {
            result.add(base.get(i % base.size()));
        }
        return result;
    }

    public void syncToActors() {
        actorRegistry.streamPlayers().forEach(player -> {
            GameSettingsPreset.PlayerSettings settings = current.getPlayerSettings(player.getId());
            player.setName(settings.getName());
            player.setBulletsMax(settings.getBulletsMax());
            player.setDamage(settings.getDamage());
            player.setTeamId(settings.getTeamId());
        });

        actorRegistry.streamByType(Actor.Type.HEALTH).forEach(actor -> {
            Dispenser dispenser = (Dispenser) actor;
            var healthSettings = current.getHealthDispenserSettings();
            dispenser.setDispenseTimeoutSec(healthSettings.getTimeout());
            dispenser.setAmount(healthSettings.getAmount());
        });

        actorRegistry.streamByType(Actor.Type.AMMO).forEach(actor -> {
            Dispenser dispenser = (Dispenser) actor;
            var ammoSettings = current.getAmmoDispenserSettings();
            dispenser.setDispenseTimeoutSec(ammoSettings.getTimeout());
            dispenser.setAmount(ammoSettings.getAmount());
        });
    }

}


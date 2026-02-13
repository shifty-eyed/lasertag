package net.lasertag.lasertagserver.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.lasertag.lasertagserver.model.Actor;
import net.lasertag.lasertagserver.model.Dispenser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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


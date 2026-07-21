package net.lasertag.lasertagserver.core;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Per-match game event logger. Opens a dedicated file on game start and closes it on game end.
 * Messages also flow to console / server file / SSE via the GameEvents logger additivity.
 */
public final class GameEventLog {

	public static final String LOGGER_NAME = "GameEvents";

	private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);
	private static final DateTimeFormatter FILE_TIMESTAMP =
		DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final String LOGS_DIR = "logs";
	private static final String APPENDER_NAME = "GAME_FILE";

	private static FileAppender<ILoggingEvent> currentAppender;

	private GameEventLog() {}

	public static synchronized void open(String presetName) {
		close();
		try {
			Path logsPath = Path.of(LOGS_DIR);
			Files.createDirectories(logsPath);

			String sanitized = sanitizePresetName(presetName);
			String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
			String filePath = logsPath.resolve("game-" + sanitized + "_" + timestamp + ".log").toString();

			LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

			PatternLayoutEncoder encoder = new PatternLayoutEncoder();
			encoder.setContext(context);
			encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%5.5level] %msg%n");
			encoder.start();

			FileAppender<ILoggingEvent> appender = new FileAppender<>();
			appender.setContext(context);
			appender.setName(APPENDER_NAME);
			appender.setFile(filePath);
			appender.setEncoder(encoder);
			appender.start();

			ch.qos.logback.classic.Logger gameLogger = context.getLogger(LOGGER_NAME);
			gameLogger.addAppender(appender);
			currentAppender = appender;

			log.info("Game log opened: {}", filePath);
		} catch (Exception e) {
			log.error("Failed to open game event log file", e);
		}
	}

	public static synchronized void close() {
		if (currentAppender == null) {
			return;
		}
		try {
			LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
			ch.qos.logback.classic.Logger gameLogger = context.getLogger(LOGGER_NAME);
			gameLogger.detachAppender(currentAppender);
			currentAppender.stop();
		} catch (Exception e) {
			log.error("Failed to close game event log file", e);
		} finally {
			currentAppender = null;
		}
	}

	public static void info(String format, Object... args) {
		log.info(format, args);
	}

	static String sanitizePresetName(String presetName) {
		if (presetName == null || presetName.isBlank() || "New...".equals(presetName)) {
			return "New";
		}
		String sanitized = presetName.replaceAll("[^a-zA-Z0-9._-]+", "_");
		if (sanitized.isBlank()) {
			return "New";
		}
		return sanitized;
	}
}

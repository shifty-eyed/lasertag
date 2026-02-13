package net.lasertag.lasertagserver.web;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SseLogAppender extends AppenderBase<ILoggingEvent> {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static SseEventService sseEventService;

	// Static method to register the SseEventService from Spring context
	public static void setSseEventService(SseEventService service) {
		sseEventService = service;
	}

	@Override
	protected void append(ILoggingEvent event) {
		if (sseEventService == null) {
			return;
		}

		// Filter to only capture logs from UdpServer and Game classes
		String loggerName = event.getLoggerName();
		if (!loggerName.equals("net.lasertag.lasertagserver.core.UdpServer") &&
			!loggerName.equals("net.lasertag.lasertagserver.core.Game")) {
			return;
		}

		// Format the log message
		String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
		String level = event.getLevel().toString();
		String simpleLoggerName = loggerName.substring(loggerName.lastIndexOf('.') + 1);
		String message = event.getFormattedMessage();
		
		String formattedLog = String.format("[%s] %s [%s]: %s", 
			timestamp, level, simpleLoggerName, message);

		// Send to SSE clients
		sseEventService.sendLogMessage(formattedLog);
	}
}


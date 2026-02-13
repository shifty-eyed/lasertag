package net.lasertag.lasertagserver.core;

import net.lasertag.lasertagserver.model.Messaging;
import net.lasertag.lasertagserver.model.Player;

public interface GameEventsListener {
	void onMessageFromPlayer(Player player, Messaging.MessageFromClient message);

	void eventConsoleStartGame(int timeMinutes, int fragLimit, GameType gameType);

	void eventConsoleEndGame();

	void refreshConsoleTable();

	void onPlayerJoinedOrLeft();

	void onPlayerDataUpdated(Player player, boolean isNameUpdated);

}

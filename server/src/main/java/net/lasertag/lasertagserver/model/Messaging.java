package net.lasertag.lasertagserver.model;

import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.*;

import static net.lasertag.lasertagserver.model.MessageType.*;

public abstract class Messaging {

	public static final int TEAM_RED = 0;
	public static final int TEAM_BLUE = 1;
	public static final int TEAM_GREEN = 2;
	public static final int TEAM_YELLOW = 3;
	public static final int TEAM_PURPLE = 4;
	public static final int TEAM_CYAN = 5;

	public static final Set<Byte> PING_GROUP = new HashSet<>(Arrays.asList(PLAYER_PING.id(), HEALTH_DISPENSER_PING.id(), AMMO_DISPENSER_PING.id()));

	private static final Map<Integer, MessageType> MESSAGE_TYPE_BY_ID = MessageType.populateMessageTypeByIdMap();

	public static MessageType getMessageTypeById(int id) {
		var messageType = MESSAGE_TYPE_BY_ID.get(id);
		if (messageType == null) {
			throw new NoSuchElementException("Message type not found for id: " + id);
		}
		return messageType;
	}

	@Getter
	public static class MessageFromClient extends Messaging {

		private final byte typeId;
		private final MessageType type;
		private final byte actorId;
		private final byte extraValue;
		private final byte health;
		private final boolean firstEverMessage;



		public MessageFromClient(byte[] bytes, int length) {
			if (length < 2) {
				throw new IllegalArgumentException("Invalid message, too short: " + Arrays.toString(Arrays.copyOfRange(bytes, 0, length)));
			}
			this.typeId = bytes[0];
			this.actorId = bytes[1];
			this.type = getMessageTypeById(this.typeId);
			if (PING_GROUP.contains(this.typeId)) {
				this.firstEverMessage = bytes[2] != 0;
				this.extraValue = 0;
				this.health = 0;
			} else if (length == 4) {
				this.extraValue = bytes[2];
				this.health = bytes[3];
				this.firstEverMessage = false;
			} else {
				throw new IllegalArgumentException("Invalid message: " + Arrays.toString(Arrays.copyOfRange(bytes, 0, length)));
			}
		}

		@Override
		public String toString() {
			return "MessageFromClient{" +
				"type=" + typeId +
				", p=" + actorId +
				", extraValue=" + extraValue +
				", h=" + health +
				", first=" + firstEverMessage +
				'}';
		}
	}

	public static byte[] eventToBytes(byte type, byte... payload) {
		var result = new byte[1 + payload.length];
		result[0] = type;
		System.arraycopy(payload, 0, result, 1, payload.length);
		return result;
	}

	public static byte[] playerStatsToBytes(boolean includeNames, List<Player> players, boolean gameRunning, int gameTypeOrdinal, int timeSeconds) {
		var size = 6 + getPlayersSize(players, includeNames);
		ByteBuffer data = ByteBuffer.allocate(size);
		data.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		data.put(FULL_STATS.id()); //byte 1
		data.put((byte)(gameRunning ? 1 : 0)); //byte 2
		data.put((byte) gameTypeOrdinal); //byte 3: gameType ordinal (0=DM, 1=TDM, 2=CTF)
		data.putShort((short)(timeSeconds)); //byte 4,5
		data.put((byte)players.size()); //byte 6
		for (Player player : players) {
			data.put((byte)player.getId()); //byte 1
			data.put((byte)player.getHealth()); //byte 2
			data.put((byte)player.getScore()); //byte 3
			data.put((byte)player.getTeamId()); //byte 4
			data.put((byte)player.getDamage()); //byte 5
			data.put((byte)player.getBulletsMax()); //byte 6
			data.put((byte)player.getAssignedRespawnPoint()); //byte 7
			data.put(player.isFlagCarrier() ? (byte) 1 : (byte) 0); //byte 8
			if (includeNames) {
				data.put((byte) player.getName().length()); //byte 9
				data.put(player.getName().getBytes());
			} else {
				data.put((byte) 0); //byte 9
			}
		}
		return data.array();
	}

	private static int getPlayersSize(List<Player> players, boolean includeNames) {
		int size = 0;
		for (Player player : players) {
			size += 9 + (includeNames ? player.getName().length() : 0);
		}
		return size;
	}

}

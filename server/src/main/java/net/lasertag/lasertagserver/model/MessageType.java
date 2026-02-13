package net.lasertag.lasertagserver.model;

import java.util.HashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import static  net.lasertag.lasertagserver.model.MessageType.Direction.*;

public record MessageType(byte id, String name, Direction directionFlag) {
	public static final MessageType PING = new MessageType((byte) 1, "PING", SERVER_TO_CLIENT);
	public static final MessageType PLAYER_PING = new MessageType((byte) 41, "PLAYER_PING", CLIENT_TO_SERVER);
	public static final MessageType HEALTH_DISPENSER_PING = new MessageType((byte) 45, "HEALTH_DISPENSER_PING", CLIENT_TO_SERVER);
	public static final MessageType AMMO_DISPENSER_PING = new MessageType((byte) 46, "AMMO_DISPENSER_PING", CLIENT_TO_SERVER);

	public static final MessageType YOU_HIT_SOMEONE = new MessageType((byte) 4, "YOU_HIT_SOMEONE", CLIENT_TO_SERVER);
	public static final MessageType GOT_HIT = new MessageType((byte) 5, "GOT_HIT", CLIENT_TO_SERVER);
	public static final MessageType RESPAWN = new MessageType((byte) 6, "RESPAWN", CLIENT_TO_SERVER);
	public static final MessageType GAME_OVER = new MessageType((byte) 7, "GAME_OVER", SERVER_TO_CLIENT);
	public static final MessageType GAME_START = new MessageType((byte) 8, "GAME_START", SERVER_TO_CLIENT);
	public static final MessageType YOU_KILLED = new MessageType((byte) 9, "YOU_KILLED", SERVER_TO_CLIENT);
	public static final MessageType YOU_SCORED = new MessageType((byte) 10, "YOU_SCORED", SERVER_TO_CLIENT);
	public static final MessageType FULL_STATS = new MessageType((byte) 11, "FULL_STATS", SERVER_TO_CLIENT);

	public static final MessageType DEVICE_PLAYER_STATE = new MessageType((byte) 13, "DEVICE_PLAYER_STATE", CLIENT_TO_SERVER);
	public static final MessageType DEVICE_CONNECTED = new MessageType((byte)14, "DEVICE_CONNECTED", CLIENT_TO_SERVER);
	public static final MessageType DEVICE_DISCONNECTED = new MessageType((byte)15, "DEVICE_DISCONNECTED", CLIENT_TO_SERVER);

	public static final MessageType GOT_HEALTH = new MessageType((byte) 16, "GOT_HEALTH", CLIENT_TO_SERVER);
	public static final MessageType GOT_AMMO = new MessageType((byte) 17, "GOT_AMMO", CLIENT_TO_SERVER);
	public static final MessageType GOT_FLAG = new MessageType((byte) 18, "GOT_FLAG", CLIENT_TO_SERVER);

	public static final MessageType FLAG_TAKEN = new MessageType((byte) 19, "FLAG_TAKEN", CLIENT_TO_SERVER);
	public static final MessageType FLAG_LOST = new MessageType((byte) 20, "FLAG_LOST", SERVER_TO_CLIENT);
	public static final MessageType FLAG_CAPTURED = new MessageType((byte) 21, "FLAG_CAPTURED", CLIENT_TO_SERVER);

	public static final MessageType GIVE_HEALTH_TO_PLAYER = new MessageType((byte) 26, "GIVE_HEALTH_TO_PLAYER", BOTH_DIRECTIONS);
	public static final MessageType GIVE_AMMO_TO_PLAYER = new MessageType((byte) 27, "GIVE_AMMO_TO_PLAYER", BOTH_DIRECTIONS);


	public static final MessageType DISPENSER_USED = new MessageType((byte) 51, "DISPENSER_USED", SERVER_TO_CLIENT);
	public static final MessageType DISPENSER_SET_TIMEOUT = new MessageType((byte) 53, "DISPENSER_SET_TIMEOUT", SERVER_TO_CLIENT);

	public static final MessageType GAME_TIMER = new MessageType((byte) 101, "GAME_TIMER", SERVER_TO_CLIENT);
	public static final MessageType LOST_CONNECTION = new MessageType((byte) 102, "LOST_CONNECTION", SERVER_TO_CLIENT);
	public static final MessageType MOCK_DEVICE_EVENT = new MessageType((byte) 103, "MOCK_DEVICE_EVENT", SERVER_TO_CLIENT);

	public enum Direction {
		CLIENT_TO_SERVER,
		SERVER_TO_CLIENT,
		BOTH_DIRECTIONS
	}

	public boolean isClientToServer() {
		return directionFlag == CLIENT_TO_SERVER || directionFlag == BOTH_DIRECTIONS;
	}

	public boolean isServerToClient() {
		return directionFlag == SERVER_TO_CLIENT || directionFlag == BOTH_DIRECTIONS;
	}

	static HashMap<Integer, MessageType> populateMessageTypeByIdMap() {
		HashMap<Integer, MessageType> messageTypeMap = new HashMap<>();
		try {
			Field[] fields = MessageType.class.getDeclaredFields();

			for (Field field : fields) {
				if (Modifier.isStatic(field.getModifiers()) &&
					field.getType() == MessageType.class) {

					MessageType messageType = (MessageType) field.get(null);
					messageTypeMap.put((int) messageType.id(), messageType);
				}
			}
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		return messageTypeMap;
	}
}

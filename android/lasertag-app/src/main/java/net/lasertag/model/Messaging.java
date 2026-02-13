package net.lasertag.model;


import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class Messaging {

    public static final byte PING = 1;
    public static final byte PLAYER_REPLY_PING = 41;
    public static final byte GUN_SHOT = 2;
    public static final byte GUN_RELOAD = 3;
    public static final byte YOU_HIT_SOMEONE = 4;
    public static final byte GOT_HIT = 5;
    public static final byte RESPAWN = 6;
    public static final byte GAME_OVER = 7;
    public static final byte GAME_START = 8;
    public static final byte YOU_KILLED = 9;
    public static final byte YOU_SCORED = 10;
    public static final byte PLAYER_VALUES_SNAPSHOT = 11;
    public static final byte GUN_NO_BULLETS = 12;
    public static final byte DEVICE_PLAYER_STATE = 13;
    public static final byte DEVICE_CONNECTED = 14;
    public static final byte DEVICE_DISCONNECTED = 15;

    public static final byte GOT_HEALTH = 16;
    public static final byte GOT_AMMO = 17;
    public static final byte GOT_FLAG = 18;
    public static final byte RESPAWN_POINT_WRONG = 19;

    public static final byte GIVE_HEALTH_TO_PLAYER = 26;
    public static final byte GIVE_AMMO_TO_PLAYER = 27;

    public static final byte GAME_TIMER = 101;
    public static final byte SERVER_DISCONNECTED = 102;
    public static final byte MOCK_EVENT_FROM_DEVICE = 103;


    public static WirelessMessage fromBytes(byte[] bytes, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, length);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        byte type = buffer.get();
        if (type == PING) {
            return new SignalMessage(PING);
        } else if (type == PLAYER_VALUES_SNAPSHOT) {
            return parseFullStatsMessage(buffer);
        } else if (type == GAME_START) {
            return parseGameStartEventFromServer(buffer);
        } else if (type == MOCK_EVENT_FROM_DEVICE) {
            var payload = new byte[length - 1];
            buffer.get(payload);
            return new MockEventMessageFromDevice(MOCK_EVENT_FROM_DEVICE, payload);
        } else {
            return parseEventFromServer(type, buffer);
        }
    }

    private static EventMessageIn parseEventFromServer(byte type, ByteBuffer buffer) {
        var otherPlayerId = buffer.get();
        return new EventMessageIn(type, otherPlayerId);
    }

    private static GameStartMessageIn parseGameStartEventFromServer(ByteBuffer buffer) {
        var teamPlay = buffer.get();
        var gameTimeMinutes = buffer.get();
        return new GameStartMessageIn(GAME_START, teamPlay != 0, gameTimeMinutes);
    }

    public static EventMessageIn parseMessageFromDevice(List<Byte> bytes) {
        return new EventMessageIn(bytes.get(0), bytes.get(1));
    }

    private static StatsMessageIn parseFullStatsMessage(ByteBuffer buffer) {
        var isGameRunning = buffer.get() != 0;
        var teamPlay = buffer.get() != 0;
        var gameTimerSeconds = buffer.getShort();
        var playersCount = buffer.get();
        var players = new Player[playersCount];
        for (int i = 0; i < playersCount; i++) {
            var id = buffer.get();
            var health = buffer.get();
            var score = buffer.get();
            var teamId = buffer.get();
            var damage = buffer.get();
            var bulletsMax = buffer.get();
            var assignedRespawnPoint = buffer.get();
            var flagCarrier = buffer.get() != 0;
            var nameLength = buffer.get();
            var nameBytes = new byte[nameLength];
            if (nameLength > 0) {
                buffer.get(nameBytes, 0, nameLength);
            }
            players[i] = new Player(id, health, score, teamId, damage, 0, 0, bulletsMax, assignedRespawnPoint, flagCarrier, new String(nameBytes));
        }
        return new StatsMessageIn(PLAYER_VALUES_SNAPSHOT, isGameRunning, teamPlay, gameTimerSeconds, players);
    }

}

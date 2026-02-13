package net.lasertag;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.InetAddress;
import java.util.UUID;

public class Config {

    public static final int DEFAULT_PLAYER_ID = 2;

    public static final String TAG = "Lasertag";
    public static final String CHANNEL_ID = "LasertagForegroundServiceChannel";

    private static final String PREFS_NAME = "LaserTagPrefs";
    private static final String PLAYER_ID_KEY = "player_id";

    public static final String GUN_DEVICE_NAME = "LaserTagGun";
    public static final String VEST_DEVICE_NAME = "LaserTagVest";

    public static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final String INTERCOM_GAME_MESSAGE = "UDP_MESSAGE_RECEIVED";
    public static final String INTERCOM_TIME_TICK = "TIME_TICK_RECEIVED";
    public static final String INTERCOM_GAME_STATE = "CURRENT_STATE";

    public static final int STATE_IDLE = 0;
    public static final int STATE_GAME = 1;
    public static final int STATE_DEAD = 2;
    public static final int STATE_OFFLINE = 3;

    public static final int SERVER_PORT = 9878;
    public static final int LISTENING_PORT = 1234;
    public static final long HEARTBEAT_INTERVAL = 2000;
    public static final long HEARTBEAT_TIMEOUT = 5000;

    public static final int MAX_HEALTH = 100;
    public static final int MAGAZINE_SIZE = 10;

    private final byte playerId;
    private InetAddress broadcastAddress;
    private InetAddress serverAddress = null;
    private final Context context;

    public Config(Context context) {
        this.context = context;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        playerId = (byte)prefs.getInt(PLAYER_ID_KEY, DEFAULT_PLAYER_ID);
        try {
            broadcastAddress = InetAddress.getByName("255.255.255.255");
        } catch (Exception ignored) {}
    }

    public byte getPlayerId() {
        return playerId;
    }
    public void setPlayerId(int playerId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PLAYER_ID_KEY, playerId);
        editor.apply();
    }

    public InetAddress getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(InetAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    public InetAddress getBroadcastAddress() {
        return broadcastAddress;
    }

    public static final int[] TEAM_COLORS = {
        R.color.tableRowBackgroundRed,
        R.color.tableRowBackgroundBlue,
        R.color.tableRowBackgroundGreen,
        R.color.tableRowBackgroundYellow,
        R.color.tableRowBackgroundMagenta,
        R.color.tableRowBackgroundCyan
    };

    public static final int[] TEAM_TEXT_COLORS = {
        R.color.nameTextRed,
        R.color.nameTextBlue,
        R.color.nameTextGreen,
        R.color.nameTextYellow,
        R.color.nameTextMagenta,
        R.color.nameTextCyan
    };

    public int getTeamColor(int teamId, boolean background) {
        return background ? TEAM_COLORS[teamId] : TEAM_TEXT_COLORS[teamId];
    }
}

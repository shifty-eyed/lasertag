package net.lasertag;

import static net.lasertag.Config.*;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;

import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import net.lasertag.communication.BluetoothClient;
import net.lasertag.model.EventMessageIn;
import net.lasertag.model.WirelessMessage;
import net.lasertag.model.Player;
import net.lasertag.model.StatsMessageIn;
import net.lasertag.model.TimeMessage;
import net.lasertag.model.Messaging;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@SuppressLint({"SetTextI18n","InlinedApi","DefaultLocale"})
public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    public static final String[] REQUIRED_PERMISSIONS =
        (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) ? new String[]{} :
            new String[]{
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
            };

    private final BroadcastReceiver serviceMessageReceiver = new BroadcastReceiver() {
        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case INTERCOM_GAME_STATE -> {
                    var state = intent.getIntExtra("state", -1);
                    teamPlay = intent.getBooleanExtra("teamPlay", false);
                    if (state != currentState) {
                        currentState = state;
                        if (!toasterOn) {
                            onRefreshUIGameSate();
                        }
                    }
                }
                case INTERCOM_TIME_TICK ->
                        handleTime((TimeMessage) intent.getSerializableExtra("message"));
                case INTERCOM_GAME_MESSAGE ->
                        handleIncomingMessage((WirelessMessage) intent.getSerializableExtra("message"),
                                (Player) Objects.requireNonNull(intent.getSerializableExtra("player")));

            }
        }
    };

    private Config config;
    private TextView playerName;
    private TextView playerHealth;
    private TextView playerScore;
    private TextView playerTotalAmmo;
    private ImageView deviceStatusGun;
    private ImageView deviceStatusVest;
    private TableLayout playersTable;
    private ConstraintLayout playerInfoLayout;
    private ConstraintLayout announcementLayout;
    private TextView announcementText;
    private TextView gameTime;
    private LinearLayout bulletsBar;
    private LinearLayout teamScoresBar;

    private TextToSpeech textToSpeech;

    private static final String[] teamNames = new String[] {"Red", "Blue", "Green", "Yellow", "Purple", "Cyan"};
    private volatile int currentState = -1;
    private volatile boolean teamPlay = false;
    private volatile Player[] players = new Player[0];
    private final Player thisPlayer = new Player(DEFAULT_PLAYER_ID);
    private volatile boolean toasterOn = false;
    private int lastLeader = -1;

    private AdminSettingsDialog adminSettingsDialog;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        if (allPermissionsGranted()) {
            startService(new Intent(this, GameService.class));
            registerReceiver(serviceMessageReceiver, new IntentFilter(INTERCOM_GAME_MESSAGE), Context.RECEIVER_EXPORTED);
            registerReceiver(serviceMessageReceiver, new IntentFilter(INTERCOM_TIME_TICK), Context.RECEIVER_EXPORTED);
            registerReceiver(serviceMessageReceiver, new IntentFilter(INTERCOM_GAME_STATE), Context.RECEIVER_EXPORTED);
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS,1);
        }

        config = new Config(this);
        thisPlayer.setId(config.getPlayerId());
        textToSpeech = new TextToSpeech(this, this);
        adminSettingsDialog = new AdminSettingsDialog(this);

        enableFullScreenMode();
        setContentView(R.layout.activity_main);

        playerName = findViewById(R.id.player_name);
        playerHealth = findViewById(R.id.player_health);
        playerScore = findViewById(R.id.player_score);
        playerTotalAmmo = findViewById(R.id.player_total_ammo);
        playersTable = findViewById(R.id.players_table);
        playerInfoLayout = findViewById(R.id.player_info_layout);
        announcementLayout = findViewById(R.id.announcement_layout);
        announcementText = findViewById(R.id.announcement_text);
        announcementText.setOnClickListener((View v) -> adminSettingsDialog.onAdminSettingsInvocationTap());
        gameTime = findViewById(R.id.game_timer);
        bulletsBar = findViewById(R.id.bullets_bar);
        teamScoresBar = findViewById(R.id.team_scores);

        deviceStatusGun = findViewById(R.id.device_status_gun);
        deviceStatusGun.setVisibility(View.GONE);
        deviceStatusVest = findViewById(R.id.device_status_vest);

        currentState = STATE_OFFLINE;
        onRefreshUIGameSate();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (allPermissionsGranted()) {
                Intent serviceIntent = new Intent(this, GameService.class);
                startService(serviceIntent);
            } else {
                Toast.makeText(this, "Permissions not granted. Cannot start the service.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            textToSpeech.setSpeechRate(0.7f);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
            }
        } else {
            Log.e(TAG, "Initialization failed");
        }
    }

    private void enableFullScreenMode() {
        EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        goFullScreen();
    }

    public void goFullScreen() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        goFullScreen();
        sendBroadcast(new Intent("ACTIVITY_RESUMED"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        sendBroadcast(new Intent("ACTIVITY_PAUSED"));
    }

    private void showAnnouncementLayout(boolean show) {
        announcementLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        playerInfoLayout.setVisibility(!show ? View.VISIBLE : View.GONE);
    }

    private void showToasterMessage(String message, long delayMillis) {
        toasterOn = true;
        showAnnouncementLayout(true);
        announcementText.setText(message);
        new Handler().postDelayed(() -> speak(message), 1000);
        new Handler().postDelayed(this::onRefreshUIGameSate, delayMillis);
    }

    private void speak(String message) {
        Log.i(TAG, "Speaking: " + message);
        textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null, null);
    }

    private void onRefreshUIGameSate() {
        toasterOn = false;
        switch (currentState) {
            case STATE_GAME -> showAnnouncementLayout(false);
            case STATE_IDLE -> {
                showAnnouncementLayout(true);
                gameTime.setText("--:--");
                announcementText.setText("Game is not started.");
            }
            case STATE_DEAD -> {
                showAnnouncementLayout(true);
                announcementText.setText(String.format("Go to\nRespawn Point %d", thisPlayer.getAssignedRespawnPoint()));
            }
            case STATE_OFFLINE -> {
                gameTime.setText("--:--");
                showAnnouncementLayout(true);
                announcementText.setText("Offline");
            }
        }
    }

    private void handleEvent(EventMessageIn message) {
        var payload = message.getPayload();
        var otherPlayer = getPlayerById(payload);
        var otherName = otherPlayer != null ? otherPlayer.getName() : "someone";
        switch (message.getType()) {
            case Messaging.GAME_START -> lastLeader = -1;
            case Messaging.GOT_HIT -> {
               //maybe make screen flash red or show quick toaster with otherName
            }
            case Messaging.RESPAWN -> showToasterMessage("Play!", 2000);
            case Messaging.YOU_KILLED -> showToasterMessage(otherName + " killed you.", 3000);
            case Messaging.YOU_SCORED -> showToasterMessage("You killed " + otherName, 2000);
            case Messaging.GAME_OVER -> {
                if (teamPlay) {
                    var teamName = payload < 0 ? "No one" : teamNames[payload];
                    showToasterMessage("Game Over!\n" + teamName + " wins.", 4000);
                } else {
                    if (otherPlayer != null && otherPlayer.getId() == config.getPlayerId()) {
                        showToasterMessage("You win!", 4000);
                    } else {
                        showToasterMessage("Game Over!\n" + (otherPlayer == null ? "No one" : otherPlayer.getName()) + " wins.", 4000);
                    }
                }
            }
            case Messaging.GIVE_HEALTH_TO_PLAYER ->
                showToasterMessage( String.format("+ %d health", payload), 1500);
            case Messaging.GIVE_AMMO_TO_PLAYER ->
                    showToasterMessage( String.format("+ %d bullets", payload), 1500);
            case Messaging.DEVICE_CONNECTED, Messaging.DEVICE_DISCONNECTED -> {
                var connected = (message.getType() == Messaging.DEVICE_CONNECTED);
                //var isGun = (payload == BluetoothClient.DEVICE_GUN);
                //var deviceName = isGun ? "gun" : "vest";
                var deviceName = "vest";
                //(isGun ? deviceStatusGun : deviceStatusVest)
                deviceStatusVest
                       .setVisibility(connected ? View.GONE : View.VISIBLE);
                speak(deviceName + (connected ? " connected." : " disconnected."));
            }
        }
    }

    private void handleTime(TimeMessage message) {
        gameTime.setText(String.format("%02d:%02d", message.getMinutes(), message.getSeconds()));
        if (message.getMinutes() == 0 && message.getSeconds() < 10 && message.getSeconds() > 0) {
            speak(String.valueOf(message.getSeconds()));
        }
    }

    private void handleIncomingMessage(WirelessMessage message, Player myPlayerInfo) {
        runOnUiThread(() -> updatePlayerInfo(myPlayerInfo));

        if (message instanceof StatsMessageIn) {
            runOnUiThread(() -> updatePlayersTable((StatsMessageIn) message));
        } else if (message instanceof EventMessageIn) {
            handleEvent((EventMessageIn) message);
        }
    }
    private void updatePlayerInfo(Player player) {
        thisPlayer.copyPlayerValuesFrom(player);
        playerName.setText(player.getName());
        playerName.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(player.getTeamId(), true), null));
        playerHealth.setText(String.valueOf(player.getHealth()));
        playerScore.setText(String.valueOf(player.getScore()));
        playerTotalAmmo.setText(String.valueOf(player.getBulletsTotal()));
        refreshBulletsBar(player.getBulletsInMagazine());
    }

    private void updatePlayersInfoAndAnnounceLeaderChange(Player[] newPlayers) {
        if (players.length > 0) {
            if (teamPlay) {
                var teamScores = getTeamScores(players);
                var maxScoreEntry = Collections.max(teamScores.entrySet(), Map.Entry.comparingByValue());
                var countOfLeaders = (int) teamScores.values().stream().filter(s -> s.equals(maxScoreEntry.getValue())).count();
                var newLeaderTeam = countOfLeaders == 1 ? maxScoreEntry.getKey() : -1;
                if (lastLeader != newLeaderTeam) {
                    var message = (newLeaderTeam == -1 ? "Teams are tie!" : teamNames[newLeaderTeam] + " team leads!");
                    new Handler().postDelayed(() -> speak(message), toasterOn ? 2000 : 100);
                }
                lastLeader = newLeaderTeam;
            } else {
                var maxScore = newPlayers[0].getScore();
                var countOfLeaders = (int) Arrays.stream(newPlayers).filter(p -> p.getScore() == maxScore).count();
                var newLeaderId = countOfLeaders == 1 ? newPlayers[0].getId() : -1;
                if (lastLeader != newLeaderId) {
                    var message = (newLeaderId == -1 ? "You are tie!" :
                            (newLeaderId == config.getPlayerId()
                                    ? "You are"
                                    : Objects.requireNonNull(getPlayerById(newLeaderId)).getName() + " is") + " the new leader!");
                    new Handler().postDelayed(() -> speak(message), toasterOn ? 2000 : 100);
                }
                lastLeader = newLeaderId;
            }
        }
        players = newPlayers;
    }

    private Map<Integer, Integer> getTeamScores(Player[] players) {
        Map<Integer, Integer> teamScores = new HashMap<>();
        for (Player player : players) {
            teamScores.put(player.getTeamId(), teamScores.getOrDefault(player.getTeamId(), 0) + player.getScore());
        }
        return teamScores;
    }

    private void updatePlayersTable(StatsMessageIn message) {
        playersTable.removeViews(1, Math.max(0, playersTable.getChildCount() - 1));
        teamScoresBar.removeAllViews();
        for (Player player : message.getPlayers()) {
            TableRow row = new TableRow(this);
            row.setPadding(8, 8, 8, 8);
            row.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.table_row_border, null));
            row.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(player.getTeamId(), true), null));

            TextView nameText = new TextView(this);
            nameText.setText(player.getName());
            nameText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2));

            TextView scoreText = new TextView(this);
            scoreText.setText(String.valueOf(player.getScore()));
            scoreText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            // consider removing health from table, player doesnt need to know other's health
            TextView healthText = new TextView(this);
            healthText.setText(String.valueOf(player.getHealth()));
            healthText.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));

            var textFields = Arrays.asList(nameText, scoreText, healthText);

            for (TextView text : textFields) {
                text.setPadding(8, 8, 8, 8);
                if (player.getHealth() <= 0 && currentState == STATE_GAME) {
                    text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    text.setTextColor(ResourcesCompat.getColor(getResources(), R.color.black, null));
                } else {
                    text.setTextColor(ResourcesCompat.getColor(getResources(), R.color.white, null));
                }
                row.addView(text);
            }
            playersTable.addView(row);
        }
        if (teamPlay) {
            for (Map.Entry<Integer, Integer> e : getTeamScores(message.getPlayers()).entrySet()) {
                TextView teamScore = new TextView(this);
                teamScore.setTextColor(ResourcesCompat.getColor(getResources(), R.color.white, null));
                teamScore.setPadding(16, 8, 8, 8);
                teamScore.setTextSize(30);
                teamScore.setText(String.valueOf(e.getValue()));
                teamScore.setBackgroundColor(ResourcesCompat.getColor(getResources(), config.getTeamColor(e.getKey(), true), null));
                teamScore.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
                teamScoresBar.addView(teamScore);
            }
        }
        updatePlayersInfoAndAnnounceLeaderChange(message.getPlayers());
    }

    private void refreshBulletsBar(int bulletsLeft) {
        bulletsBar.removeAllViews();
        for (int i = 0; i < bulletsLeft; i++) {
            ImageView bullet = new ImageView(this);
            bullet.setImageResource(R.drawable.bullet);
            bullet.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            bulletsBar.addView(bullet);
        }
    }

    private Player getPlayerById(int id) {
        for (Player player : players) {
            if (player.getId() == id) {
                return player;
            }
        }
        return null;
    }

    public Config getConfig() {
        return config;
    }

    public Player getThisPlayer() {
        return thisPlayer;
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called");
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
        unregisterReceiver(serviceMessageReceiver);
    }

}
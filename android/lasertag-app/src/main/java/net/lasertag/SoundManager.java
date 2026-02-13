package net.lasertag;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

public class SoundManager {

    private final SoundPool soundPool;

    private final int gunShotSound;
    private final int gotHitSound;
    private final int youHitSomeoneSound;
    private final int noBulletsSound;
    private final int reloadSound;
    private final int youKilledSound;
    private final int respawnSound;
    private final int gameOverSound;
    private final int gameStartSound;
    private final int youScoredSound;

    public SoundManager(Context context) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build();

        gunShotSound = soundPool.load(context, R.raw.fire, 1);
        gotHitSound = soundPool.load(context, R.raw.hitby, 1);
        youHitSomeoneSound = soundPool.load(context, R.raw.hit, 1);
        noBulletsSound = soundPool.load(context, R.raw.noammo, 1);
        reloadSound = soundPool.load(context, R.raw.reload, 1);
        youKilledSound = soundPool.load(context, R.raw.death, 1);
        respawnSound = soundPool.load(context, R.raw.respawn, 1);
        gameOverSound = soundPool.load(context, R.raw.game_over, 1);
        gameStartSound = soundPool.load(context, R.raw.game_start, 1);
        youScoredSound = soundPool.load(context, R.raw.you_scored, 1);
    }

    public void release() {
        soundPool.release();
    }

    private void play(int soundId) {
        soundPool.play(soundId, 1, 1, 0, 0, 1);
    }

    public void playGunShot() {
        play(gunShotSound);
    }

    public void playGotHit() {
        play(gotHitSound);
    }

    public void playYouHitSomeone() {
        play(youHitSomeoneSound);
    }

    public void playNoBullets() {
        play(noBulletsSound);
    }

    public void playReload() {
        play(reloadSound);
    }

    public void playYouKilled() {
        play(youKilledSound);
    }

    public void playRespawn() {
        play(respawnSound);
    }

    public void playGameOver() {
        play(gameOverSound);
    }

    public void playGameStart() {
        play(gameStartSound);
    }

    public void playYouScored() {
        play(youScoredSound);
    }

    public void playGotHealth() {
        play(youScoredSound);
    }

    public void playGotAmmo() {
        play(reloadSound);
    }

}

# Capture The Flag Game Mode Implementation

## 1. Add GameType Enum

Create [`src/main/java/net/lasertag/lasertagserver/core/GameType.java`](src/main/java/net/lasertag/lasertagserver/core/GameType.java):

```java
public enum GameType {
    DM(false),
    TEAM_DM(true),
    CTF(true);
    
    private final boolean teamBased;
    
    GameType(boolean teamBased) { this.teamBased = teamBased; }
    public boolean isTeamBased() { return teamBased; }
}
```

## 2. Add Team Score Tracking

In [`ActorRegistry.java`](src/main/java/net/lasertag/lasertagserver/core/ActorRegistry.java):

- Add `Map<Integer, Integer> teamScores` field (stored, not computed)
- Add `incrementTeamScore(int teamId)` method
- Add `resetTeamScores()` for game start
- Keep existing `getTeamScores()` but return the stored map

## 3. Update GameSettingsPreset

In [`GameSettingsPreset.java`](src/main/java/net/lasertag/lasertagserver/core/GameSettingsPreset.java):

- Replace `boolean teamPlay` with `GameType gameType`
- Add convenience method: `boolean isTeamPlay() { return gameType.isTeamBased(); }`
- Update `getAllSettings()` to include `gameType`

## 4. Add CTF Message Types

In [`MessageType.java`](src/main/java/net/lasertag/lasertagserver/model/MessageType.java):

- Add `FLAG_TAKEN` (player picked up enemy flag)
- Add `FLAG_LOST` (flag carrier killed, flag auto-returns)
- Add `FLAG_CAPTURED` (flag delivered to home base, team scores)

## 5. Update Game Logic

In [`Game.java`](src/main/java/net/lasertag/lasertagserver/core/Game.java):

**On player kill (Team DM):**

```java
if (gameType.isTeamBased()) {
    actorRegistry.incrementTeamScore(hitByPlayer.getTeamId());
}
```

**On FLAG_CAPTURED (CTF):**

```java
actorRegistry.incrementTeamScore(capturingPlayer.getTeamId());
player.setFlagCarrier(false);
```

**Win condition check:**

```java
int vitalScore = actorRegistry.getTeamScores().get(teamId); // always use team score for team modes
```

**On game start:**

- Reset team scores via `actorRegistry.resetTeamScores()`

**Handle new CTF messages in `onMessageFromPlayer()`:**

- `FLAG_TAKEN`: Set `player.setFlagCarrier(true)`, broadcast to all
- `FLAG_LOST`: Set `player.setFlagCarrier(false)`, broadcast to all  
- `FLAG_CAPTURED`: Increment team score, set `player.setFlagCarrier(false)`, check win condition

## 6. Update Protocol

In [`Messaging.java`](src/main/java/net/lasertag/lasertagserver/model/Messaging.java):

- Change byte 3 from `teamPlay` boolean to `gameType.ordinal()` (0=DM, 1=TDM, 2=CTF)

In [`Game.java`](src/main/java/net/lasertag/lasertagserver/core/Game.java) line 99:

- Send `gameType.ordinal()` instead of `isTeamPlay() ? 1 : 0`

## 7. Update Web UI

In [`app.js`](src/main/resources/static/app.js):

- Replace `teamPlay: false` with `gameType: 'DEATHMATCH'`
- Update `startGame()` to send `gameType` string
- Add computed property `isTeamBased` to check if current game type is team-based
- Add computed property `availableTeams` returning only Red (0) and Blue (1) for team modes

In [`index.html`](src/main/resources/static/index.html):

- Replace TeamPlay checkbox with Game Type dropdown: Deathmatch / Team Deathmatch / CTF
- Show team scores section for all team-based modes
- Limit player team dropdown to Red/Blue only when game type is team-based

## 8. Update API

In [`GameController.java`](src/main/java/net/lasertag/lasertagserver/web/GameController.java):

- Change `StartGameRequest.teamPlay` to `gameType` (String or enum)
- Parse and validate game type

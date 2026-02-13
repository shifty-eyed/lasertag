package net.lasertag.lasertagserver.core;

public enum GameType {
    DM(false),
    TEAM_DM(true),
    CTF(true);

    private final boolean teamBased;

    GameType(boolean teamBased) {
        this.teamBased = teamBased;
    }

    public boolean isTeamBased() {
        return teamBased;
    }
}


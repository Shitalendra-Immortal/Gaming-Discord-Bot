package com.gamingbot.tournament;

import java.util.ArrayList;
import java.util.List;

public class PlayerProfile {
    public String user;
    public int wins;
    public int losses;
    public int tournamentWins;
    public List<String> matchHistory = new ArrayList<>();
    public boolean notifyMatches = true;
    public boolean notifyResults = true;
    public boolean notifyAnnouncements = true;

    public PlayerProfile() {
    }

    public PlayerProfile(String user) {
        this.user = user;
    }

    public int points() {
        return wins * 3;
    }
}

package com.gamingbot.tournament;

import java.util.ArrayList;
import java.util.List;

public class Team {
    public String name;
    public String captain = "";
    public List<String> players = new ArrayList<>();
    public int wins;
    public int losses;

    public Team() {
    }

    public Team(String name, String captain) {
        this.name = name;
        this.captain = captain;
        if (captain != null && !captain.isBlank()) {
            this.players.add(captain);
        }
    }

    public int points() {
        return wins * 3;
    }
}

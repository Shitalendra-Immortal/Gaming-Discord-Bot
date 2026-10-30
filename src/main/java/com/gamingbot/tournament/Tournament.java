package com.gamingbot.tournament;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Tournament {
    public String name;
    public String game;
    public String format = "Single elimination";
    public String date = "TBD";
    public int teamLimit;
    public boolean soloAllowed;
    public boolean registrationOpen = true;
    public Map<String, Team> teams = new LinkedHashMap<>();
    public List<MatchRecord> matches = new ArrayList<>();
    public List<String> bracket = new ArrayList<>();
    public List<String> advancedTeams = new ArrayList<>();
    public List<String> withdrawnTeams = new ArrayList<>();
    public int bracketRound = 1;
    public List<String> bracketPool = new ArrayList<>();
    public List<String> losersPool = new ArrayList<>();
    public int losersRound = 1;
    public String winnersChampion = "";
    public String losersChampion = "";
    public int finalStage;
    public String champion = "";
    public String prizePool = "";

    public Tournament() {
    }

    public Tournament(String name, String game, String format, String date,
                      int teamLimit, boolean soloAllowed) {
        this.name = name;
        this.game = game;
        this.format = format;
        this.date = date;
        this.teamLimit = teamLimit;
        this.soloAllowed = soloAllowed;
    }
}

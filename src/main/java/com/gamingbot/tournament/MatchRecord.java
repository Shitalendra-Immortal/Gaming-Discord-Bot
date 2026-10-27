package com.gamingbot.tournament;

public class MatchRecord {
    public int id;
    public String teamA;
    public String teamB;
    public String winner = "";
    public String loser = "";
    public String status = "PENDING";
    public String reporter = "";
    public int scoreWinner = -1;
    public int scoreLoser = -1;
    public int round;
    public String bracket = "";

    public MatchRecord() {
    }

    public MatchRecord(int id, String teamA, String teamB, String winner,
                       String loser, String reporter) {
        this.id = id;
        this.teamA = teamA;
        this.teamB = teamB;
        this.winner = winner;
        this.loser = loser;
        this.reporter = reporter;
    }
}

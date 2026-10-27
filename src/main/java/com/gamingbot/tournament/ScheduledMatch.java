package com.gamingbot.tournament;

/**
 * A match with a fixed date and time. Check-ins, reminders, and start
 * notices are tracked here; results still go through the normal
 * submit/confirm flow.
 */
public class ScheduledMatch {
    public int id;
    public String tournament = "";
    public String teamA = "";
    public String teamB = "";
    public long scheduledAt;
    public String channelId = "";
    public boolean checkedA;
    public boolean checkedB;
    public boolean reminded;
    public boolean startedNotified;

    public ScheduledMatch() {
    }

    public ScheduledMatch(int id, String tournament, String teamA, String teamB,
                          long scheduledAt, String channelId) {
        this.id = id;
        this.tournament = tournament;
        this.teamA = teamA;
        this.teamB = teamB;
        this.scheduledAt = scheduledAt;
        this.channelId = channelId;
    }
}

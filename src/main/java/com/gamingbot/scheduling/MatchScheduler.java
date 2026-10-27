package com.gamingbot.scheduling;

import com.gamingbot.tournament.Managers;
import com.gamingbot.tournament.ScheduledMatch;
import com.gamingbot.tournament.TournamentManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background ticker (every 60 seconds) for scheduled matches: a reminder
 * 30 minutes before start, and a start notice at match time, including a
 * no-show note for teams that never checked in. Failures for one server
 * never stop the ticker for the others.
 */
public class MatchScheduler {
    private final JDA jda;
    private final Managers managers;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();

    public MatchScheduler(JDA jda, Managers managers) {
        this.jda = jda;
        this.managers = managers;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::tick, 0, 60, TimeUnit.SECONDS);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (TournamentManager manager : managers.all()) {
            try {
                for (ScheduledMatch m : manager.collectDueReminders(now)) {
                    sendReminder(manager, m);
                }
                for (ScheduledMatch m : manager.collectStarting(now)) {
                    sendStarting(manager, m);
                }
            } catch (Exception e) {
                System.err.println("Scheduler error: " + e.getMessage());
            }
        }
    }

    private void sendReminder(TournamentManager manager, ScheduledMatch m) {
        TextChannel channel = channel(m);
        if (channel == null) {
            return;
        }
        StringBuilder missing = new StringBuilder();
        if (!m.checkedA) {
            appendCaptain(manager, m, m.teamA, missing);
        }
        if (!m.checkedB) {
            appendCaptain(manager, m, m.teamB, missing);
        }
        channel.sendMessage("Reminder: **" + m.teamA + " vs " + m.teamB + "** starts at "
                + TournamentManager.formatIst(m.scheduledAt) + " IST (30 minutes or less). "
                + "Not checked in yet: " + missing + " — check in with /check-in.").queue();
    }

    private void sendStarting(TournamentManager manager, ScheduledMatch m) {
        TextChannel channel = channel(m);
        if (channel == null) {
            return;
        }
        if (m.checkedA && m.checkedB) {
            channel.sendMessage("Match starting now: **" + m.teamA + " vs " + m.teamB
                    + "** — good luck!").queue();
            return;
        }
        StringBuilder missing = new StringBuilder();
        if (!m.checkedA) {
            appendCaptain(manager, m, m.teamA, missing);
        }
        if (!m.checkedB) {
            appendCaptain(manager, m, m.teamB, missing);
        }
        channel.sendMessage("Match time: **" + m.teamA + " vs " + m.teamB + "** — "
                + missing + " did not check in. Please contact a moderator.").queue();
    }

    private void appendCaptain(TournamentManager manager, ScheduledMatch m,
                               String team, StringBuilder out) {
        String captain = manager.getTeamCaptain(m.tournament, team);
        if (out.length() > 0) {
            out.append(", ");
        }
        out.append(team).append(" (").append(captain == null ? "no captain" : captain).append(")");
    }

    private TextChannel channel(ScheduledMatch m) {
        if (m.channelId == null || m.channelId.isEmpty()) {
            return null;
        }
        return jda.getTextChannelById(m.channelId);
    }
}

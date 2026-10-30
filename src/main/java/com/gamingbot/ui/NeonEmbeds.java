package com.gamingbot.ui;

import com.gamingbot.tournament.ScheduledMatch;
import com.gamingbot.tournament.Team;
import com.gamingbot.tournament.Tournament;
import com.gamingbot.tournament.TournamentManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Instant;
import java.util.List;

/**
 * The neon look for LEGEND's messages: one violet accent color, a
 * thumbnail-ready builder, and card layouts for tournaments, matches,
 * leaderboards, and server info. Pure builders, so they are unit-tested
 * without a Discord connection. Banner art is intentionally left out:
 * embeds need a hosted image URL, and none is configured; add
 * setImage(...) here later if the server provides one.
 */
public class NeonEmbeds {
    public static final int NEON_PURPLE = 0x8B5CF6;

    private static EmbedBuilder base(String title) {
        return new EmbedBuilder()
                .setTitle(title)
                .setColor(NEON_PURPLE)
                .setTimestamp(Instant.now())
                .setFooter("LEGEND");
    }

    public static MessageEmbed tournamentCard(Tournament t) {
        EmbedBuilder embed = base("🏆 " + t.name)
                .setDescription(t.game + " • " + t.format);
        embed.addField("Date", t.date, true);
        embed.addField("Game", t.game, true);
        String limit = t.teamLimit > 0 ? String.valueOf(t.teamLimit) : "no limit";
        embed.addField("Teams Registered", t.teams.size() + " / " + limit, true);
        if (t.prizePool != null && !t.prizePool.isBlank()) {
            embed.addField("Prize Pool", t.prizePool, true);
        }
        embed.addField("Registration", t.registrationOpen ? "Open" : "Closed", true);
        if (!t.soloAllowed) {
            embed.setFooter("LEGEND • Join with /team-create, then /team-join");
        }
        return embed.build();
    }

    public static MessageEmbed matchCard(ScheduledMatch m) {
        EmbedBuilder embed = base("⚔️ " + m.teamA + " vs " + m.teamB)
                .setDescription(m.tournament + " • " + TournamentManager.formatIst(m.scheduledAt)
                        + " IST");
        embed.addField("Tournament", m.tournament, true);
        embed.addField("Time", TournamentManager.formatIst(m.scheduledAt) + " IST", true);
        if (m.format != null && !m.format.isBlank()) {
            embed.addField("Format", m.format, true);
        }
        if (m.maps != null && !m.maps.isBlank()) {
            embed.addField("Map Pool", m.maps, false);
        }
        embed.addField("Check-in",
                m.teamA + (m.checkedA ? " ✓" : " ✗") + "  •  "
                        + m.teamB + (m.checkedB ? " ✓" : " ✗"),
                false);
        return embed.build();
    }

    public static MessageEmbed matchDetails(ScheduledMatch m, String captainA, String captainB) {
        EmbedBuilder embed = base("Match #" + m.id + " — " + m.teamA + " vs " + m.teamB)
                .setDescription(m.tournament);
        embed.addField("Time", TournamentManager.formatIst(m.scheduledAt) + " IST", true);
        if (m.format != null && !m.format.isBlank()) {
            embed.addField("Format", m.format, true);
        }
        if (m.maps != null && !m.maps.isBlank()) {
            embed.addField("Map Pool", m.maps, false);
        }
        embed.addField("Captains",
                m.teamA + ": " + (captainA == null ? "—" : captainA) + "\n"
                        + m.teamB + ": " + (captainB == null ? "—" : captainB),
                false);
        embed.addField("Check-in",
                m.teamA + (m.checkedA ? " ✓ checked in" : " ✗ not checked in") + "\n"
                        + m.teamB + (m.checkedB ? " ✓ checked in" : " ✗ not checked in"),
                false);
        return embed.build();
    }

    public static MessageEmbed leaderboardCard(String tournamentName, List<Team> ranked) {
        EmbedBuilder embed = base("📊 Leaderboard — " + tournamentName);
        if (ranked.isEmpty()) {
            embed.setDescription("No teams yet.");
            return embed.build();
        }
        StringBuilder rows = new StringBuilder("```\n");
        rows.append(String.format("%-4s%-16s%4s%4s%6s%n", "#", "Team", "W", "L", "Pts"));
        int rank = 1;
        for (Team team : ranked) {
            String name = team.name.length() > 15 ? team.name.substring(0, 15) : team.name;
            rows.append(String.format("%-4d%-16s%4d%4d%6d%n",
                    rank++, name, team.wins, team.losses, team.points()));
        }
        rows.append("```");
        embed.setDescription(rows.toString());
        return embed.build();
    }

    public static MessageEmbed rulesCard(String tournamentName) {
        return base("📖 Tournament Rules — " + tournamentName)
                .setDescription("• Results are submitted by the winning captain "
                        + "and must be confirmed by the opposing captain before points count.\n"
                        + "• Disputed results go to moderators instead of changing the leaderboard.\n"
                        + "• One team per player per tournament.\n"
                        + "• Captains check in before scheduled matches; no-shows are reported "
                        + "to moderators.\n"
                        + "• A win is worth 3 points.")
                .build();
    }

    public static MessageEmbed serverCard(int members, int tournaments, int teams) {
        EmbedBuilder embed = base("Server Stats");
        embed.addField("Members", String.valueOf(members), true);
        embed.addField("Tournaments", String.valueOf(tournaments), true);
        embed.addField("Teams", String.valueOf(teams), true);
        embed.addField("Supported Games", "Valorant • CS2 • BGMI • Minecraft", false);
        embed.addField("Bot Features",
                "Tournaments • Match scheduling • Leaderboards • Auto roles • "
                        + "Moderation • Live match updates",
                false);
        return embed.build();
    }

    public static MessageEmbed upcomingCard(String tournamentName, List<ScheduledMatch> matches) {
        EmbedBuilder embed = base("📅 Upcoming Matches — " + tournamentName);
        if (matches.isEmpty()) {
            embed.setDescription("No upcoming matches scheduled.");
            return embed.build();
        }
        for (ScheduledMatch m : matches) {
            embed.addField("#" + m.id + " " + m.teamA + " vs " + m.teamB,
                    TournamentManager.formatIst(m.scheduledAt) + " IST • Check-in: "
                            + m.teamA + (m.checkedA ? " ✓" : " ✗") + ", "
                            + m.teamB + (m.checkedB ? " ✓" : " ✗"),
                    false);
        }
        return embed.build();
    }
}

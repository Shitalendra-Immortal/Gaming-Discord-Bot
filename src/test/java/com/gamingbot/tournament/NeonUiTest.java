package com.gamingbot.tournament;

import com.gamingbot.ui.NeonEmbeds;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NeonUiTest {

    private TournamentManager manager(Path tmp) {
        return new TournamentManager("test-guild", tmp);
    }

    private String field(MessageEmbed embed, String name) {
        for (MessageEmbed.Field field : embed.getFields()) {
            if (field.getName().equals(name)) {
                return field.getValue();
            }
        }
        return null;
    }

    private boolean hasField(MessageEmbed embed, String name) {
        return field(embed, name) != null;
    }

    @Test
    void tournamentCardShowsPrizePoolOnlyWhenSet() {
        Tournament withPrize = new Tournament("Cup", "Valorant", "5V5", "2026-11-01", 64, false);
        withPrize.prizePool = "₹5,000";
        withPrize.teams.put("Alpha", new Team("Alpha", "Alice"));
        MessageEmbed card = NeonEmbeds.tournamentCard(withPrize);
        assertTrue(card.getTitle().contains("Cup"));
        assertEquals(new Color(NeonEmbeds.NEON_PURPLE), card.getColor());
        assertEquals("₹5,000", field(card, "Prize Pool"));
        assertTrue(field(card, "Teams Registered").startsWith("1 / 64"));

        Tournament noPrize = new Tournament("Cup", "Valorant", "5V5", "TBD", 0, true);
        assertFalse(hasField(NeonEmbeds.tournamentCard(noPrize), "Prize Pool"));
    }

    @Test
    void matchCardShowsFormatMapsAndCheckIn() {
        ScheduledMatch m = new ScheduledMatch(1, "Cup", "Alpha", "Beta",
                System.currentTimeMillis() + 3600_000, "chan1", "BO3", "Bind, Ascent, Haven");
        m.checkedA = true;
        MessageEmbed card = NeonEmbeds.matchCard(m);
        assertTrue(card.getTitle().contains("Alpha vs Beta"));
        assertEquals("BO3", field(card, "Format"));
        assertEquals("Bind, Ascent, Haven", field(card, "Map Pool"));
        assertTrue(field(card, "Check-in").contains("Alpha ✓"));
        assertTrue(field(card, "Check-in").contains("Beta ✗"));

        ScheduledMatch plain = new ScheduledMatch(2, "Cup", "Alpha", "Beta",
                System.currentTimeMillis() + 3600_000, "chan1");
        assertFalse(hasField(NeonEmbeds.matchCard(plain), "Format"));
        assertFalse(hasField(NeonEmbeds.matchCard(plain), "Map Pool"));
    }

    @Test
    void leaderboardCardRanksByPoints() {
        Team alpha = new Team("Alpha", "Alice");
        alpha.wins = 5;
        Team beta = new Team("Beta", "Bob");
        beta.wins = 4;
        beta.losses = 1;
        MessageEmbed card = NeonEmbeds.leaderboardCard("Cup", List.of(alpha, beta));
        String description = card.getDescription();
        assertTrue(description.contains("Alpha"));
        assertTrue(description.contains("15"));
        assertTrue(description.indexOf("Alpha") < description.indexOf("Beta"));
        assertTrue(NeonEmbeds.leaderboardCard("Cup", List.of())
                .getDescription().contains("No teams yet."));
    }

    @Test
    void serverAndRulesCards() {
        MessageEmbed server = NeonEmbeds.serverCard(12548, 3, 42);
        assertEquals("12548", field(server, "Members"));
        assertEquals("3", field(server, "Tournaments"));
        assertTrue(field(server, "Supported Games").contains("Valorant"));
        assertTrue(field(server, "Supported Games").contains("BGMI"));
        assertFalse(NeonEmbeds.rulesCard("Cup").getDescription().isBlank());
    }

    @Test
    void prizePoolFormatAndMapsSurviveReload(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        m.createTournament("Cup", "Valorant", "Single elimination", "TBD", 0, false, "₹5,000");
        m.createTeam("Cup", "Alpha", "Alice");
        m.createTeam("Cup", "Beta", "Bob");
        m.scheduleMatch("Cup", "Alpha", "Beta", System.currentTimeMillis() + 3600_000,
                "chan1", "BO3", "Bind, Ascent");
        TournamentManager reloaded = manager(tmp);
        assertEquals("₹5,000", reloaded.snapshot().get("Cup").prizePool);
        ScheduledMatch match = reloaded.scheduledMatch("Cup", 1);
        assertEquals("BO3", match.format);
        assertEquals("Bind, Ascent", match.maps);
    }

    @Test
    void rankedTeamsOrdersByPointsThenWins(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        m.createTournament("Cup", "Valorant", "Single elimination", "TBD", 0, false);
        m.createTeam("Cup", "Alpha", "Alice");
        m.createTeam("Cup", "Beta", "Bob");
        m.submitResult("Cup", "Alpha", "Beta", "Alice", -1, -1);
        Tournament t = m.snapshot().get("Cup");
        int matchId = t.matches.get(t.matches.size() - 1).id;
        m.confirmResult("Cup", matchId, "Bob");
        List<Team> ranked = m.rankedTeams("Cup");
        assertEquals("Alpha", ranked.get(0).name);
        assertEquals("Beta", ranked.get(1).name);
    }
}

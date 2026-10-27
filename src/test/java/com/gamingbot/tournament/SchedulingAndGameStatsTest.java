package com.gamingbot.tournament;

import com.gamingbot.gamestats.GameStatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchedulingAndGameStatsTest {

    private TournamentManager manager(Path tmp) {
        return new TournamentManager("test-guild", tmp);
    }

    private void setupCup(TournamentManager m) {
        m.createTournament("Cup", "Valorant", "Single elimination", "TBD", 0, false);
        m.createTeam("Cup", "Alpha", "Alice");
        m.createTeam("Cup", "Beta", "Bob");
    }

    @Test
    void scheduleAndCheckInFlow(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        long when = System.currentTimeMillis() + 3600_000;
        String scheduled = m.scheduleMatch("Cup", "Alpha", "Beta", when, "chan1");
        assertTrue(scheduled.contains("Match #1 scheduled"), scheduled);
        assertTrue(scheduled.contains("IST"), scheduled);

        assertTrue(m.checkIn("Cup", 1, "Carol").contains("Only a captain"));
        String first = m.checkIn("Cup", 1, "Alice");
        assertTrue(first.contains("checked in"), first);
        assertTrue(first.contains("Waiting on Beta"), first);
        assertTrue(m.checkIn("Cup", 1, "Alice").contains("already checked in"));
        String second = m.checkIn("Cup", 1, "Bob");
        assertTrue(second.contains("Both teams are checked in"), second);
    }

    @Test
    void scheduleValidation(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        long when = System.currentTimeMillis() + 3600_000;
        assertTrue(m.scheduleMatch("Nope", "Alpha", "Beta", when, "c").contains("No tournament"));
        assertTrue(m.scheduleMatch("Cup", "Alpha", "Gamma", when, "c").contains("No team"));
        assertTrue(m.scheduleMatch("Cup", "Alpha", "Alpha", when, "c").contains("cannot play itself"));
        assertTrue(m.checkIn("Cup", 99, "Alice").contains("No scheduled match"));
    }

    @Test
    void upcomingListsMatchesWithCheckInStatus(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        long when = System.currentTimeMillis() + 3600_000;
        m.scheduleMatch("Cup", "Alpha", "Beta", when, "chan1");
        String list = m.upcoming("Cup");
        assertTrue(list.contains("Alpha vs Beta"), list);
        assertTrue(list.contains("IST"), list);
        assertTrue(list.contains("✗"), list);
    }

    @Test
    void reminderDueWindow(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        long now = System.currentTimeMillis();
        m.scheduleMatch("Cup", "Alpha", "Beta", now + 20 * 60_000, "chan1");
        m.createTeam("Cup", "Gamma", "Gail");
        m.createTeam("Cup", "Delta", "Dan");
        m.scheduleMatch("Cup", "Gamma", "Delta", now + 40 * 60_000, "chan1");
        List<ScheduledMatch> due = m.collectDueReminders(now);
        assertEquals(1, due.size());
        assertEquals("Alpha", due.get(0).teamA);
        assertTrue(m.collectDueReminders(now).isEmpty(), "Reminder must fire only once");
    }

    @Test
    void startingCollectionFiresOnce(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        long now = System.currentTimeMillis();
        m.scheduleMatch("Cup", "Alpha", "Beta", now - 1000, "chan1");
        List<ScheduledMatch> starting = m.collectStarting(now);
        assertEquals(1, starting.size());
        assertTrue(m.collectStarting(now).isEmpty(), "Start notice must fire only once");
    }

    @Test
    void scheduleAndLinksSurviveReload(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        m.scheduleMatch("Cup", "Alpha", "Beta", System.currentTimeMillis() + 3600_000, "chan1");
        m.linkGame("Alice", "Valorant", "Player#1234");
        TournamentManager reloaded = manager(tmp);
        assertTrue(reloaded.upcoming("Cup").contains("Alpha vs Beta"));
        assertEquals("Player#1234", reloaded.getGameLink("Alice", "Valorant"));
    }

    @Test
    void gameLinksRoundTrip(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        assertNull(m.getGameLink("Alice", "Valorant"));
        m.linkGame("Alice", "Valorant", "Player#1234");
        m.linkGame("Alice", "CS2", "76561198000000000");
        Map<String, String> links = m.linkedGames("Alice");
        assertEquals(2, links.size());
        assertEquals("76561198000000000", m.getGameLink("Alice", "CS2"));
        m.linkGame("Alice", "Valorant", "Other#9999");
        assertEquals("Other#9999", m.getGameLink("Alice", "Valorant"));
    }

    @Test
    void riotIdParsing() {
        assertEquals("Player", GameStatsService.splitRiotId("Player#1234")[0]);
        assertEquals("1234", GameStatsService.splitRiotId("Player#1234")[1]);
        assertNull(GameStatsService.splitRiotId("Player"));
        assertNull(GameStatsService.splitRiotId("Player#"));
        assertNull(GameStatsService.splitRiotId("#1234"));
        assertNull(GameStatsService.splitRiotId("A#B#C"));
    }

    @Test
    void steamIdDetection() {
        assertTrue(GameStatsService.looksLikeSteamId("76561198000000000"));
        assertFalse(GameStatsService.looksLikeSteamId("somevanity"));
        assertFalse(GameStatsService.looksLikeSteamId("123"));
    }

    @Test
    void unconfiguredServiceReportsMissingKeys() throws Exception {
        GameStatsService service = new GameStatsService(null, null);
        assertFalse(service.riotConfigured());
        assertFalse(service.steamConfigured());
        assertTrue(service.fetchValorantSummary("Player#1234").contains("RIOT_API_KEY"));
        assertTrue(service.fetchSteamSummary("76561198000000000").contains("STEAM_API_KEY"));
    }
}

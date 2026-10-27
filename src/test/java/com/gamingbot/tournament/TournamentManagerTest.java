package com.gamingbot.tournament;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TournamentManagerTest {

    private TournamentManager manager(Path tmp) {
        return new TournamentManager("test-guild", tmp);
    }

    private void setupCup(TournamentManager m) {
        m.createTournament("Cup", "Valorant", "Single elimination", "TBD", 0, false);
        m.createTeam("Cup", "Alpha", "Alice");
        m.createTeam("Cup", "Beta", "Bob");
    }

    private void confirmWin(TournamentManager m, String tournament, String winner,
                            String loser, String winnerCaptain, String loserCaptain) {
        m.submitResult(tournament, winner, loser, winnerCaptain, -1, -1);
        Tournament t = m.snapshot().get(tournament);
        int matchId = t.matches.get(t.matches.size() - 1).id;
        String reply = m.confirmResult(tournament, matchId, loserCaptain);
        assertTrue(reply.contains("confirmed"), "Expected confirmation, got: " + reply);
    }

    @Test
    void soloRegistrationAutoClosesWhenFull(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        m.createTournament("SoloCup", "Valorant", "Single elimination", "TBD", 2, true);
        m.registerSolo("SoloCup", "Alice");
        m.registerSolo("SoloCup", "Bob");
        Tournament t = m.snapshot().get("SoloCup");
        assertEquals(2, t.teams.size());
        assertFalse(t.registrationOpen);
        assertTrue(m.registerSolo("SoloCup", "Carol").contains("closed"));
    }

    @Test
    void oneTeamPerPlayer(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        String reply = m.joinTeam("Cup", "Beta", "Alice");
        assertTrue(reply.contains("already on"));
        assertFalse(m.snapshot().get("Cup").teams.get("Beta").players.contains("Alice"));
    }

    @Test
    void confirmAwardsPointsOnlyToOpposingCaptain(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        m.submitResult("Cup", "Alpha", "Beta", "Alice", -1, -1);
        String wrongCaptain = m.confirmResult("Cup", 1, "Alice");
        assertTrue(wrongCaptain.contains("opposing"));
        assertEquals(0, m.snapshot().get("Cup").teams.get("Alpha").wins);
        String ok = m.confirmResult("Cup", 1, "Bob");
        assertTrue(ok.contains("confirmed"));
        assertEquals(1, m.snapshot().get("Cup").teams.get("Alpha").wins);
        assertTrue(m.playerLeaderboard().contains("Alice - 3 pts"));
    }

    @Test
    void disputeBlocksConfirmation(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        m.submitResult("Cup", "Alpha", "Beta", "Alice", -1, -1);
        m.disputeResult("Cup", 1);
        String reply = m.confirmResult("Cup", 1, "Bob");
        assertTrue(reply.contains("already DISPUTED"));
        assertEquals(0, m.snapshot().get("Cup").teams.get("Alpha").wins);
    }

    @Test
    void correctResultRecalculatesPoints(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        confirmWin(m, "Cup", "Alpha", "Beta", "Alice", "Bob");
        m.correctResult("Cup", 1, "Beta", "Alpha");
        Tournament t = m.snapshot().get("Cup");
        assertEquals(0, t.teams.get("Alpha").wins);
        assertEquals(1, t.teams.get("Beta").wins);
    }

    @Test
    void singleEliminationBracketAdvancesWinners(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        m.createTournament("Cup", "Valorant", "Single elimination", "TBD", 0, false);
        m.createTeam("Cup", "A", "CapA");
        m.createTeam("Cup", "B", "CapB");
        m.createTeam("Cup", "C", "CapC");
        m.createTeam("Cup", "D", "CapD");
        m.bracket("Cup");
        List<MatchRecord> round1 = m.snapshot().get("Cup").matches.stream()
                .filter(x -> "S".equals(x.bracket) && x.round == 1).toList();
        assertEquals(2, round1.size());
        confirmWin(m, "Cup", "A", "B", "CapA", "CapB");
        confirmWin(m, "Cup", "C", "D", "CapC", "CapD");
        List<MatchRecord> round2 = m.snapshot().get("Cup").matches.stream()
                .filter(x -> "S".equals(x.bracket) && x.round == 2).toList();
        assertEquals(1, round2.size());
        assertTrue((round2.get(0).teamA.equals("A") && round2.get(0).teamB.equals("C"))
                || (round2.get(0).teamA.equals("C") && round2.get(0).teamB.equals("A")));
        confirmWin(m, "Cup", "A", "C", "CapA", "CapC");
        assertEquals("A", m.snapshot().get("Cup").champion);
    }

    @Test
    void doubleEliminationFinalWithReset(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        m.createTournament("Cup", "Valorant", "Double elimination", "TBD", 0, false);
        m.createTeam("Cup", "A", "CapA");
        m.createTeam("Cup", "B", "CapB");
        m.bracket("Cup");
        confirmWin(m, "Cup", "A", "B", "CapA", "CapB");
        Tournament t = m.snapshot().get("Cup");
        assertTrue(t.matches.stream().anyMatch(x -> "F".equals(x.bracket)),
                "Final should start once both champions are known");
        // Losers-bracket champion wins the first final: forces a reset match.
        confirmWin(m, "Cup", "B", "A", "CapB", "CapA");
        t = m.snapshot().get("Cup");
        assertEquals(2, t.matches.stream().filter(x -> "F".equals(x.bracket)).count());
        // Winners-bracket champion wins the reset: champion decided.
        confirmWin(m, "Cup", "A", "B", "CapA", "CapB");
        assertEquals("A", m.snapshot().get("Cup").champion);
    }

    @Test
    void dataSurvivesReloadInSameGuild(@TempDir Path tmp) {
        TournamentManager m = manager(tmp);
        setupCup(m);
        TournamentManager reloaded = new TournamentManager("test-guild", tmp);
        assertTrue(reloaded.snapshot().containsKey("Cup"));
        assertEquals(2, reloaded.snapshot().get("Cup").teams.size());
    }

    @Test
    void serversDoNotShareData(@TempDir Path tmp) {
        Managers managers = new Managers(tmp);
        managers.get("guild-one").createTournament("Cup", "Valorant",
                "Single elimination", "TBD", 0, false);
        assertTrue(managers.get("guild-two").snapshot().isEmpty());
        assertFalse(managers.get("guild-one").snapshot().isEmpty());
    }
}

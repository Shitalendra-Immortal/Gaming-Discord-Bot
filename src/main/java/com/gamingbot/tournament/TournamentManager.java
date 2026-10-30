package com.gamingbot.tournament;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TournamentManager {
    private final Path guildDir;
    private final Path db;
    private final Map<String, Tournament> tournaments;
    private final Map<String, PlayerProfile> profiles;
    private final Map<String, List<ScheduledMatch>> scheduled;
    private final Map<String, Map<String, String>> links;

    public TournamentManager(String guildKey) {
        this(guildKey, Path.of("data"));
    }

    public TournamentManager(String guildKey, Path baseDir) {
        this.guildDir = baseDir.resolve(guildKey);
        this.db = SqliteStore.dbFile(guildDir);
        SqliteStore.importLegacyJson(db, guildDir);
        this.tournaments = new ConcurrentHashMap<>(SqliteStore.loadTournaments(db));
        SqliteStore.mergeMirrorTournaments(db, guildDir, this.tournaments);
        this.profiles = new ConcurrentHashMap<>(SqliteStore.loadPlayers(db));
        this.scheduled = new ConcurrentHashMap<>(SqliteStore.loadScheduled(db));
        this.links = new ConcurrentHashMap<>(SqliteStore.loadLinks(db));
    }

    public synchronized int tournamentCount() {
        return tournaments.size();
    }

    public synchronized int teamCount() {
        return tournaments.values().stream().mapToInt(t -> t.teams.size()).sum();
    }

    private void save() {
        SqliteStore.saveTournaments(db, tournaments);
        SqliteStore.savePlayers(db, profiles);
        SqliteStore.saveScheduled(db, scheduled);
        SqliteStore.saveLinks(db, links);
    }

    public static String formatIst(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
    }

    public synchronized String scheduleMatch(String tournamentName, String teamA,
                                             String teamB, long epochMillis, String channelId) {
        return scheduleMatch(tournamentName, teamA, teamB, epochMillis, channelId, "", "");
    }

    public synchronized String scheduleMatch(String tournamentName, String teamA,
                                             String teamB, long epochMillis, String channelId,
                                             String format, String maps) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        if (!t.teams.containsKey(teamA)) {
            return "No team called '" + teamA + "' in this tournament.";
        }
        if (!t.teams.containsKey(teamB)) {
            return "No team called '" + teamB + "' in this tournament.";
        }
        if (teamA.equals(teamB)) {
            return "A team cannot play itself.";
        }
        List<ScheduledMatch> matches = scheduled.computeIfAbsent(tournamentName,
                k -> new ArrayList<>());
        int id = 1;
        for (ScheduledMatch m : matches) {
            id = Math.max(id, m.id + 1);
        }
        matches.add(new ScheduledMatch(id, tournamentName, teamA, teamB, epochMillis, channelId,
                format, maps));
        save();
        return "Match #" + id + " scheduled: " + teamA + " vs " + teamB + " on "
                + formatIst(epochMillis)
                + " IST. Captains, check in with /check-in before it starts.";
    }

    public synchronized String checkIn(String tournamentName, int matchId, String user) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        ScheduledMatch m = findScheduled(tournamentName, matchId);
        if (m == null) {
            return "No scheduled match #" + matchId + " in '" + tournamentName + "'.";
        }
        if (System.currentTimeMillis() > m.scheduledAt + 15 * 60 * 1000) {
            return "Check-in is closed for match #" + matchId + " (it started over 15 minutes ago).";
        }
        String team = null;
        boolean isA = false;
        if (t.teams.containsKey(m.teamA) && t.teams.get(m.teamA).captain.equals(user)) {
            team = m.teamA;
            isA = true;
        } else if (t.teams.containsKey(m.teamB) && t.teams.get(m.teamB).captain.equals(user)) {
            team = m.teamB;
        }
        if (team == null) {
            return "Only a captain of " + m.teamA + " or " + m.teamB + " can check in.";
        }
        if (isA ? m.checkedA : m.checkedB) {
            return team + " is already checked in for match #" + matchId + ".";
        }
        if (isA) {
            m.checkedA = true;
        } else {
            m.checkedB = true;
        }
        save();
        if (m.checkedA && m.checkedB) {
            return team + " checked in. Both teams are checked in — match #" + matchId + " is ready!";
        }
        String waiting = isA ? m.teamB : m.teamA;
        return team + " checked in for match #" + matchId + ". Waiting on " + waiting + ".";
    }

    public synchronized ScheduledMatch scheduledMatch(String tournamentName, int matchId) {
        return findScheduled(tournamentName, matchId);
    }

    public synchronized List<Team> rankedTeams(String tournamentName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return List.of();
        }
        List<Team> teams = new ArrayList<>(t.teams.values());
        teams.sort((a, b) -> {
            int byPoints = Integer.compare(b.points(), a.points());
            return byPoints != 0 ? byPoints : Integer.compare(b.wins, a.wins);
        });
        return teams;
    }

    public synchronized List<ScheduledMatch> upcomingMatches(String tournamentName) {
        long now = System.currentTimeMillis();
        List<ScheduledMatch> future = new ArrayList<>();
        for (ScheduledMatch m : scheduled.getOrDefault(tournamentName, List.of())) {
            if (m.scheduledAt + 15 * 60 * 1000 >= now) {
                future.add(m);
            }
        }
        future.sort(java.util.Comparator.comparingLong(m -> m.scheduledAt));
        return future;
    }

    private ScheduledMatch findScheduled(String tournamentName, int matchId) {
        List<ScheduledMatch> matches = scheduled.getOrDefault(tournamentName, List.of());
        for (ScheduledMatch m : matches) {
            if (m.id == matchId) {
                return m;
            }
        }
        return null;
    }

    public synchronized String upcoming(String tournamentName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        long now = System.currentTimeMillis();
        List<ScheduledMatch> future = new ArrayList<>();
        for (ScheduledMatch m : scheduled.getOrDefault(tournamentName, List.of())) {
            if (m.scheduledAt + 15 * 60 * 1000 >= now) {
                future.add(m);
            }
        }
        if (future.isEmpty()) {
            return "No upcoming matches scheduled in '" + tournamentName + "'.";
        }
        future.sort(java.util.Comparator.comparingLong(m -> m.scheduledAt));
        StringBuilder out = new StringBuilder("**Upcoming matches: ").append(tournamentName).append("**\n");
        for (ScheduledMatch m : future) {
            out.append("#").append(m.id).append(" ").append(m.teamA).append(" vs ").append(m.teamB)
                    .append(" — ").append(formatIst(m.scheduledAt)).append(" IST — check-in: ")
                    .append(m.teamA).append(m.checkedA ? " ✓" : " ✗").append(", ")
                    .append(m.teamB).append(m.checkedB ? " ✓" : " ✗").append("\n");
        }
        return out.toString();
    }

    public synchronized List<ScheduledMatch> collectDueReminders(long now) {
        List<ScheduledMatch> due = new ArrayList<>();
        boolean changed = false;
        for (List<ScheduledMatch> matches : scheduled.values()) {
            for (ScheduledMatch m : matches) {
                if (m.reminded || m.scheduledAt <= now) {
                    continue;
                }
                if (m.checkedA && m.checkedB) {
                    m.reminded = true;
                    changed = true;
                    continue;
                }
                if (m.scheduledAt - now <= 30 * 60 * 1000) {
                    m.reminded = true;
                    changed = true;
                    due.add(m);
                }
            }
        }
        if (changed) {
            save();
        }
        return due;
    }

    public synchronized List<ScheduledMatch> collectStarting(long now) {
        List<ScheduledMatch> starting = new ArrayList<>();
        boolean changed = false;
        for (List<ScheduledMatch> matches : scheduled.values()) {
            for (ScheduledMatch m : matches) {
                if (!m.startedNotified && now >= m.scheduledAt) {
                    m.startedNotified = true;
                    changed = true;
                    starting.add(m);
                }
            }
        }
        if (changed) {
            save();
        }
        return starting;
    }

    public synchronized String linkGame(String user, String game, String gameId) {
        links.computeIfAbsent(user, k -> new LinkedHashMap<>()).put(game, gameId);
        save();
        return "Linked " + game + " account " + gameId + " for " + user + ".";
    }

    public synchronized String getGameLink(String user, String game) {
        Map<String, String> userLinks = links.get(user);
        return userLinks == null ? null : userLinks.get(game);
    }

    public synchronized Map<String, String> linkedGames(String user) {
        Map<String, String> userLinks = links.get(user);
        return userLinks == null ? Map.of() : new LinkedHashMap<>(userLinks);
    }

    private PlayerProfile profile(String user) {
        return profiles.computeIfAbsent(user, PlayerProfile::new);
    }

    public synchronized Map<String, Tournament> snapshot() {
        return new LinkedHashMap<>(tournaments);
    }

    private void applyWin(Team team, String tournamentName) {
        team.wins++;
        for (String player : team.players) {
            PlayerProfile p = profile(player);
            p.wins++;
            p.matchHistory.add(tournamentName + ": win");
        }
    }

    private void applyLoss(Team team, String tournamentName) {
        team.losses++;
        for (String player : team.players) {
            PlayerProfile p = profile(player);
            p.losses++;
            p.matchHistory.add(tournamentName + ": loss");
        }
    }

    private void revertWin(Team team, String tournamentName) {
        if (team.wins > 0) {
            team.wins--;
        }
        for (String player : team.players) {
            PlayerProfile p = profile(player);
            if (p.wins > 0) {
                p.wins--;
            }
            p.matchHistory.remove(tournamentName + ": win");
        }
    }

    private void revertLoss(Team team, String tournamentName) {
        if (team.losses > 0) {
            team.losses--;
        }
        for (String player : team.players) {
            PlayerProfile p = profile(player);
            if (p.losses > 0) {
                p.losses--;
            }
            p.matchHistory.remove(tournamentName + ": loss");
        }
    }

    public synchronized String createTournament(String name, String game, String format,
                                                String date, int teamLimit, boolean soloAllowed) {
        return createTournament(name, game, format, date, teamLimit, soloAllowed, "");
    }

    public synchronized String createTournament(String name, String game, String format,
                                                String date, int teamLimit, boolean soloAllowed,
                                                String prizePool) {
        if (tournaments.containsKey(name)) {
            return "A tournament called '" + name + "' already exists.";
        }
        Tournament t = new Tournament(name, game, format, date, teamLimit, soloAllowed);
        t.prizePool = prizePool == null ? "" : prizePool;
        tournaments.put(name, t);
        save();
        return "Tournament '" + name + "' (" + game + ", " + format + ", " + date
                + ") created. Registration is open.";
    }

    public synchronized String listTournaments() {
        if (tournaments.isEmpty()) {
            return "No tournaments yet. Create one with /tournament-create.";
        }
        StringBuilder out = new StringBuilder("**Tournaments**\n");
        tournaments.values().forEach(t ->
                out.append("- ").append(t.name).append(" (").append(t.game).append(", ")
                        .append(t.format).append(", ").append(t.date).append(", ")
                        .append(t.teams.size()).append(" teams, registration ")
                        .append(t.registrationOpen ? "open" : "closed").append(")\n"));
        return out.toString();
    }

    public synchronized String createTeam(String tournamentName, String teamName, String captain) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        if (!t.registrationOpen) {
            return "Registration for '" + tournamentName + "' is closed.";
        }
        if (t.teams.containsKey(teamName)) {
            return "Team '" + teamName + "' already exists in this tournament.";
        }
        t.teams.put(teamName, new Team(teamName, captain));
        profile(captain);
        closeIfFull(t);
        save();
        return "Team '" + teamName + "' created in '" + tournamentName
                + "' with captain " + captain + ".";
    }

    public synchronized String inviteTeam(String tournamentName, String teamName, String player) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        Team team = t.teams.get(teamName);
        if (team == null) {
            return "No team called '" + teamName + "' in this tournament.";
        }
        if (!team.players.contains(player)) {
            team.players.add(player);
        }
        profile(player);
        save();
        return player + " was invited to '" + teamName + "'.";
    }

    public synchronized String joinTeam(String tournamentName, String teamName, String player) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        if (!t.registrationOpen) {
            return "Registration for '" + tournamentName + "' is closed.";
        }
        Team team = t.teams.get(teamName);
        if (team == null) {
            return "No team called '" + teamName + "' in this tournament.";
        }
        for (Team other : t.teams.values()) {
            if (!other.name.equals(teamName) && other.players.contains(player)) {
                return "You are already on '" + other.name
                        + "' in this tournament. One team per player.";
            }
        }
        if (!team.players.contains(player)) {
            team.players.add(player);
        }
        profile(player);
        closeIfFull(t);
        save();
        return player + " joined '" + teamName + "'.";
    }

    public synchronized String registerSolo(String tournamentName, String player) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        if (!t.soloAllowed) {
            return "Solo registration is not allowed in '" + tournamentName
                    + "'. Join a team with /team-join instead.";
        }
        if (!t.registrationOpen) {
            return "Registration for '" + tournamentName + "' is closed.";
        }
        for (Team other : t.teams.values()) {
            if (other.players.contains(player)) {
                return "You are already registered in '" + tournamentName + "'.";
            }
        }
        t.teams.put(player, new Team(player, player));
        profile(player);
        closeIfFull(t);
        save();
        return player + " registered solo in '" + tournamentName + "'.";
    }

    private void closeIfFull(Tournament t) {
        if (t.teamLimit > 0 && t.teams.size() >= t.teamLimit) {
            t.registrationOpen = false;
        }
    }

    public synchronized String submitResult(String tournamentName, String winnerName,
                                            String loserName, String reporter,
                                            int scoreWinner, int scoreLoser) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        if (winnerName.equals(loserName)) {
            return "Winner and loser must be different teams.";
        }
        Team winnerTeam = t.teams.get(winnerName);
        Team loserTeam = t.teams.get(loserName);
        if (winnerTeam == null || loserTeam == null) {
            return "Both teams must exist in this tournament first.";
        }
        if (!winnerTeam.captain.equals(reporter)) {
            return "Only the winning team's captain can submit a result.";
        }
        int id = nextMatchId(t);
        MatchRecord record = new MatchRecord(id, winnerName, loserName, winnerName, loserName, reporter);
        record.scoreWinner = scoreWinner;
        record.scoreLoser = scoreLoser;
        t.matches.add(record);
        save();
        String score = scoreWinner >= 0 && scoreLoser >= 0
                ? " (score " + scoreWinner + "-" + scoreLoser + ")" : "";
        return "Match #" + id + " submitted: " + winnerName + " beat " + loserName + score
                + ". It is pending confirmation by the opposing captain with /confirm-result. The other side can dispute it with /dispute-result. No leaderboard points are applied until it is confirmed.";
    }

    private MatchRecord findMatch(Tournament t, int id) {
        for (MatchRecord m : t.matches) {
            if (m.id == id) {
                return m;
            }
        }
        return null;
    }

    public synchronized String confirmResult(String tournamentName, int matchId, String confirmer) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        MatchRecord m = findMatch(t, matchId);
        if (m == null) {
            return "No match #" + matchId + " in '" + tournamentName + "'.";
        }
        if (!m.status.equals("PENDING")) {
            return "Match #" + matchId + " is already " + m.status + ".";
        }
        Team winner = t.teams.get(m.winner);
        Team loser = t.teams.get(m.loser);
        if (winner == null || loser == null) {
            return "The teams in this match no longer exist.";
        }
        if (!loser.captain.equals(confirmer)) {
            return "Only the opposing team's captain can confirm this result.";
        }
        applyWin(winner, tournamentName);
        applyLoss(loser, tournamentName);
        m.status = "CONFIRMED";
        if (!t.advancedTeams.contains(m.winner)) {
            t.advancedTeams.add(m.winner);
        }
        advanceBracket(t, m.winner, m.loser);
        save();
        return "Match #" + matchId + " confirmed: " + m.winner + " beat " + m.loser + " (+3 points).";
    }

    public synchronized String disputeResult(String tournamentName, int matchId) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        MatchRecord m = findMatch(t, matchId);
        if (m == null) {
            return "No match #" + matchId + " in '" + tournamentName + "'.";
        }
        if (!m.status.equals("PENDING")) {
            return "Only a pending match can be disputed. Match #" + matchId + " is " + m.status + ".";
        }
        m.status = "DISPUTED";
        save();
        return "Match #" + matchId + " disputed. No points applied until a moderator corrects it with /correct-result.";
    }

    public synchronized String correctResult(String tournamentName, int matchId,
                                             String winnerName, String loserName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        MatchRecord m = findMatch(t, matchId);
        if (m == null) {
            return "No match #" + matchId + " in '" + tournamentName + "'.";
        }
        Team newWinner = t.teams.get(winnerName);
        Team newLoser = t.teams.get(loserName);
        if (newWinner == null || newLoser == null) {
            return "Both teams must exist in this tournament first.";
        }
        if (m.status.equals("CONFIRMED")) {
            Team oldWinner = t.teams.get(m.winner);
            Team oldLoser = t.teams.get(m.loser);
            if (oldWinner != null) {
                revertWin(oldWinner, tournamentName);
            }
            if (oldLoser != null) {
                revertLoss(oldLoser, tournamentName);
            }
        }
        applyWin(newWinner, tournamentName);
        applyLoss(newLoser, tournamentName);
        m.winner = winnerName;
        m.loser = loserName;
        m.status = "CONFIRMED";
        save();
        return "Match #" + matchId + " corrected to " + winnerName + " beat " + loserName
                + ". Rankings recalculated.";
    }

    public synchronized String bracket(String tournamentName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        boolean hasBracketMatches = t.matches.stream()
                .anyMatch(m -> m.bracket != null && !m.bracket.isEmpty());
        if (hasBracketMatches) {
            return renderBracket(t);
        }
        List<String> names = new ArrayList<>(t.teams.keySet());
        if (names.size() < 2) {
            return "Need at least 2 teams to generate a bracket.";
        }
        String format = t.format == null ? "" : t.format.toLowerCase();
        t.bracket = new ArrayList<>();
        if (format.contains("round")) {
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    MatchRecord m = new MatchRecord();
                    m.id = nextMatchId(t);
                    m.teamA = names.get(i);
                    m.teamB = names.get(j);
                    m.bracket = "R";
                    t.matches.add(m);
                    t.bracket.add("Match [#" + m.id + "]: " + m.teamA + " vs " + m.teamB);
                }
            }
        } else if (format.contains("double")) {
            t.bracketRound = 1;
            t.losersRound = 1;
            t.bracketPool.clear();
            t.losersPool.clear();
            t.finalStage = 0;
            t.winnersChampion = "";
            t.losersChampion = "";
            t.champion = "";
            createPairings(t, names, 1, "W");
        } else {
            t.bracketRound = 1;
            t.bracketPool.clear();
            t.champion = "";
            createPairings(t, names, 1, "S");
        }
        save();
        return renderBracket(t);
    }

    private String renderBracket(Tournament t) {
        StringBuilder out = new StringBuilder("**Bracket: ").append(t.name)
                .append(" (").append(t.format).append(", ").append(t.date).append(")**\n");
        t.bracket.forEach(p -> out.append("- ").append(p).append("\n"));
        if (!t.advancedTeams.isEmpty()) {
            out.append("Advanced so far: ").append(String.join(", ", t.advancedTeams)).append("\n");
        }
        if (!t.withdrawnTeams.isEmpty()) {
            out.append("Withdrawn: ").append(String.join(", ", t.withdrawnTeams)).append("\n");
        }
        if (t.champion != null && !t.champion.isEmpty()) {
            out.append("Champion: ").append(t.champion).append("\n");
        }
        return out.toString();
    }

    private int nextMatchId(Tournament t) {
        int max = 0;
        for (MatchRecord m : t.matches) {
            max = Math.max(max, m.id);
        }
        return max + 1;
    }

    private String bracketLabel(String bracket) {
        switch (bracket) {
            case "W":
                return "Winners bracket";
            case "L":
                return "Losers bracket";
            case "F":
                return "Final";
            case "S":
                return "Single elimination";
            default:
                return "Bracket";
        }
    }

    private void createPairings(Tournament t, List<String> teams, int round, String bracket) {
        for (int i = 0; i + 1 < teams.size(); i += 2) {
            MatchRecord m = new MatchRecord();
            m.id = nextMatchId(t);
            m.teamA = teams.get(i);
            m.teamB = teams.get(i + 1);
            m.round = round;
            m.bracket = bracket;
            t.matches.add(m);
            t.bracket.add("Round " + round + " (" + bracketLabel(bracket) + "): "
                    + m.teamA + " vs " + m.teamB + " [#" + m.id + "]");
        }
        if (teams.size() % 2 == 1) {
            String bye = teams.get(teams.size() - 1);
            t.bracket.add("Bye: " + bye + " advances automatically.");
            if (bracket.equals("L")) {
                t.losersPool.add(bye);
            } else {
                t.bracketPool.add(bye);
            }
        }
    }

    private boolean roundComplete(Tournament t, int round, String bracket) {
        boolean any = false;
        for (MatchRecord m : t.matches) {
            if (m.round == round && bracket.equals(m.bracket)) {
                any = true;
                if (m.status.equals("PENDING")) {
                    return false;
                }
            }
        }
        return any;
    }

    private List<String> roundWinners(Tournament t, int round, String bracket) {
        List<String> winners = new ArrayList<>();
        for (MatchRecord m : t.matches) {
            if (m.round == round && bracket.equals(m.bracket) && m.status.equals("CONFIRMED")) {
                winners.add(m.winner);
            }
        }
        return winners;
    }

    private void formLosersRound(Tournament t) {
        if (t.losersPool.size() < 2) {
            return;
        }
        List<String> teams = new ArrayList<>(t.losersPool);
        t.losersPool.clear();
        createPairings(t, teams, t.losersRound, "L");
    }

    private void maybeStartFinal(Tournament t) {
        if (t.finalStage != 0 || t.winnersChampion == null || t.winnersChampion.isEmpty()) {
            return;
        }
        if (t.losersPool.size() == 1) {
            t.losersChampion = t.losersPool.get(0);
            t.losersPool.clear();
            MatchRecord f = new MatchRecord();
            f.id = nextMatchId(t);
            f.teamA = t.winnersChampion;
            f.teamB = t.losersChampion;
            f.round = 1;
            f.bracket = "F";
            t.matches.add(f);
            t.finalStage = 1;
            t.bracket.add("Final: " + f.teamA + " vs " + f.teamB + " [#" + f.id + "]");
        }
    }

    /**
     * Advances the bracket from a confirmed manual result. Bracket pairings are
     * scheduled matches; when the same pair's result is confirmed, the pairing
     * is decided and winners move on. Corrections recalculate points but do
     * not rewind bracket progression.
     */
    private void advanceBracket(Tournament t, String winner, String loser) {
        MatchRecord pairing = null;
        for (MatchRecord m : t.matches) {
            if (!m.status.equals("PENDING") || m.bracket == null || m.bracket.isEmpty()) {
                continue;
            }
            boolean samePair = (m.teamA.equals(winner) && m.teamB.equals(loser))
                    || (m.teamA.equals(loser) && m.teamB.equals(winner));
            if (samePair) {
                pairing = m;
                break;
            }
        }
        if (pairing == null) {
            return;
        }
        pairing.winner = winner;
        pairing.loser = loser;
        pairing.status = "CONFIRMED";
        String bracket = pairing.bracket;
        if (bracket.equals("R")) {
            return;
        }
        if (bracket.equals("S")) {
            if (roundComplete(t, pairing.round, "S")) {
                List<String> next = new ArrayList<>(roundWinners(t, pairing.round, "S"));
                next.addAll(t.bracketPool);
                t.bracketPool.clear();
                if (next.size() == 1) {
                    t.champion = next.get(0);
                    t.bracket.add("Champion: " + t.champion);
                } else {
                    t.bracketRound = pairing.round + 1;
                    createPairings(t, next, t.bracketRound, "S");
                }
            }
            return;
        }
        if (bracket.equals("W")) {
            t.losersPool.add(loser);
            if (roundComplete(t, pairing.round, "W")) {
                List<String> next = new ArrayList<>(roundWinners(t, pairing.round, "W"));
                next.addAll(t.bracketPool);
                t.bracketPool.clear();
                if (next.size() == 1) {
                    t.winnersChampion = next.get(0);
                } else {
                    t.bracketRound = pairing.round + 1;
                    createPairings(t, next, t.bracketRound, "W");
                }
            }
            formLosersRound(t);
            maybeStartFinal(t);
            return;
        }
        if (bracket.equals("L")) {
            if (roundComplete(t, pairing.round, "L")) {
                t.losersRound = pairing.round + 1;
                formLosersRound(t);
            }
            maybeStartFinal(t);
            return;
        }
        if (bracket.equals("F")) {
            if (t.finalStage == 1) {
                if (winner.equals(t.winnersChampion)) {
                    t.champion = t.winnersChampion;
                    t.finalStage = 3;
                    t.bracket.add("Champion: " + t.champion);
                } else {
                    t.finalStage = 2;
                    MatchRecord reset = new MatchRecord();
                    reset.id = nextMatchId(t);
                    reset.teamA = t.winnersChampion;
                    reset.teamB = t.losersChampion;
                    reset.round = pairing.round + 1;
                    reset.bracket = "F";
                    t.matches.add(reset);
                    t.bracket.add("Final reset: " + reset.teamA + " vs " + reset.teamB
                            + " [#" + reset.id + "]");
                }
            } else if (t.finalStage == 2) {
                t.champion = winner;
                t.finalStage = 3;
                t.bracket.add("Champion: " + t.champion);
            }
        }
    }

    public synchronized String withdrawTeam(String tournamentName, String teamName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        Team removed = t.teams.remove(teamName);
        if (removed == null) {
            return "No team called '" + teamName + "' in this tournament.";
        }
        t.withdrawnTeams.add(teamName);
        for (MatchRecord m : new ArrayList<>(t.matches)) {
            if (!m.status.equals("PENDING") || m.bracket == null || m.bracket.isEmpty()) {
                continue;
            }
            String opponent = null;
            if (m.teamA.equals(teamName)) {
                opponent = m.teamB;
            } else if (m.teamB.equals(teamName)) {
                opponent = m.teamA;
            } else {
                continue;
            }
            t.matches.remove(m);
            if (m.bracket.equals("F")) {
                t.champion = opponent;
                t.finalStage = 3;
                t.bracket.add("Champion by withdrawal: " + opponent);
            } else if (m.bracket.equals("L")) {
                t.losersPool.add(opponent);
            } else {
                t.bracketPool.add(opponent);
            }
        }
        if (t.teamLimit > 0 && t.teams.size() < t.teamLimit) {
            t.registrationOpen = true;
        }
        save();
        return "Team '" + teamName + "' withdrew from '" + tournamentName
                + "'. Its remaining matches are void; the bracket advances its opponents.";
    }

    public synchronized String getTeamCaptain(String tournamentName, String teamName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return null;
        }
        Team team = t.teams.get(teamName);
        return team == null ? null : team.captain;
    }

    public synchronized String playerStats(String user) {
        PlayerProfile p = profiles.get(user);
        if (p == null) {
            return "No recorded matches for " + user + " yet.";
        }
        int played = p.wins + p.losses;
        int winRate = played == 0 ? 0 : p.wins * 100 / played;
        StringBuilder recent = new StringBuilder();
        for (int i = p.matchHistory.size() - 1; i >= 0 && i >= p.matchHistory.size() - 5; i--) {
            recent.append(p.matchHistory.get(i)).append("; ");
        }
        return "**Stats for " + user + "**\n"
                + "Matches played: " + played + "\n"
                + "Win rate: " + winRate + "% (" + p.wins + "W-" + p.losses + "L)\n"
                + "Tournament wins: " + p.tournamentWins + "\n"
                + "Recent form: " + (recent.length() == 0 ? "none yet" : recent.toString()) + "\n"
                + "Note: these statistics come from matches recorded by this bot, not from a verified game-data source.";
    }

    public synchronized String leaderboard(String tournamentName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        List<Team> teams = new ArrayList<>(t.teams.values());
        teams.sort((a, b) -> {
            int byPoints = Integer.compare(b.points(), a.points());
            return byPoints != 0 ? byPoints : Integer.compare(b.wins, a.wins);
        });
        if (teams.isEmpty()) {
            return "No teams in '" + tournamentName + "' yet.";
        }
        StringBuilder out = new StringBuilder("**Team leaderboard: ").append(tournamentName).append("**\n");
        int rank = 1;
        for (Team team : teams) {
            out.append(rank++).append(". ").append(team.name)
                    .append(" - ").append(team.points()).append(" pts (")
                    .append(team.wins).append("W-").append(team.losses).append("L, captain ")
                    .append(team.captain.isBlank() ? "none" : team.captain).append(")\n");
        }
        return out.toString();
    }

    public synchronized String playerLeaderboard() {
        return playerLeaderboard(1);
    }

    public synchronized String playerLeaderboard(int page) {
        List<PlayerProfile> players = new ArrayList<>(profiles.values());
        players.sort((a, b) -> {
            int byPoints = Integer.compare(b.points(), a.points());
            return byPoints != 0 ? byPoints : Integer.compare(b.wins, a.wins);
        });
        if (players.isEmpty()) {
            return "No player records yet.";
        }
        int perPage = 10;
        int safePage = Math.max(1, page);
        int start = (safePage - 1) * perPage;
        if (start >= players.size()) {
            return "No players on page " + safePage + ".";
        }
        StringBuilder out = new StringBuilder("**Player leaderboard — page ")
                .append(safePage).append("**\n");
        for (int i = start; i < Math.min(start + perPage, players.size()); i++) {
            PlayerProfile p = players.get(i);
            out.append(i + 1).append(". ").append(p.user)
                    .append(" - ").append(p.points()).append(" pts (")
                    .append(p.wins).append("W-").append(p.losses).append("L)\n");
        }
        return out.toString();
    }

    public synchronized String announceMatch(String tournamentName, String teamA, String teamB) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        return "Upcoming match in '" + tournamentName + "' (" + t.date + "): "
                + teamA + " vs " + teamB + ". Registered players, get ready!";
    }

    public synchronized String announceWinner(String tournamentName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        List<Team> teams = new ArrayList<>(t.teams.values());
        if (teams.isEmpty()) {
            return "No teams in '" + tournamentName + "' yet.";
        }
        teams.sort((a, b) -> {
            int byPoints = Integer.compare(b.points(), a.points());
            return byPoints != 0 ? byPoints : Integer.compare(b.wins, a.wins);
        });
        Team winner = teams.get(0);
        for (String player : winner.players) {
            profile(player).tournamentWins++;
        }
        save();
        return "Tournament '" + tournamentName + "' winner: " + winner.name + ". Congratulations!";
    }

    public synchronized String remindCaptains(String tournamentName) {
        Tournament t = tournaments.get(tournamentName);
        if (t == null) {
            return "No tournament called '" + tournamentName + "'.";
        }
        StringBuilder out = new StringBuilder();
        boolean any = false;
        for (MatchRecord m : t.matches) {
            if (m.status.equals("PENDING")) {
                any = true;
                Team winner = t.teams.get(m.winner);
                Team loser = t.teams.get(m.loser);
                out.append("Match #").append(m.id).append(" pending: captains ")
                        .append(winner == null ? "?" : winner.captain).append(" and ")
                        .append(loser == null ? "?" : loser.captain)
                        .append(", please confirm or dispute the result.\n");
            }
        }
        if (!any) {
            return "No pending results in '" + tournamentName + "'.";
        }
        return out.toString();
    }

    public synchronized String notifyOpt(String player, String type, boolean enabled) {
        PlayerProfile p = profile(player);
        switch (type) {
            case "matches":
                p.notifyMatches = enabled;
                break;
            case "results":
                p.notifyResults = enabled;
                break;
            case "announcements":
                p.notifyAnnouncements = enabled;
                break;
            default:
                return "Unknown notification type. Use matches, results, or announcements.";
        }
        save();
        return player + " notifications for " + type + " are now " + (enabled ? "on" : "off") + ".";
    }
}

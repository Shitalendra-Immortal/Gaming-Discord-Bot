package com.gamingbot.tournament;

import com.gamingbot.moderation.ModAction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite storage, one database file per server. Lists (rosters, brackets,
 * match history) are stored as JSON columns; everything else is relational.
 * On first use for a server, legacy JSON files in the same server folder
 * are imported automatically, so existing data survives the migration.
 */
public class SqliteStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TOURNAMENT_MAP_TYPE =
            new TypeToken<LinkedHashMap<String, Tournament>>() {
            }.getType();
    private static final Type PLAYER_MAP_TYPE =
            new TypeToken<LinkedHashMap<String, PlayerProfile>>() {
            }.getType();
    private static final Type MOD_LIST_TYPE =
            new TypeToken<ArrayList<ModAction>>() {
            }.getType();
    private static final Type STRING_LIST_TYPE =
            new TypeToken<ArrayList<String>>() {
            }.getType();

    public static Path dbFile(Path guildDir) {
        return guildDir.resolve("bot.db");
    }

    private static Connection connect(Path db) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver missing", e);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }

    public static void init(Path db) {
        try {
            Files.createDirectories(db.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create data folder", e);
        }
        try (Connection c = connect(db); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS tournaments ("
                    + "name TEXT PRIMARY KEY, game TEXT, format TEXT, date TEXT, "
                    + "team_limit INTEGER, solo_allowed INTEGER, registration_open INTEGER, "
                    + "bracket_json TEXT, advanced_json TEXT, withdrawn_json TEXT, "
                    + "bracket_round INTEGER, bracket_pool_json TEXT, losers_pool_json TEXT, "
                    + "losers_round INTEGER, winners_champion TEXT, losers_champion TEXT, "
                    + "final_stage INTEGER, champion TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS teams ("
                    + "tournament TEXT, name TEXT, captain TEXT, wins INTEGER, losses INTEGER, "
                    + "players_json TEXT, PRIMARY KEY (tournament, name))");
            s.execute("CREATE TABLE IF NOT EXISTS matches ("
                    + "tournament TEXT, id INTEGER, team_a TEXT, team_b TEXT, winner TEXT, "
                    + "loser TEXT, reporter TEXT, status TEXT, score_winner INTEGER, "
                    + "score_loser INTEGER, round INTEGER, bracket TEXT, "
                    + "PRIMARY KEY (tournament, id))");
            s.execute("CREATE TABLE IF NOT EXISTS players ("
                    + "user TEXT PRIMARY KEY, wins INTEGER, losses INTEGER, tournament_wins INTEGER, "
                    + "history_json TEXT, notify_matches INTEGER, notify_results INTEGER, "
                    + "notify_announcements INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS mod_actions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, user TEXT, moderator TEXT, "
                    + "action TEXT, reason TEXT, timestamp TEXT)");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not init database " + db, e);
        }
    }

    private static int bool(boolean value) {
        return value ? 1 : 0;
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }

    private static List<String> stringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<String> list = GSON.fromJson(json, STRING_LIST_TYPE);
        return list == null ? new ArrayList<>() : list;
    }

    // ----- Tournaments -----

    public static Map<String, Tournament> loadTournaments(Path db) {
        init(db);
        Map<String, Tournament> out = new LinkedHashMap<>();
        try (Connection c = connect(db);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM tournaments")) {
            while (rs.next()) {
                Tournament t = new Tournament();
                t.name = rs.getString("name");
                t.game = rs.getString("game");
                t.format = rs.getString("format");
                t.date = rs.getString("date");
                t.teamLimit = rs.getInt("team_limit");
                t.soloAllowed = rs.getInt("solo_allowed") == 1;
                t.registrationOpen = rs.getInt("registration_open") == 1;
                t.bracket = stringList(rs.getString("bracket_json"));
                t.advancedTeams = stringList(rs.getString("advanced_json"));
                t.withdrawnTeams = stringList(rs.getString("withdrawn_json"));
                t.bracketRound = rs.getInt("bracket_round");
                t.bracketPool = stringList(rs.getString("bracket_pool_json"));
                t.losersPool = stringList(rs.getString("losers_pool_json"));
                t.losersRound = rs.getInt("losers_round");
                t.winnersChampion = rs.getString("winners_champion");
                t.losersChampion = rs.getString("losers_champion");
                t.finalStage = rs.getInt("final_stage");
                t.champion = rs.getString("champion");
                out.put(t.name, t);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load tournaments", e);
        }
        try (Connection c = connect(db);
             PreparedStatement teams = c.prepareStatement(
                     "SELECT * FROM teams WHERE tournament = ?");
             PreparedStatement matches = c.prepareStatement(
                     "SELECT * FROM matches WHERE tournament = ? ORDER BY id")) {
            for (Tournament t : out.values()) {
                teams.setString(1, t.name);
                try (ResultSet rs = teams.executeQuery()) {
                    while (rs.next()) {
                        Team team = new Team();
                        team.name = rs.getString("name");
                        team.captain = rs.getString("captain");
                        team.wins = rs.getInt("wins");
                        team.losses = rs.getInt("losses");
                        team.players = stringList(rs.getString("players_json"));
                        t.teams.put(team.name, team);
                    }
                }
                matches.setString(1, t.name);
                try (ResultSet rs = matches.executeQuery()) {
                    while (rs.next()) {
                        MatchRecord m = new MatchRecord();
                        m.id = rs.getInt("id");
                        m.teamA = rs.getString("team_a");
                        m.teamB = rs.getString("team_b");
                        m.winner = rs.getString("winner");
                        m.loser = rs.getString("loser");
                        m.reporter = rs.getString("reporter");
                        m.status = rs.getString("status");
                        m.scoreWinner = rs.getInt("score_winner");
                        m.scoreLoser = rs.getInt("score_loser");
                        m.round = rs.getInt("round");
                        m.bracket = rs.getString("bracket");
                        t.matches.add(m);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load teams and matches", e);
        }
        return out;
    }

    public static void saveTournaments(Path db, Map<String, Tournament> data) {
        init(db);
        try (Connection c = connect(db)) {
            c.setAutoCommit(false);
            try (Statement clear = c.createStatement()) {
                clear.execute("DELETE FROM matches");
                clear.execute("DELETE FROM teams");
                clear.execute("DELETE FROM tournaments");
            }
            try (PreparedStatement tStmt = c.prepareStatement(
                    "INSERT INTO tournaments VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                 PreparedStatement teamStmt = c.prepareStatement(
                         "INSERT INTO teams VALUES (?,?,?,?,?,?)");
                 PreparedStatement mStmt = c.prepareStatement(
                         "INSERT INTO matches VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (Tournament t : data.values()) {
                    int i = 1;
                    tStmt.setString(i++, t.name);
                    tStmt.setString(i++, str(t.game));
                    tStmt.setString(i++, str(t.format));
                    tStmt.setString(i++, str(t.date));
                    tStmt.setInt(i++, t.teamLimit);
                    tStmt.setInt(i++, bool(t.soloAllowed));
                    tStmt.setInt(i++, bool(t.registrationOpen));
                    tStmt.setString(i++, GSON.toJson(t.bracket));
                    tStmt.setString(i++, GSON.toJson(t.advancedTeams));
                    tStmt.setString(i++, GSON.toJson(t.withdrawnTeams));
                    tStmt.setInt(i++, t.bracketRound);
                    tStmt.setString(i++, GSON.toJson(t.bracketPool));
                    tStmt.setString(i++, GSON.toJson(t.losersPool));
                    tStmt.setInt(i++, t.losersRound);
                    tStmt.setString(i++, str(t.winnersChampion));
                    tStmt.setString(i++, str(t.losersChampion));
                    tStmt.setInt(i++, t.finalStage);
                    tStmt.setString(i++, str(t.champion));
                    tStmt.executeUpdate();
                    for (Team team : t.teams.values()) {
                        int j = 1;
                        teamStmt.setString(j++, t.name);
                        teamStmt.setString(j++, team.name);
                        teamStmt.setString(j++, str(team.captain));
                        teamStmt.setInt(j++, team.wins);
                        teamStmt.setInt(j++, team.losses);
                        teamStmt.setString(j++, GSON.toJson(team.players));
                        teamStmt.executeUpdate();
                    }
                    for (MatchRecord m : t.matches) {
                        int j = 1;
                        mStmt.setString(j++, t.name);
                        mStmt.setInt(j++, m.id);
                        mStmt.setString(j++, str(m.teamA));
                        mStmt.setString(j++, str(m.teamB));
                        mStmt.setString(j++, str(m.winner));
                        mStmt.setString(j++, str(m.loser));
                        mStmt.setString(j++, str(m.reporter));
                        mStmt.setString(j++, str(m.status));
                        mStmt.setInt(j++, m.scoreWinner);
                        mStmt.setInt(j++, m.scoreLoser);
                        mStmt.setInt(j++, m.round);
                        mStmt.setString(j++, str(m.bracket));
                        mStmt.executeUpdate();
                    }
                }
            }
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save tournaments", e);
        }
        // JSON mirror next to the database: the web dashboard reads this file.
        try {
            Files.createDirectories(db.getParent());
            Files.writeString(db.getParent().resolve("tournaments.json"), GSON.toJson(data));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write JSON mirror", e);
        }
    }

    // ----- Players -----

    public static Map<String, PlayerProfile> loadPlayers(Path db) {
        init(db);
        Map<String, PlayerProfile> out = new LinkedHashMap<>();
        try (Connection c = connect(db);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM players")) {
            while (rs.next()) {
                PlayerProfile p = new PlayerProfile();
                p.user = rs.getString("user");
                p.wins = rs.getInt("wins");
                p.losses = rs.getInt("losses");
                p.tournamentWins = rs.getInt("tournament_wins");
                p.matchHistory = stringList(rs.getString("history_json"));
                p.notifyMatches = rs.getInt("notify_matches") == 1;
                p.notifyResults = rs.getInt("notify_results") == 1;
                p.notifyAnnouncements = rs.getInt("notify_announcements") == 1;
                out.put(p.user, p);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load players", e);
        }
        return out;
    }

    public static void savePlayers(Path db, Map<String, PlayerProfile> data) {
        init(db);
        try (Connection c = connect(db)) {
            c.setAutoCommit(false);
            try (Statement clear = c.createStatement()) {
                clear.execute("DELETE FROM players");
            }
            try (PreparedStatement stmt = c.prepareStatement(
                    "INSERT INTO players VALUES (?,?,?,?,?,?,?,?)")) {
                for (PlayerProfile p : data.values()) {
                    int i = 1;
                    stmt.setString(i++, p.user);
                    stmt.setInt(i++, p.wins);
                    stmt.setInt(i++, p.losses);
                    stmt.setInt(i++, p.tournamentWins);
                    stmt.setString(i++, GSON.toJson(p.matchHistory));
                    stmt.setInt(i++, bool(p.notifyMatches));
                    stmt.setInt(i++, bool(p.notifyResults));
                    stmt.setInt(i++, bool(p.notifyAnnouncements));
                    stmt.executeUpdate();
                }
            }
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save players", e);
        }
    }

    // ----- Moderation log -----

    public static List<ModAction> loadModActions(Path db) {
        init(db);
        List<ModAction> out = new ArrayList<>();
        try (Connection c = connect(db);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM mod_actions ORDER BY id")) {
            while (rs.next()) {
                ModAction a = new ModAction();
                a.user = rs.getString("user");
                a.moderator = rs.getString("moderator");
                a.action = rs.getString("action");
                a.reason = rs.getString("reason");
                a.timestamp = rs.getString("timestamp");
                out.add(a);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load moderation log", e);
        }
        return out;
    }

    public static void addModAction(Path db, ModAction action) {
        init(db);
        try (Connection c = connect(db);
             PreparedStatement stmt = c.prepareStatement(
                     "INSERT INTO mod_actions (user, moderator, action, reason, timestamp) "
                             + "VALUES (?,?,?,?,?)")) {
            stmt.setString(1, str(action.user));
            stmt.setString(2, str(action.moderator));
            stmt.setString(3, str(action.action));
            stmt.setString(4, str(action.reason));
            stmt.setString(5, str(action.timestamp));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save moderation log", e);
        }
    }

    // ----- Dashboard mirror merge -----

    /**
     * Tournaments created through the web dashboard land in the JSON mirror.
     * On bot start, any mirror tournaments not yet in the database are
     * imported, so a restart picks up dashboard creations.
     */
    public static void mergeMirrorTournaments(Path db, Path guildDir,
                                              Map<String, Tournament> current) {
        Path mirror = guildDir.resolve("tournaments.json");
        if (!Files.exists(mirror)) {
            return;
        }
        try {
            Map<String, Tournament> data = GSON.fromJson(
                    Files.readString(mirror), TOURNAMENT_MAP_TYPE);
            if (data == null) {
                return;
            }
            boolean changed = false;
            for (Map.Entry<String, Tournament> entry : data.entrySet()) {
                if (!current.containsKey(entry.getKey())) {
                    current.put(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            if (changed) {
                saveTournaments(db, current);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not merge dashboard mirror", e);
        }
    }

    // ----- One-time JSON import -----

    public static void importLegacyJson(Path db, Path guildDir) {
        if (Files.exists(db)) {
            return;
        }
        Path tournamentsJson = guildDir.resolve("tournaments.json");
        Path playersJson = guildDir.resolve("players.json");
        Path modJson = guildDir.resolve("moderation.json");
        boolean any = Files.exists(tournamentsJson) || Files.exists(playersJson)
                || Files.exists(modJson);
        if (!any) {
            return;
        }
        try {
            if (Files.exists(tournamentsJson)) {
                Map<String, Tournament> data = GSON.fromJson(
                        Files.readString(tournamentsJson), TOURNAMENT_MAP_TYPE);
                if (data != null) {
                    saveTournaments(db, data);
                }
            }
            if (Files.exists(playersJson)) {
                Map<String, PlayerProfile> data = GSON.fromJson(
                        Files.readString(playersJson), PLAYER_MAP_TYPE);
                if (data != null) {
                    savePlayers(db, data);
                }
            }
            if (Files.exists(modJson)) {
                List<ModAction> actions = GSON.fromJson(Files.readString(modJson), MOD_LIST_TYPE);
                if (actions != null) {
                    for (ModAction a : actions) {
                        addModAction(db, a);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not import legacy JSON data", e);
        }
    }

    // ----- Backups -----

    public static Path backup(Path db, Path backupsDir) {
        try {
            Files.createDirectories(backupsDir);
            Path target = backupsDir.resolve("bot-" + System.currentTimeMillis() + ".db");
            Files.copy(db, target);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Could not back up database", e);
        }
    }
}

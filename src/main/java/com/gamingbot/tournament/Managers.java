package com.gamingbot.tournament;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One TournamentManager per server. All tournament, player, and moderation
 * data is scoped to its server, so servers never see each other's data.
 */
public class Managers {
    private final Path baseDir;
    private final Map<String, TournamentManager> managers = new ConcurrentHashMap<>();

    public Managers(Path baseDir) {
        this.baseDir = baseDir;
    }

    public static String guildKey(String guildId, String userId) {
        return guildId != null ? guildId : "dm-" + userId;
    }

    public TournamentManager get(String guildKey) {
        return managers.computeIfAbsent(guildKey,
                key -> new TournamentManager(key, baseDir));
    }

    public int tournamentCount() {
        return managers.values().stream().mapToInt(TournamentManager::tournamentCount).sum();
    }

    public int teamCount() {
        return managers.values().stream().mapToInt(TournamentManager::teamCount).sum();
    }
}

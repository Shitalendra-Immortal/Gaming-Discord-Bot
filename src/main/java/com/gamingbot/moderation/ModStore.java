package com.gamingbot.moderation;

import com.gamingbot.tournament.SqliteStore;

import java.nio.file.Path;
import java.util.List;

/**
 * Moderation log, one SQLite database per server. The guild key resolves to
 * data/<guildKey>/bot.db, so servers never share a log.
 */
public class ModStore {
    private static Path dbFor(String guildKey) {
        return SqliteStore.dbFile(Path.of("data", guildKey));
    }

    public static synchronized List<ModAction> load(String guildKey) {
        return SqliteStore.loadModActions(dbFor(guildKey));
    }

    public static synchronized void add(String guildKey, ModAction action) {
        SqliteStore.addModAction(dbFor(guildKey), action);
    }
}

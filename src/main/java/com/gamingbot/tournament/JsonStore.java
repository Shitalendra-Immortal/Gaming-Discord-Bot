package com.gamingbot.tournament;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonStore {
    private static final Path TOURNAMENT_FILE = Path.of("data", "tournaments.json");
    private static final Path PLAYER_FILE = Path.of("data", "players.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TOURNAMENT_MAP_TYPE =
            new TypeToken<LinkedHashMap<String, Tournament>>() {
            }.getType();
    private static final Type PLAYER_MAP_TYPE =
            new TypeToken<LinkedHashMap<String, PlayerProfile>>() {
            }.getType();

    public static Map<String, Tournament> load() {
        try {
            if (!Files.exists(TOURNAMENT_FILE)) {
                return new LinkedHashMap<>();
            }
            Map<String, Tournament> data = GSON.fromJson(Files.readString(TOURNAMENT_FILE),
                    TOURNAMENT_MAP_TYPE);
            return data == null ? new LinkedHashMap<>() : data;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load tournament data", e);
        }
    }

    public static void save(Map<String, Tournament> data) {
        try {
            Files.createDirectories(TOURNAMENT_FILE.getParent());
            Files.writeString(TOURNAMENT_FILE, GSON.toJson(data));
        } catch (IOException e) {
            throw new IllegalStateException("Could not save tournament data", e);
        }
    }

    public static Map<String, PlayerProfile> loadPlayers() {
        try {
            if (!Files.exists(PLAYER_FILE)) {
                return new LinkedHashMap<>();
            }
            Map<String, PlayerProfile> data = GSON.fromJson(Files.readString(PLAYER_FILE),
                    PLAYER_MAP_TYPE);
            return data == null ? new LinkedHashMap<>() : data;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load player data", e);
        }
    }

    public static void savePlayers(Map<String, PlayerProfile> data) {
        try {
            Files.createDirectories(PLAYER_FILE.getParent());
            Files.writeString(PLAYER_FILE, GSON.toJson(data));
        } catch (IOException e) {
            throw new IllegalStateException("Could not save player data", e);
        }
    }
}

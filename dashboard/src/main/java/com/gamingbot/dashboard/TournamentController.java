package com.gamingbot.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class TournamentController {
    private static final Path FILE = resolveDataFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // When started from dashboard/, the bot's data folder is one level up.
    // The bot writes one JSON mirror per server (data/<server>/tournaments.json);
    // the dashboard reads the first mirror it finds, falling back to the
    // legacy shared file.
    private static Path resolveDataFile() {
        Path cwd = Path.of("").toAbsolutePath();
        Path dataDir = cwd.getFileName() != null
                && "dashboard".equals(cwd.getFileName().toString())
                ? Path.of("..", "data") : Path.of("data");
        try {
            if (Files.isDirectory(dataDir)) {
                try (var stream = Files.list(dataDir)) {
                    return stream.filter(Files::isDirectory)
                            .map(dir -> dir.resolve("tournaments.json"))
                            .filter(Files::exists)
                            .findFirst()
                            .orElse(dataDir.resolve("tournaments.json"));
                }
            }
        } catch (IOException e) {
            // Fall through to the legacy path.
        }
        return dataDir.resolve("tournaments.json");
    }

    private JsonObject load() throws IOException {
        if (!Files.exists(FILE)) {
            return new JsonObject();
        }
        JsonElement parsed = JsonParser.parseString(Files.readString(FILE));
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }

    @GetMapping(value = "/api/tournaments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> tournaments() throws IOException {
        return ResponseEntity.ok(GSON.toJson(load()));
    }

    @PostMapping(value = "/api/tournaments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestBody Map<String, Object> input) throws IOException {
        JsonObject all = load();
        String name = String.valueOf(input.get("name"));
        JsonObject t = new JsonObject();
        t.addProperty("name", name);
        t.addProperty("game", String.valueOf(input.getOrDefault("game", "")));
        t.addProperty("format", String.valueOf(input.getOrDefault("format", "Single elimination")));
        t.addProperty("date", String.valueOf(input.getOrDefault("date", "TBD")));
        t.addProperty("teamLimit", ((Number) input.getOrDefault("teamLimit", 0)).intValue());
        t.addProperty("soloAllowed", Boolean.TRUE.equals(input.get("soloAllowed")));
        t.addProperty("registrationOpen", true);
        t.add("teams", new JsonObject());
        t.add("matches", new JsonArray());
        t.add("bracket", new JsonArray());
        t.add("advancedTeams", new JsonArray());
        t.add("withdrawnTeams", new JsonArray());
        all.add(name, t);
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, GSON.toJson(all));
        return ResponseEntity.ok(GSON.toJson(t));
    }

    @GetMapping(value = "/api/leaderboard", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> leaderboard(@RequestParam String tournament) throws IOException {
        JsonObject all = load();
        JsonObject t = all.getAsJsonObject(tournament);
        if (t == null) {
            return ResponseEntity.notFound().build();
        }
        JsonObject teams = t.getAsJsonObject("teams");
        List<JsonObject> rows = new ArrayList<>();
        if (teams != null) {
            for (Map.Entry<String, JsonElement> entry : teams.entrySet()) {
                JsonObject team = entry.getValue().getAsJsonObject();
                JsonObject row = new JsonObject();
                row.addProperty("team", entry.getKey());
                row.addProperty("wins", team.has("wins") ? team.get("wins").getAsInt() : 0);
                row.addProperty("losses", team.has("losses") ? team.get("losses").getAsInt() : 0);
                row.addProperty("points", row.get("wins").getAsInt() * 3);
                rows.add(row);
            }
        }
        rows.sort((a, b) -> {
            int byPoints = Integer.compare(b.get("points").getAsInt(), a.get("points").getAsInt());
            return byPoints != 0 ? byPoints : Integer.compare(b.get("wins").getAsInt(), a.get("wins").getAsInt());
        });
        JsonArray out = new JsonArray();
        rows.forEach(out::add);
        return ResponseEntity.ok(GSON.toJson(out));
    }
}

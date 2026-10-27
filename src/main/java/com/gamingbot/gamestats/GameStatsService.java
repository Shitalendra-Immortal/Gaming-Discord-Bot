package com.gamingbot.gamestats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Live game data from Riot (Valorant) and Steam (CS2). Both APIs need keys
 * supplied as environment variables when the bot runs: RIOT_API_KEY and
 * STEAM_API_KEY. Without a key, the matching game reports itself as
 * unconfigured instead of failing — the bot never needs a key to run.
 *
 * Honest limits: Riot developer keys last 24 hours; a long-lived key
 * needs Riot's production approval. Valorant data uses the Asia-Pacific
 * shard, which fits this community's region.
 */
public class GameStatsService {
    private static final String RIOT_SHARD = "ap";
    private final String riotKey;
    private final String steamKey;
    private final HttpClient http;

    public GameStatsService() {
        this(System.getenv("RIOT_API_KEY"), System.getenv("STEAM_API_KEY"));
    }

    public GameStatsService(String riotKey, String steamKey) {
        this.riotKey = riotKey;
        this.steamKey = steamKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean riotConfigured() {
        return riotKey != null && !riotKey.isBlank();
    }

    public boolean steamConfigured() {
        return steamKey != null && !steamKey.isBlank();
    }

    /** Splits "Name#TAG" into [name, tag], or null when the format is wrong. */
    public static String[] splitRiotId(String riotId) {
        if (riotId == null) {
            return null;
        }
        String[] parts = riotId.split("#", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }
        return parts;
    }

    /** A SteamID64 is 17 digits; anything else is treated as a vanity name. */
    public static boolean looksLikeSteamId(String id) {
        return id != null && id.matches("\\d{17}");
    }

    public String fetchValorantSummary(String riotId) throws IOException, InterruptedException {
        String[] parts = splitRiotId(riotId);
        if (parts == null) {
            return "Use Riot ID format Name#TAG.";
        }
        if (!riotConfigured()) {
            return "Live Valorant stats need a RIOT_API_KEY set on the bot host. "
                    + "Note: Riot developer keys last 24 hours; a long-lived key needs Riot approval.";
        }
        String puuid = riotAccountPuuid(parts[0], parts[1]);
        if (puuid == null) {
            return "Riot ID " + riotId + " was not found.";
        }
        JsonObject history = getJson("https://" + RIOT_SHARD + ".api.riotgames.com/val/match/v1/"
                + "matchlists/by-puuid/" + puuid, true);
        JsonArray matches = history.getAsJsonArray("history");
        if (matches == null || matches.isEmpty()) {
            return "No recent Valorant matches found for " + riotId + ".";
        }
        int played = 0;
        int wins = 0;
        int kills = 0;
        int deaths = 0;
        int checked = 0;
        for (JsonElement element : matches) {
            if (checked >= 5) {
                break;
            }
            String matchId = element.getAsJsonObject().get("matchId").getAsString();
            JsonObject match = getJson("https://" + RIOT_SHARD + ".api.riotgames.com/val/match/v1/"
                    + "matches/" + matchId, true);
            String teamId = null;
            JsonObject playerStats = null;
            for (JsonElement player : match.getAsJsonArray("players")) {
                JsonObject p = player.getAsJsonObject();
                if (puuid.equals(p.get("puuid").getAsString())) {
                    teamId = p.get("teamId").getAsString();
                    playerStats = p.getAsJsonObject("stats");
                    break;
                }
            }
            if (teamId == null || playerStats == null) {
                continue;
            }
            boolean won = false;
            for (JsonElement team : match.getAsJsonArray("teams")) {
                JsonObject t = team.getAsJsonObject();
                if (teamId.equals(t.get("teamId").getAsString())) {
                    won = t.get("won").getAsBoolean();
                    break;
                }
            }
            played++;
            if (won) {
                wins++;
            }
            kills += playerStats.get("kills").getAsInt();
            deaths += playerStats.get("deaths").getAsInt();
            checked++;
        }
        if (played == 0) {
            return "No recent Valorant matches with stats found for " + riotId + ".";
        }
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        return "Valorant recent form for " + riotId + ": " + wins + "W-" + (played - wins) + "L over "
                + played + " matches, K/D " + String.format("%.2f", kd)
                + " (" + kills + " kills, " + deaths + " deaths).";
    }

    private String riotAccountPuuid(String name, String tag) throws IOException, InterruptedException {
        String url = "https://americas.api.riotgames.com/riot/account/v1/accounts/by-riot-id/"
                + encode(name) + "/" + encode(tag);
        HttpResponse<String> response = get(url, true);
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("Riot API returned " + response.statusCode()
                    + " — the key may be expired (developer keys last 24 hours).");
        }
        return JsonParser.parseString(response.body()).getAsJsonObject().get("puuid").getAsString();
    }

    public String fetchSteamSummary(String steamIdOrVanity) throws IOException, InterruptedException {
        if (!steamConfigured()) {
            return "Live CS2 stats need a STEAM_API_KEY set on the bot host.";
        }
        String steamId = steamIdOrVanity;
        if (!looksLikeSteamId(steamId)) {
            JsonObject resolved = getJson("https://api.steampowered.com/ISteamUser/ResolveVanityURL/"
                    + "v0001/?key=" + steamKey + "&vanityurl=" + encode(steamId), false);
            JsonObject response = resolved.getAsJsonObject("response");
            if (response == null || response.get("success").getAsInt() != 1) {
                return "Steam user '" + steamIdOrVanity + "' was not found.";
            }
            steamId = response.get("steamid").getAsString();
        }
        JsonObject stats = getJson("https://api.steampowered.com/ISteamUserStats/GetUserStatsForGame/"
                + "v0002/?appid=730&key=" + steamKey + "&steamid=" + steamId, false);
        JsonObject playerStats = stats.getAsJsonObject("playerstats");
        if (playerStats == null || !playerStats.has("stats")) {
            return "CS2 stats are not available for this account (the profile may be private).";
        }
        Integer kills = null;
        Integer deaths = null;
        Integer wins = null;
        for (JsonElement element : playerStats.getAsJsonArray("stats")) {
            JsonObject stat = element.getAsJsonObject();
            switch (stat.get("name").getAsString()) {
                case "total_kills":
                    kills = stat.get("value").getAsInt();
                    break;
                case "total_deaths":
                    deaths = stat.get("value").getAsInt();
                    break;
                case "total_wins":
                    wins = stat.get("value").getAsInt();
                    break;
                default:
                    break;
            }
        }
        if (kills == null && deaths == null && wins == null) {
            return "CS2 stats are not available for this account.";
        }
        StringBuilder out = new StringBuilder("CS2 lifetime stats for ").append(steamIdOrVanity).append(": ");
        if (kills != null) {
            out.append(kills).append(" kills");
        }
        if (deaths != null) {
            out.append(", ").append(deaths).append(" deaths");
            if (kills != null) {
                double kd = deaths == 0 ? kills : (double) kills / deaths;
                out.append(" (K/D ").append(String.format("%.2f", kd)).append(")");
            }
        }
        if (wins != null) {
            out.append(", ").append(wins).append(" wins");
        }
        return out.append(".").toString();
    }

    private JsonObject getJson(String url, boolean riot) throws IOException, InterruptedException {
        HttpResponse<String> response = get(url, riot);
        if (response.statusCode() == 429) {
            throw new IOException("Rate limited by the game API — try again in a minute.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Game API returned " + response.statusCode() + ".");
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private HttpResponse<String> get(String url, boolean riot)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (riot) {
            builder.header("X-Riot-Token", riotKey);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

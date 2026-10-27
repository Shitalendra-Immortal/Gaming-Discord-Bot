package com.gamingbot.listeners;

import com.gamingbot.gamestats.GameStatsService;
import com.gamingbot.tournament.Managers;
import com.gamingbot.tournament.TournamentManager;
import com.gamingbot.util.Cooldowns;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LiveStatsListener extends ListenerAdapter {
    private static final Set<String> COMMANDS = Set.of("link-game", "live-stats");

    private final Managers managers;
    private final GameStatsService stats;

    public LiveStatsListener(Managers managers) {
        this(managers, new GameStatsService());
    }

    public LiveStatsListener(Managers managers, GameStatsService stats) {
        this.managers = managers;
        this.stats = stats;
    }

    private TournamentManager manager(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild() == null ? null : event.getGuild().getId();
        return managers.get(Managers.guildKey(guildId, event.getUser().getId()));
    }

    private String reqString(SlashCommandInteractionEvent event, String name) {
        return Objects.requireNonNull(event.getOption(name)).getAsString();
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        String command = event.getName();
        if (!COMMANDS.contains(command)) {
            return;
        }
        if (!Cooldowns.tryUse(event.getUser().getId(), command, 3)) {
            long left = Cooldowns.remainingSeconds(event.getUser().getId(), command, 3);
            event.reply("Slow down. Try again in " + left + " seconds.")
                    .setEphemeral(true).queue();
            return;
        }
        TournamentManager manager = manager(event);
        switch (command) {
            case "link-game": {
                String game = reqString(event, "game");
                String id = reqString(event, "id").trim();
                if (game.equals("Valorant") && GameStatsService.splitRiotId(id) == null) {
                    event.reply("Use Riot ID format Name#TAG, for example Player#1234.")
                            .setEphemeral(true).queue();
                    break;
                }
                if (id.isEmpty()) {
                    event.reply("Give your game ID to link.").setEphemeral(true).queue();
                    break;
                }
                event.reply(manager.linkGame(event.getUser().getAsMention(), game, id)).queue();
                break;
            }
            case "live-stats": {
                String user = event.getOption("user") == null
                        ? event.getUser().getAsMention()
                        : Objects.requireNonNull(event.getOption("user")).getAsUser().getAsMention();
                Map<String, String> links = manager.linkedGames(user);
                if (links.isEmpty()) {
                    event.reply(user + " has no linked game accounts. Link one with /link-game first.")
                            .setEphemeral(true).queue();
                    break;
                }
                event.deferReply().queue();
                StringBuilder out = new StringBuilder("**Live stats for ").append(user).append("**\n");
                for (Map.Entry<String, String> link : links.entrySet()) {
                    try {
                        if (link.getKey().equals("Valorant")) {
                            out.append(stats.fetchValorantSummary(link.getValue())).append("\n");
                        } else {
                            out.append(stats.fetchSteamSummary(link.getValue())).append("\n");
                        }
                    } catch (Exception e) {
                        out.append(link.getKey()).append(": could not fetch live stats (")
                                .append(e.getMessage()).append(")\n");
                    }
                }
                event.getHook().sendMessage(out.toString()).queue();
                break;
            }
            default:
                break;
        }
    }
}

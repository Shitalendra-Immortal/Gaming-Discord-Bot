package com.gamingbot;

import com.gamingbot.listeners.GameRoleListener;
import com.gamingbot.listeners.LiveStatsListener;
import com.gamingbot.listeners.ModerationListener;
import com.gamingbot.listeners.ReminderListener;
import com.gamingbot.listeners.SchedulingListener;
import com.gamingbot.listeners.TournamentListener;
import com.gamingbot.scheduling.MatchScheduler;
import com.gamingbot.tournament.Managers;
import com.gamingbot.tournament.TournamentManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class BotMain extends ListenerAdapter {
    private static final long STARTED_AT = System.currentTimeMillis();
    private static final String HELP_TEXT =
            "**LEGEND commands**\n"
            + "Tournaments: /tournament-create, /tournament-list, /dashboard, /bracket, /withdraw\n"
            + "Scheduling: /schedule-match, /check-in, /upcoming\n"
            + "Live stats: /link-game, /live-stats\n"
            + "Teams: /team-create, /team-invite, /team-join, /register-solo\n"
            + "Results: /submit-result, /confirm-result, /dispute-result, /correct-result (mods)\n"
            + "Standings: /leaderboard, /player-leaderboard, /player-stats\n"
            + "Announcements: /announce-match, /announce-winner, /remind-captains, /notify-opt\n"
            + "Game roles: /game-role\n"
            + "Moderation: /warn, /mute, /timeout, /kick, /ban, /clear, /mod-log\n"
            + "Utility: /remind, /help, /status";
    private final Managers managers;

    public BotMain(Managers managers) {
        this.managers = managers;
    }

    public static void main(String[] args) throws Exception {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Set the DISCORD_TOKEN environment variable first.");
        }
        backupDataDir();
        Managers managers = new Managers(Path.of("data"));
        JDA jda = JDABuilder.createDefault(token)
                // Only non-privileged intents: the bot uses slash commands and
                // buttons, so it does not need GUILD_MEMBERS or MESSAGE_CONTENT,
                // which Discord rejects (close code 4014) unless they are
                // enabled on the application's Bot page.
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .setActivity(Activity.playing("Tournaments | /leaderboard"))
                .addEventListeners(new BotMain(managers), new TournamentListener(managers),
                        new ModerationListener(), new ReminderListener(), new GameRoleListener(),
                        new SchedulingListener(managers), new LiveStatsListener(managers))
                .build();
        jda.awaitReady();
        new MatchScheduler(jda, managers).start();
        System.out.println("Gaming bot is online.");
    }

    private static void backupDataDir() {
        Path data = Path.of("data");
        if (!Files.isDirectory(data)) {
            return;
        }
        try {
            Path target = Path.of("backups", String.valueOf(System.currentTimeMillis()));
            Files.walk(data).forEach(source -> {
                try {
                    Path dest = target.resolve(data.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            System.out.println("Backed up data to " + target);
        } catch (IOException e) {
            System.out.println("Backup skipped: " + e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        String command = event.getName();
        if (command.equals("help")) {
            event.reply(HELP_TEXT).setEphemeral(true).queue();
        } else if (command.equals("status")) {
            long uptimeMinutes = (System.currentTimeMillis() - STARTED_AT) / 60000;
            int tournaments = 0;
            int teams = 0;
            if (managers != null) {
                for (Guild guild : event.getJDA().getGuilds()) {
                    TournamentManager m = managers.get(guild.getId());
                    tournaments += m.tournamentCount();
                    teams += m.teamCount();
                }
            }
            event.reply("**Bot status**\nUptime: " + uptimeMinutes + " minutes\nServers: "
                    + event.getJDA().getGuilds().size()
                    + "\nTournaments: " + tournaments + "\nTeams: " + teams)
                    .setEphemeral(true).queue();
        }
    }

    @Override
    public void onGuildJoin(@Nonnull GuildJoinEvent event) {
        if (event.getGuild().getSystemChannel() != null) {
            event.getGuild().getSystemChannel().sendMessage(
                    "Thanks for adding LEGEND! I run tournaments, teams, brackets, "
                    + "leaderboards, and moderation.\nStart with /help, create a tournament "
                    + "with /tournament-create, and check standings with /dashboard.").queue();
        }
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        event.getJDA().updateCommands().addCommands(
                Commands.slash("help", "Show all commands grouped by category"),
                Commands.slash("status", "Show bot uptime and totals"),
                Commands.slash("tournament-create", "Create a tournament")
                        .addOption(OptionType.STRING, "name", "Tournament name", true)
                        .addOption(OptionType.STRING, "game", "Game being played", true)
                        .addOption(OptionType.STRING, "format", "Format, e.g. Single elimination", false)
                        .addOption(OptionType.STRING, "date", "Tournament date", false)
                        .addOption(OptionType.INTEGER, "team-limit", "Max teams, 0 means no limit", false)
                        .addOption(OptionType.BOOLEAN, "solo", "Allow solo registration", false)
                        .addOption(OptionType.STRING, "prize-pool", "Prize pool, e.g. ₹5,000", false),
                Commands.slash("tournament-list", "List all tournaments"),
                Commands.slash("dashboard", "Show the in-Discord dashboard with tournaments and standings"),
                Commands.slash("team-create", "Create a team in a tournament (you become captain)")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.STRING, "team", "Team name", true),
                Commands.slash("team-invite", "Invite a player to your team")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.STRING, "team", "Team name", true)
                        .addOption(OptionType.USER, "user", "Player to invite", true),
                Commands.slash("team-join", "Join a team")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.STRING, "team", "Team name", true),
                Commands.slash("register-solo", "Register solo in a tournament that allows it")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true),
                Commands.slash("submit-result", "Submit a match result for confirmation")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.STRING, "winner", "Winning team", true)
                        .addOption(OptionType.STRING, "loser", "Losing team", true)
                        .addOption(OptionType.INTEGER, "score-winner", "Winner's score", false)
                        .addOption(OptionType.INTEGER, "score-loser", "Loser's score", false),
                Commands.slash("confirm-result", "Confirm a pending match result")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.INTEGER, "match", "Match number", true),
                Commands.slash("dispute-result", "Dispute a pending match result")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.INTEGER, "match", "Match number", true),
                Commands.slash("correct-result", "Correct a match result (moderators only)")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.INTEGER, "match", "Match number", true)
                        .addOption(OptionType.STRING, "winner", "Correct winning team", true)
                        .addOption(OptionType.STRING, "loser", "Correct losing team", true),
                Commands.slash("bracket", "Generate a tournament bracket and schedule")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true),
                Commands.slash("withdraw", "Withdraw a team from a tournament")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.STRING, "team", "Team name", true),
                Commands.slash("player-stats", "Show a player's statistics")
                        .addOption(OptionType.USER, "user", "Player (defaults to you)", false),
                Commands.slash("game-role", "Pick your game role")
                        .addOptions(new OptionData(OptionType.STRING, "game", "Game", true)
                                .addChoice("Valorant", "Valorant")
                                .addChoice("BGMI", "BGMI")
                                .addChoice("Minecraft", "Minecraft")
                                .addChoice("CS2", "CS2")),
                Commands.slash("leaderboard", "Show a tournament team leaderboard")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true),
                Commands.slash("player-leaderboard", "Show the player leaderboard")
                        .addOption(OptionType.INTEGER, "page", "Leaderboard page, 10 players per page", false),
                Commands.slash("announce-match", "Announce an upcoming match")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.STRING, "team-a", "First team", true)
                        .addOption(OptionType.STRING, "team-b", "Second team", true),
                Commands.slash("announce-winner", "Announce a tournament winner")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true),
                Commands.slash("remind-captains", "Remind captains about pending results")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true),
                Commands.slash("notify-opt", "Turn a notification type on or off")
                        .addOptions(
                                new OptionData(OptionType.STRING, "type", "Notification type", true)
                                        .addChoice("matches", "matches")
                                        .addChoice("results", "results")
                                        .addChoice("announcements", "announcements"),
                                new OptionData(OptionType.BOOLEAN, "enabled", "Turn on or off", true)),
                Commands.slash("warn", "Warn a member")
                        .addOption(OptionType.USER, "user", "Member to warn", true)
                        .addOption(OptionType.STRING, "reason", "Reason", false),
                Commands.slash("mute", "Mute a member with a timeout")
                        .addOption(OptionType.USER, "user", "Member to mute", true)
                        .addOption(OptionType.INTEGER, "minutes", "Mute length in minutes", true)
                        .addOption(OptionType.STRING, "reason", "Reason", false),
                Commands.slash("kick", "Kick a member")
                        .addOption(OptionType.USER, "user", "Member to kick", true)
                        .addOption(OptionType.STRING, "reason", "Reason", false),
                Commands.slash("ban", "Ban a member")
                        .addOption(OptionType.USER, "user", "Member to ban", true)
                        .addOption(OptionType.STRING, "reason", "Reason", false),
                Commands.slash("timeout", "Time out a member")
                        .addOption(OptionType.USER, "user", "Member to time out", true)
                        .addOption(OptionType.INTEGER, "minutes", "Timeout length in minutes", true),
                Commands.slash("clear", "Bulk-delete recent messages (max 100)")
                        .addOption(OptionType.INTEGER, "count", "How many messages", true),
                Commands.slash("mod-log", "Show recent moderation actions"),
                Commands.slash("remind", "Set a reminder in this channel")
                        .addOption(OptionType.INTEGER, "minutes", "Minutes from now", true)
                        .addOption(OptionType.STRING, "message", "Reminder text", true),
                Commands.slash("schedule-match", "Schedule a match at a fixed date and time (IST)")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.STRING, "team-a", "First team", true)
                        .addOption(OptionType.STRING, "team-b", "Second team", true)
                        .addOption(OptionType.STRING, "date", "Date YYYY-MM-DD", true)
                        .addOption(OptionType.STRING, "time", "Time HH:MM, 24-hour, IST", true)
                        .addOption(OptionType.STRING, "format", "Match format, e.g. BO3", false)
                        .addOption(OptionType.STRING, "maps", "Map pool, e.g. Bind, Ascent, Haven", false),
                Commands.slash("check-in", "Check your team in for a scheduled match (captains only)")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true)
                        .addOption(OptionType.INTEGER, "match", "Scheduled match number", true),
                Commands.slash("upcoming", "List upcoming scheduled matches for a tournament")
                        .addOption(OptionType.STRING, "tournament", "Tournament name", true),
                Commands.slash("link-game", "Link your game account for live stats")
                        .addOptions(new OptionData(OptionType.STRING, "game", "Game", true)
                                .addChoice("Valorant", "Valorant")
                                .addChoice("CS2", "CS2"),
                                new OptionData(OptionType.STRING, "id",
                                        "Riot ID like Name#TAG, or Steam ID / vanity name", true)),
                Commands.slash("live-stats", "Show live game stats for a linked account")
                        .addOption(OptionType.USER, "user", "Player (defaults to you)", false)
        ).queue();
    }
}

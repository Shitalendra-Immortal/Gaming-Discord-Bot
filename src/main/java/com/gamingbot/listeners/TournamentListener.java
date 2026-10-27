package com.gamingbot.listeners;

import com.gamingbot.tournament.Managers;
import com.gamingbot.tournament.MatchRecord;
import com.gamingbot.tournament.Team;
import com.gamingbot.tournament.Tournament;
import com.gamingbot.tournament.TournamentManager;
import com.gamingbot.util.Cooldowns;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TournamentListener extends ListenerAdapter {
    private final Managers managers;
    private static final Set<String> COMMANDS = Set.of(
            "tournament-create", "tournament-list", "dashboard",
            "team-create", "team-invite", "team-join", "register-solo",
            "submit-result", "confirm-result", "dispute-result", "correct-result",
            "bracket", "withdraw", "player-stats",
            "leaderboard", "player-leaderboard",
            "announce-match", "announce-winner", "remind-captains", "notify-opt");

    public TournamentListener(Managers managers) {
        this.managers = managers;
    }

    private TournamentManager manager(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild() == null ? null : event.getGuild().getId();
        return managers.get(Managers.guildKey(guildId, event.getUser().getId()));
    }

    private TournamentManager manager(ButtonInteractionEvent event) {
        String guildId = event.getGuild() == null ? null : event.getGuild().getId();
        return managers.get(Managers.guildKey(guildId, event.getUser().getId()));
    }

    private String optString(SlashCommandInteractionEvent event, String name) {
        return event.getOption(name) == null ? null : event.getOption(name).getAsString();
    }

    private String reqString(SlashCommandInteractionEvent event, String name) {
        return Objects.requireNonNull(event.getOption(name)).getAsString();
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith("dashboard:")) {
            String view = id.substring("dashboard:".length());
            if (view.equals("refresh")) {
                view = "tournaments";
            }
            event.editMessageEmbeds(buildDashboard(view, event.getJDA(), manager(event)))
                    .setActionRow(dashboardButtons()).queue();
            return;
        }
        if (!id.startsWith("register:")) {
            return;
        }
        String tournament = id.substring("register:".length());
        String result = manager(event).registerSolo(tournament, event.getUser().getAsMention());
        event.reply(result).setEphemeral(true).queue();
    }

    private Button[] dashboardButtons() {
        return new Button[] {
                Button.primary("dashboard:tournaments", "Tournaments"),
                Button.secondary("dashboard:leaderboard", "Leaderboard"),
                Button.success("dashboard:refresh", "Refresh")
        };
    }

    private MessageEmbed buildDashboard(String view, JDA jda, TournamentManager manager) {
        Map<String, Tournament> tournaments = manager.snapshot();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("leaderboard".equals(view) ? "Gaming Dashboard — Leaderboards" : "Gaming Dashboard")
                .setColor(0x5865F2)
                .setTimestamp(Instant.now());
        String avatar = jda.getSelfUser().getEffectiveAvatarUrl();
        if (avatar != null) {
            embed.setThumbnail(avatar);
        }
        if (tournaments.isEmpty()) {
            embed.setDescription("No tournaments yet. Create one with /tournament-create.");
            return embed.build();
        }
        if ("leaderboard".equals(view)) {
            String players = manager.playerLeaderboard().replace("**Player leaderboard — page 1**\n", "");
            if (players.length() > 1500) {
                players = players.substring(0, 1500) + "…";
            }
            embed.setDescription("**Top players**\n" + players);
            for (Tournament t : tournaments.values()) {
                List<Team> teams = new ArrayList<>(t.teams.values());
                teams.sort((a, b) -> {
                    int byPoints = Integer.compare(b.points(), a.points());
                    return byPoints != 0 ? byPoints : Integer.compare(b.wins, a.wins);
                });
                if (teams.isEmpty()) {
                    embed.addField(t.name, "No teams yet.", false);
                    continue;
                }
                StringBuilder rows = new StringBuilder();
                for (int i = 0; i < Math.min(5, teams.size()); i++) {
                    Team team = teams.get(i);
                    rows.append(i + 1).append(". ").append(team.name)
                            .append(" — ").append(team.points()).append(" pts (")
                            .append(team.wins).append("W-").append(team.losses).append("L)\n");
                }
                embed.addField(t.name, rows.toString(), false);
            }
            embed.setFooter("Switch views with the buttons below.");
            return embed.build();
        }
        int totalTeams = 0;
        int confirmed = 0;
        int pending = 0;
        for (Tournament t : tournaments.values()) {
            totalTeams += t.teams.size();
            List<Team> sorted = new ArrayList<>(t.teams.values());
            sorted.sort((a, b) -> {
                int byPoints = Integer.compare(b.points(), a.points());
                return byPoints != 0 ? byPoints : Integer.compare(b.wins, a.wins);
            });
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                Team team = sorted.get(i);
                if (top.length() > 0) {
                    top.append("\n");
                }
                top.append(i + 1).append(". ").append(team.name)
                        .append(" (").append(team.points()).append(" pts)");
            }
            if (top.length() == 0) {
                top.append("none yet");
            }
            int tConfirmed = 0;
            int tPending = 0;
            StringBuilder recent = new StringBuilder();
            for (int i = t.matches.size() - 1; i >= 0 && i >= t.matches.size() - 3; i--) {
                MatchRecord m = t.matches.get(i);
                if ("CONFIRMED".equals(m.status)) {
                    tConfirmed++;
                } else if ("PENDING".equals(m.status)) {
                    tPending++;
                }
                if (recent.length() > 0) {
                    recent.append("\n");
                }
                if ("CONFIRMED".equals(m.status) && m.scoreWinner >= 0) {
                    recent.append("#").append(m.id).append(" ").append(m.winner)
                            .append(" ").append(m.scoreWinner).append("-").append(m.scoreLoser)
                            .append(" ").append(m.loser);
                } else {
                    recent.append("#").append(m.id).append(" ").append(m.winner)
                            .append(" vs ").append(m.loser)
                            .append(" (").append(m.status.toLowerCase()).append(")");
                }
            }
            if (recent.length() == 0) {
                recent.append("none yet");
            }
            confirmed += tConfirmed;
            pending += tPending;
            String limit = t.teamLimit > 0 ? String.valueOf(t.teamLimit) : "no limit";
            embed.addField(t.name,
                    t.game + " • " + t.format + " • " + t.date + "\n"
                            + "Teams: " + t.teams.size() + " / " + limit
                            + " • Registration: " + (t.registrationOpen ? "open" : "closed") + "\n"
                            + "Top teams:\n" + top + "\n"
                            + "Recent matches:\n" + recent,
                    false);
        }
        embed.setDescription("Tournaments: " + tournaments.size()
                + " • Teams: " + totalTeams
                + " • Confirmed matches: " + confirmed
                + " • Pending results: " + pending);
        embed.setFooter("Press Refresh to update • Switch to Leaderboard for standings.");
        return embed.build();
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        String command = event.getName();
        // Ignore commands owned by other listeners: replying here would clash
        // with the real handler and fail as "already acknowledged".
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
            case "tournament-create": {
                String format = optString(event, "format");
                String date = optString(event, "date");
                long limit = event.getOption("team-limit") == null
                        ? 0 : event.getOption("team-limit").getAsLong();
                boolean solo = event.getOption("solo") != null
                        && event.getOption("solo").getAsBoolean();
                String reply = manager.createTournament(reqString(event, "name"),
                        reqString(event, "game"),
                        format == null ? "Single elimination" : format,
                        date == null ? "TBD" : date, (int) limit, solo);
                if (solo) {
                    event.reply(reply)
                            .addActionRow(Button.primary("register:" + reqString(event, "name"),
                                    "Register"))
                            .queue();
                } else {
                    event.reply(reply + "\nTo join: create a team with /team-create, "
                            + "then join it with /team-join.").queue();
                }
                break;
            }
            case "tournament-list":
                event.reply(manager.listTournaments()).queue();
                break;
            case "dashboard":
                event.replyEmbeds(buildDashboard("tournaments", event.getJDA(), manager))
                        .addActionRow(dashboardButtons()).queue();
                break;
            case "team-create":
                event.reply(manager.createTeam(reqString(event, "tournament"),
                        reqString(event, "team"), event.getUser().getAsMention())).queue();
                break;
            case "team-invite": {
                String player = event.getOption("user").getAsMember() == null
                        ? Objects.requireNonNull(event.getOption("user")).getAsUser().getAsMention()
                        : Objects.requireNonNull(event.getOption("user")).getAsMember().getAsMention();
                event.reply(manager.inviteTeam(reqString(event, "tournament"),
                        reqString(event, "team"), player)).queue();
                break;
            }
            case "team-join":
                event.reply(manager.joinTeam(reqString(event, "tournament"),
                        reqString(event, "team"), event.getUser().getAsMention())).queue();
                break;
            case "register-solo":
                event.reply(manager.registerSolo(reqString(event, "tournament"),
                        event.getUser().getAsMention())).queue();
                break;
            case "submit-result":
                event.reply(manager.submitResult(reqString(event, "tournament"),
                        reqString(event, "winner"), reqString(event, "loser"),
                        event.getUser().getAsMention(),
                        event.getOption("score-winner") == null ? -1
                                : (int) event.getOption("score-winner").getAsLong(),
                        event.getOption("score-loser") == null ? -1
                                : (int) event.getOption("score-loser").getAsLong())).queue();
                break;
            case "confirm-result":
                event.reply(manager.confirmResult(reqString(event, "tournament"),
                        (int) Objects.requireNonNull(event.getOption("match")).getAsLong(),
                        event.getUser().getAsMention())).queue();
                break;
            case "dispute-result": {
                String reply = manager.disputeResult(reqString(event, "tournament"),
                        (int) Objects.requireNonNull(event.getOption("match")).getAsLong());
                if (reply.contains("disputed.")) {
                    String guildId = event.getGuild() == null ? null : event.getGuild().getId();
                    com.gamingbot.moderation.ModStore.add(
                            Managers.guildKey(guildId, event.getUser().getId()),
                            new com.gamingbot.moderation.ModAction(
                                    reqString(event, "tournament") + " match "
                                            + event.getOption("match").getAsLong(),
                                    "moderators (pending review)", "DISPUTE",
                                    "Disputed result awaiting moderator review"));
                }
                event.reply(reply).queue();
                break;
            }
            case "correct-result": {
                if (event.getMember() == null
                        || (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)
                        && !event.getMember().hasPermission(Permission.ADMINISTRATOR))) {
                    event.reply("Only moderators can correct results.").setEphemeral(true).queue();
                    break;
                }
                event.reply(manager.correctResult(reqString(event, "tournament"),
                        (int) Objects.requireNonNull(event.getOption("match")).getAsLong(),
                        reqString(event, "winner"), reqString(event, "loser"))).queue();
                break;
            }
            case "bracket":
                event.reply(manager.bracket(reqString(event, "tournament"))).queue();
                break;
            case "withdraw": {
                String captain = manager.getTeamCaptain(reqString(event, "tournament"),
                        reqString(event, "team"));
                boolean isCaptain = captain != null
                        && captain.equals(event.getUser().getAsMention());
                boolean isMod = event.getMember() != null
                        && (event.getMember().hasPermission(Permission.MODERATE_MEMBERS)
                        || event.getMember().hasPermission(Permission.ADMINISTRATOR));
                if (!isCaptain && !isMod) {
                    event.reply("Only the team captain or a moderator can withdraw a team.")
                            .setEphemeral(true).queue();
                    break;
                }
                event.reply(manager.withdrawTeam(reqString(event, "tournament"),
                        reqString(event, "team"))).queue();
                break;
            }
            case "player-stats": {
                String user = event.getOption("user") == null
                        ? event.getUser().getAsMention()
                        : Objects.requireNonNull(event.getOption("user")).getAsUser().getAsMention();
                event.reply(manager.playerStats(user)).queue();
                break;
            }
            case "leaderboard":
                event.reply(manager.leaderboard(reqString(event, "tournament"))).queue();
                break;
            case "player-leaderboard": {
                int page = event.getOption("page") == null
                        ? 1 : (int) event.getOption("page").getAsLong();
                event.reply(manager.playerLeaderboard(page)).queue();
                break;
            }
            case "announce-match":
                event.reply(manager.announceMatch(reqString(event, "tournament"),
                        reqString(event, "team-a"), reqString(event, "team-b"))).queue();
                break;
            case "announce-winner":
                event.reply(manager.announceWinner(reqString(event, "tournament"))).queue();
                break;
            case "remind-captains":
                event.reply(manager.remindCaptains(reqString(event, "tournament"))).queue();
                break;
            case "notify-opt":
                event.reply(manager.notifyOpt(event.getUser().getAsMention(),
                        reqString(event, "type"),
                        Objects.requireNonNull(event.getOption("enabled")).getAsBoolean())).queue();
                break;
            default:
                break;
        }
    }
}

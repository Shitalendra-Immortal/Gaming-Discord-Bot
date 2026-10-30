package com.gamingbot.listeners;

import com.gamingbot.tournament.Managers;
import com.gamingbot.tournament.ScheduledMatch;
import com.gamingbot.tournament.TournamentManager;
import com.gamingbot.ui.NeonEmbeds;
import com.gamingbot.util.Cooldowns;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import javax.annotation.Nonnull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;

public class SchedulingListener extends ListenerAdapter {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> COMMANDS = Set.of(
            "schedule-match", "check-in", "upcoming");

    private final Managers managers;

    public SchedulingListener(Managers managers) {
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
        String prefix = null;
        if (id.startsWith("match-details:")) {
            prefix = "match-details:";
        } else if (id.startsWith("match-checkin:")) {
            prefix = "match-checkin:";
        } else if (id.startsWith("match-submit:")) {
            prefix = "match-submit:";
        } else {
            return;
        }
        String rest = id.substring(prefix.length());
        int sep = rest.lastIndexOf(':');
        if (sep < 0) {
            return;
        }
        String tournament = rest.substring(0, sep);
        int matchId;
        try {
            matchId = Integer.parseInt(rest.substring(sep + 1));
        } catch (NumberFormatException e) {
            return;
        }
        TournamentManager manager = manager(event);
        ScheduledMatch m = manager.scheduledMatch(tournament, matchId);
        if (m == null) {
            event.reply("That scheduled match no longer exists.").setEphemeral(true).queue();
            return;
        }
        switch (prefix) {
            case "match-details:":
                event.replyEmbeds(NeonEmbeds.matchDetails(m,
                        manager.getTeamCaptain(tournament, m.teamA),
                        manager.getTeamCaptain(tournament, m.teamB)))
                        .setEphemeral(true).queue();
                break;
            case "match-checkin:":
                event.reply(manager.checkIn(tournament, matchId, event.getUser().getAsMention()))
                        .setEphemeral(true).queue();
                break;
            case "match-submit:":
                event.reply("Captains report this result with /submit-result — tournament: "
                        + tournament + ", winner and loser team names, plus the score if you "
                        + "have it. The opposing captain must confirm it with /confirm-result "
                        + "before points count.").setEphemeral(true).queue();
                break;
            default:
                break;
        }
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
            case "schedule-match": {
                long epoch;
                try {
                    LocalDate date = LocalDate.parse(reqString(event, "date"),
                            DateTimeFormatter.ISO_LOCAL_DATE);
                    LocalTime time = LocalTime.parse(reqString(event, "time"),
                            DateTimeFormatter.ofPattern("H:mm"));
                    epoch = LocalDateTime.of(date, time).atZone(IST).toInstant().toEpochMilli();
                } catch (DateTimeParseException e) {
                    event.reply("Use date YYYY-MM-DD and time HH:MM (24-hour, IST). "
                            + "Example: date 2026-11-01, time 18:00.").setEphemeral(true).queue();
                    break;
                }
                if (epoch <= System.currentTimeMillis()) {
                    event.reply("That time is in the past. Schedule a future match.")
                            .setEphemeral(true).queue();
                    break;
                }
                String format = optString(event, "format");
                String maps = optString(event, "maps");
                String tournament = reqString(event, "tournament");
                String reply = manager.scheduleMatch(tournament,
                        reqString(event, "team-a"), reqString(event, "team-b"),
                        epoch, event.getChannel().getId(),
                        format == null ? "" : format, maps == null ? "" : maps);
                if (!reply.startsWith("Match #")) {
                    event.reply(reply).queue();
                    break;
                }
                int matchId;
                try {
                    matchId = Integer.parseInt(reply.substring("Match #".length(),
                            reply.indexOf(' ', "Match #".length())));
                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    event.reply(reply).queue();
                    break;
                }
                ScheduledMatch scheduled = manager.scheduledMatch(tournament, matchId);
                if (scheduled == null) {
                    event.reply(reply).queue();
                    break;
                }
                event.reply(reply).addEmbeds(NeonEmbeds.matchCard(scheduled))
                        .addActionRow(
                                Button.secondary("match-details:" + tournament + ":" + matchId,
                                        "Match Details"),
                                Button.success("match-checkin:" + tournament + ":" + matchId,
                                        "Check-in"),
                                Button.primary("match-submit:" + tournament + ":" + matchId,
                                        "Submit Result"))
                        .queue();
                break;
            }
            case "check-in":
                event.reply(manager.checkIn(reqString(event, "tournament"),
                        (int) Objects.requireNonNull(event.getOption("match")).getAsLong(),
                        event.getUser().getAsMention())).queue();
                break;
            case "upcoming": {
                String tournament = reqString(event, "tournament");
                if (!manager.snapshot().containsKey(tournament)) {
                    event.reply(manager.upcoming(tournament)).queue();
                    break;
                }
                event.replyEmbeds(NeonEmbeds.upcomingCard(tournament,
                        manager.upcomingMatches(tournament))).queue();
                break;
            }
            default:
                break;
        }
    }
}

package com.gamingbot.listeners;

import com.gamingbot.tournament.Managers;
import com.gamingbot.tournament.TournamentManager;
import com.gamingbot.util.Cooldowns;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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
                event.reply(manager.scheduleMatch(reqString(event, "tournament"),
                        reqString(event, "team-a"), reqString(event, "team-b"),
                        epoch, event.getChannel().getId())).queue();
                break;
            }
            case "check-in":
                event.reply(manager.checkIn(reqString(event, "tournament"),
                        (int) Objects.requireNonNull(event.getOption("match")).getAsLong(),
                        event.getUser().getAsMention())).queue();
                break;
            case "upcoming":
                event.reply(manager.upcoming(reqString(event, "tournament"))).queue();
                break;
            default:
                break;
        }
    }
}

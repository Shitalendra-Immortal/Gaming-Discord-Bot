package com.gamingbot.listeners;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReminderListener extends ListenerAdapter {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("remind")) {
            return;
        }
        long minutes = Objects.requireNonNull(event.getOption("minutes")).getAsLong();
        String message = Objects.requireNonNull(event.getOption("message")).getAsString();
        String who = event.getUser().getAsMention();
        scheduler.schedule(() -> event.getChannel()
                        .sendMessage("Reminder for " + who + ": " + message).queue(),
                minutes, TimeUnit.MINUTES);
        event.reply("Reminder set for " + minutes + " minutes from now.").queue();
    }
}

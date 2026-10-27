package com.gamingbot.listeners;

import com.gamingbot.moderation.ModAction;
import com.gamingbot.moderation.ModStore;
import com.gamingbot.util.Cooldowns;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ModerationListener extends ListenerAdapter {
    private static final Set<String> COMMANDS = Set.of(
            "warn", "mute", "kick", "ban", "timeout", "clear", "mod-log");

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        // Ignore commands owned by other listeners: replying here would clash
        // with the real handler and fail as "already acknowledged".
        String command = event.getName();
        if (!COMMANDS.contains(command)) {
            return;
        }
        if (event.getGuild() == null) {
            event.reply("Moderation commands only work inside a server.").setEphemeral(true).queue();
            return;
        }
        if (!command.equals("mod-log") && !Cooldowns.tryUse(event.getUser().getId(), command, 5)) {
            long left = Cooldowns.remainingSeconds(event.getUser().getId(), command, 5);
            event.reply("Slow down. Try again in " + left + " seconds.")
                    .setEphemeral(true).queue();
            return;
        }
        switch (command) {
            case "warn": {
                if (!hasPermission(event, Permission.MODERATE_MEMBERS)) {
                    return;
                }
                Member target = Objects.requireNonNull(event.getOption("user")).getAsMember();
                if (target == null) {
                    event.reply("That user is not in this server.").setEphemeral(true).queue();
                    return;
                }
                String reason = optionOrNull(event, "reason");
                String text = reason == null ? "No reason given" : reason;
                ModStore.add(event.getGuild().getId(), new ModAction(target.getAsMention(), event.getUser().getAsMention(),
                        "WARN", text));
                target.getUser().openPrivateChannel().queue(
                        ch -> ch.sendMessage("You were warned in " + event.getGuild().getName()
                                + ": " + text).queue(),
                        failure -> {
                        });
                event.reply("Warned " + target.getAsMention() + ": " + text).queue();
                break;
            }
            case "mute": {
                if (!hasPermission(event, Permission.MODERATE_MEMBERS)) {
                    return;
                }
                Member target = Objects.requireNonNull(event.getOption("user")).getAsMember();
                if (target == null) {
                    event.reply("That user is not in this server.").setEphemeral(true).queue();
                    return;
                }
                long minutes = Objects.requireNonNull(event.getOption("minutes")).getAsLong();
                String reason = optionOrNull(event, "reason");
                ModStore.add(event.getGuild().getId(), new ModAction(target.getAsMention(), event.getUser().getAsMention(),
                        "MUTE", reason == null ? "No reason given" : reason));
                target.timeoutFor(Duration.ofMinutes(minutes)).queue(
                        success -> event.reply("Muted " + target.getAsMention()
                                + " for " + minutes + " minutes.").queue(),
                        failure -> event.reply("Could not mute that member.").setEphemeral(true).queue());
                break;
            }
            case "kick": {
                if (!hasPermission(event, Permission.KICK_MEMBERS)) {
                    return;
                }
                Member target = Objects.requireNonNull(event.getOption("user")).getAsMember();
                if (target == null) {
                    event.reply("That user is not in this server.").setEphemeral(true).queue();
                    return;
                }
                String reason = optionOrNull(event, "reason");
                ModStore.add(event.getGuild().getId(), new ModAction(target.getAsMention(), event.getUser().getAsMention(),
                        "KICK", reason == null ? "No reason given" : reason));
                event.getGuild().kick(target,
                        reason == null ? "Kicked by moderator" : reason).queue(
                        success -> event.reply("Kicked " + target.getAsMention()).queue(),
                        failure -> event.reply("Could not kick that member.").setEphemeral(true).queue());
                break;
            }
            case "ban": {
                if (!hasPermission(event, Permission.BAN_MEMBERS)) {
                    return;
                }
                Member target = Objects.requireNonNull(event.getOption("user")).getAsMember();
                if (target == null) {
                    event.reply("That user is not in this server.").setEphemeral(true).queue();
                    return;
                }
                String reason = optionOrNull(event, "reason");
                ModStore.add(event.getGuild().getId(), new ModAction(target.getAsMention(), event.getUser().getAsMention(),
                        "BAN", reason == null ? "No reason given" : reason));
                event.getGuild().ban(target.getUser(), 0, TimeUnit.DAYS)
                        .reason(reason == null ? "Banned by moderator" : reason).queue(
                        success -> event.reply("Banned " + target.getAsMention()).queue(),
                        failure -> event.reply("Could not ban that member.").setEphemeral(true).queue());
                break;
            }
            case "timeout": {
                if (!hasPermission(event, Permission.MODERATE_MEMBERS)) {
                    return;
                }
                Member target = Objects.requireNonNull(event.getOption("user")).getAsMember();
                if (target == null) {
                    event.reply("That user is not in this server.").setEphemeral(true).queue();
                    return;
                }
                long minutes = Objects.requireNonNull(event.getOption("minutes")).getAsLong();
                String reason = optionOrNull(event, "reason");
                ModStore.add(event.getGuild().getId(), new ModAction(target.getAsMention(), event.getUser().getAsMention(),
                        "TIMEOUT", reason == null ? "No reason given" : reason));
                target.timeoutFor(Duration.ofMinutes(minutes)).queue(
                        success -> event.reply("Timed out " + target.getAsMention()
                                + " for " + minutes + " minutes.").queue(),
                        failure -> event.reply("Could not time out that member.").setEphemeral(true).queue());
                break;
            }
            case "clear": {
                if (!hasPermission(event, Permission.MESSAGE_MANAGE)) {
                    return;
                }
                int count = (int) Math.min(
                        Objects.requireNonNull(event.getOption("count")).getAsLong(), 100);
                event.getChannel().getHistory().retrievePast(count).queue(messages -> {
                    List<Message> toDelete = messages.stream()
                            .filter(m -> !m.isPinned())
                            .toList();
                    if (toDelete.isEmpty()) {
                        event.reply("Nothing to delete.").setEphemeral(true).queue();
                    } else {
                        toDelete.forEach(m -> m.delete().queue());
                        ModStore.add(event.getGuild().getId(), new ModAction("channel", event.getUser().getAsMention(),
                                "CLEAR", "Deleted " + toDelete.size() + " messages"));
                        event.reply("Deleted " + toDelete.size() + " messages.")
                                .setEphemeral(true).queue();
                    }
                });
                break;
            }
            case "mod-log": {
                if (!hasPermission(event, Permission.MODERATE_MEMBERS)) {
                    return;
                }
                List<ModAction> actions = ModStore.load(event.getGuild().getId());
                if (actions.isEmpty()) {
                    event.reply("No moderation actions logged yet.").setEphemeral(true).queue();
                    return;
                }
                StringBuilder out = new StringBuilder("**Recent moderation actions**\n");
                for (int i = actions.size() - 1; i >= 0 && i >= actions.size() - 10; i--) {
                    ModAction a = actions.get(i);
                    out.append("- ").append(a.action).append(" ").append(a.user)
                            .append(" by ").append(a.moderator).append(": ").append(a.reason)
                            .append("\n");
                }
                event.reply(out.toString()).setEphemeral(true).queue();
                break;
            }
            default:
                break;
        }
    }

    private boolean hasPermission(SlashCommandInteractionEvent event, Permission permission) {
        Member caller = event.getMember();
        if (caller == null || !caller.hasPermission(permission)) {
            event.reply("You do not have permission to do that.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private String optionOrNull(SlashCommandInteractionEvent event, String name) {
        return event.getOption(name) == null ? null : event.getOption(name).getAsString();
    }
}

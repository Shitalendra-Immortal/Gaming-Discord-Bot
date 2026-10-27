package com.gamingbot.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

public class GameRoleListener extends ListenerAdapter {
    private static final List<String> GAMES = List.of("Valorant", "BGMI", "Minecraft", "CS2");

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("game-role")) {
            return;
        }
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Game roles only work inside a server.").setEphemeral(true).queue();
            return;
        }
        Member member = event.getMember();
        String chosen = Objects.requireNonNull(event.getOption("game")).getAsString();
        for (String game : GAMES) {
            for (Role role : event.getGuild().getRolesByName(game, true)) {
                if (member.getRoles().contains(role)) {
                    event.getGuild().removeRoleFromMember(member, role).queue();
                }
            }
        }
        List<Role> found = event.getGuild().getRolesByName(chosen, true);
        if (!found.isEmpty()) {
            event.getGuild().addRoleToMember(member, found.get(0)).queue(
                    success -> event.reply("Your game role is now " + chosen + ".").queue(),
                    failure -> event.reply("Could not assign that role. The bot needs Manage Roles and a role above the game roles.")
                            .setEphemeral(true).queue());
            return;
        }
        event.getGuild().createRole().setName(chosen).queue(
                role -> event.getGuild().addRoleToMember(member, role).queue(
                        success -> event.reply("Your game role is now " + chosen + ".").queue(),
                        failure -> event.reply("Could not assign that role. The bot needs Manage Roles and a role above the game roles.")
                                .setEphemeral(true).queue()),
                failure -> event.reply("Could not create the " + chosen + " role.")
                        .setEphemeral(true).queue());
    }
}

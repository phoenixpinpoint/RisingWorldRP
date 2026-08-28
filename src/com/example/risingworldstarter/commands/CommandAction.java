package com.example.risingworldstarter.commands;

import net.risingworld.api.objects.Player;

/**
 * Action invoked when a registered chat command is entered. The arguments array
 * contains the invoked command or alias at index 0 followed by its arguments.
 */
@FunctionalInterface
public interface CommandAction {
    void execute(Player player, String[] arguments);
}

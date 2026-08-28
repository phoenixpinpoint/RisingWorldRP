package com.example.risingworldstarter.commands;

import java.util.List;

/** Immutable description of a command registered with CivicCore. */
public record RegisteredCommand(String owner, String name, String category, String usage, String description,
                                boolean requiresCharacter, List<String> aliases,
                                CommandAction action) {
    public RegisteredCommand {
        aliases = List.copyOf(aliases);
    }
}

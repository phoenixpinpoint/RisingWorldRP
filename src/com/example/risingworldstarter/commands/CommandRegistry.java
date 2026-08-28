package com.example.risingworldstarter.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Shared command registry used by CivicCore and integrations from other plugins. */
public final class CommandRegistry {
    private final List<RegisteredCommand> commands = new ArrayList<>();
    private final Map<String, RegisteredCommand> commandsByName = new LinkedHashMap<>();

    public synchronized RegisteredCommand register(String owner, String name, String usage,
                                                    String description, boolean requiresCharacter,
                                                    List<String> aliases, CommandAction action) {
        return register(owner, name, "Other", usage, description, requiresCharacter, aliases, action);
    }

    public synchronized RegisteredCommand register(String owner, String name, String category,
                                                    String usage, String description,
                                                    boolean requiresCharacter, List<String> aliases,
                                                    CommandAction action) {
        return register(owner, name, category, usage, description, requiresCharacter, aliases, List.of(), action);
    }

    public synchronized RegisteredCommand register(String owner, String name, String category,
                                                    String usage, String description,
                                                    boolean requiresCharacter, List<String> aliases,
                                                    List<CommandHelp> additionalHelp, CommandAction action) {
        String normalizedOwner = requireText(owner, "owner");
        String normalizedName = normalizeName(name);
        String normalizedCategory = requireText(category, "category");
        String normalizedUsage = requireText(usage, "usage");
        String normalizedDescription = requireText(description, "description");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(additionalHelp, "additionalHelp");
        Objects.requireNonNull(action, "action");

        List<String> normalizedAliases = aliases.stream().map(CommandRegistry::normalizeName).toList();
        List<String> allNames = new ArrayList<>();
        allNames.add(normalizedName);
        allNames.addAll(normalizedAliases);
        for (String registeredName : allNames) {
            RegisteredCommand conflict = commandsByName.get(registeredName);
            if (conflict != null) {
                throw new IllegalArgumentException("Command name " + registeredName
                        + " is already registered by " + conflict.owner());
            }
        }

        RegisteredCommand command = new RegisteredCommand(normalizedOwner, normalizedName, normalizedCategory,
                normalizedUsage, normalizedDescription, requiresCharacter, normalizedAliases, additionalHelp, action);
        commands.add(command);
        for (String registeredName : allNames) commandsByName.put(registeredName, command);
        return command;
    }

    public synchronized RegisteredCommand find(String name) {
        if (name == null || name.isBlank()) return null;
        return commandsByName.get(normalizeName(name));
    }

    public synchronized List<RegisteredCommand> getCommands() {
        return List.copyOf(commands);
    }

    public synchronized void unregister(RegisteredCommand command) {
        if (command == null || !commands.remove(command)) return;
        commandsByName.entrySet().removeIf(entry -> entry.getValue() == command);
    }

    public synchronized int unregisterOwner(String owner) {
        String normalizedOwner = requireText(owner, "owner");
        List<RegisteredCommand> ownedCommands = commands.stream()
                .filter(command -> command.owner().equalsIgnoreCase(normalizedOwner)).toList();
        ownedCommands.forEach(this::unregister);
        return ownedCommands.size();
    }

    private static String normalizeName(String name) {
        String normalized = requireText(name, "name").toLowerCase(Locale.US);
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Command names cannot contain whitespace: " + name);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}

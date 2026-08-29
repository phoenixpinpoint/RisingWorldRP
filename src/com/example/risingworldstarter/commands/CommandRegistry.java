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

    /** Returns a likely registered command for short command-name typos. */
    public synchronized RegisteredCommand suggest(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = normalizeName(name);
        RegisteredCommand best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, RegisteredCommand> entry : commandsByName.entrySet()) {
            int distance = editDistance(normalized, entry.getKey());
            int allowedDistance = Math.max(normalized.length(), entry.getKey().length()) <= 5 ? 1 : 2;
            if (distance <= allowedDistance && distance < bestDistance) {
                best = entry.getValue();
                bestDistance = distance;
            }
        }
        return best;
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

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            int[] current = new int[right.length() + 1];
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitution = previous[rightIndex - 1]
                        + (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1);
                current[rightIndex] = Math.min(Math.min(previous[rightIndex] + 1,
                        current[rightIndex - 1] + 1), substitution);
            }
            previous = current;
        }
        return previous[right.length()];
    }
}

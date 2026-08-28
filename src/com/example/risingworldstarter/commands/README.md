# Command system

This package provides CivicCore's shared command registry. CivicCore registers
all of its own chat commands here, and other Rising World plugins can use the
same registry to add commands and actions. Every registered command is
automatically included in `/help`.

## Public API

- `CommandRegistry` registers, finds, lists, and unregisters commands.
- `RegisteredCommand` contains a command's owner, name, usage, description,
  aliases, character requirement, and action.
- `CommandAction` is the callback invoked when a player enters the command.

Command names and aliases are case-insensitive and may be supplied with or
without the leading slash. Registration fails with an
`IllegalArgumentException` if a name or alias is already owned by another
command.

## Registering a command

Get CivicCore's loaded plugin instance, then use its shared registry:

```java
CivicCore civicCore = (CivicCore) getPluginByName("CivicCore");
CommandRegistry commands = civicCore.getCommandRegistry();

commands.register(
        "MyPlugin",
        "/greet",
        "/greet [player]",
        "Greet a player.",
        false,
        List.of("/hello"),
        (player, arguments) -> player.sendTextMessage("Hello from MyPlugin!")
);
```

The registration arguments are:

1. The owning plugin name.
2. The primary command name.
3. The usage text displayed by `/help`.
4. The help description.
5. Whether CivicCore must have an active character for the player.
6. A list of aliases.
7. The action to execute.

The action receives the invoked command or alias at `arguments[0]`, followed by
the entered arguments. CivicCore cancels the original chat-command event before
executing the action.

## Plugin shutdown

Plugins should remove their registrations from `onDisable()`:

```java
commands.unregisterOwner("MyPlugin");
```

`unregisterOwner` returns the number of commands removed. A plugin can instead
retain the `RegisteredCommand` returned by `register` and pass it to
`commands.unregister(command)` to remove one registration.

# CivicCore

This is a minimal Java project for the **Rising World Unity-version Plugin API**.
It compiles against the SDK included with your copy of Rising World, so its API matches your game installation.

## First build

1. Edit `resources/plugin.yml` and change `author: YourName`.
2. Set your Rising World installation in `config/build.config.json`:

   ```json
   {
     "risingWorldPath": "D:\\SteamLibrary\\steamapps\\common\\RisingWorld"
   }
   ```

3. Install a Java 20 or newer JDK, then build from a terminal:

   ```text
   Windows:  gradlew.bat build
   macOS:    ./gradlew build
   Linux:    ./gradlew build
   ```

You can override the JSON setting for a single build:

   ```text
   ./gradlew build -PrisingWorldPath="/path/to/Rising World"
   ```

Or override it with an environment variable:

   ```text
   RISING_WORLD_PATH="/path/to/Rising World" ./gradlew build
   ```

On Windows, use `gradlew.bat` in the examples above. The resolution order is the
`-PrisingWorldPath` Gradle property, `RISING_WORLD_PATH` environment
variable, then `config/build.config.json`.

The finished plugin is `build/CivicCore.jar`.

## Install for testing

Run this to build and install it into Rising World's `Plugins/CivicCore` directory:

```text
./gradlew installPlugin -PrisingWorldPath="/path/to/Rising World"
```

`installPlugin` installs only the JAR and never copies configuration files, so
an existing server configuration is protected. To explicitly copy the project
templates from `config/` into the plugin directory, run:

During the rename migration, `installPlugin` also removes only the obsolete
`Plugins/RisingWorldStarter/RisingWorldStarter.jar`. It leaves the legacy
directory and configuration files in place so CivicCore can migrate them.

```text
./gradlew installConfig -PrisingWorldPath="/path/to/Rising World"
```

`installConfig` may overwrite the root `plugin.properties`,
`economy.properties`, and `marketplace.json` templates, so use it only when you
intend to update the defaults used by newly initialized worlds. Existing
world-specific configuration is never overwritten. On Windows, use
`gradlew.bat`.

Restart the game/server. Its console/log should report `[CivicCore] Enabled ...`.
Startup also writes `[CivicCore/DEBUG]` diagnostics showing the resolved
data directory, loaded configuration, enabled marketplace item count, current
world time, payroll schedule, event registration, and available commands.

Use `/about` in chat to display the plugin name, installed version, basic feature
summary, author, and license. This command is available even before selecting a
character.

Use `/commands` to open the categorized command browser, or `/help` to print the
same registered commands in chat.

Server administrators can use `/admin` to open an in-game dashboard showing
world time, player counts, claim totals, economy settings, enabled marketplace
products, and the connected-player balance list. The dashboard includes Refresh
and Close controls and is rejected for non-administrators. Each connected player
has a Kick button and a Ban button; permanent bans require confirmation, and an
administrator cannot kick or ban their own session from the dashboard.

## Economy API

Players receive character-scoped balances, HUD updates, configurable salaries,
and a public API for integrations. See the [economy module documentation](src/com/example/risingworldstarter/economy/README.md)
for configuration, persistence, payday behavior, and API examples.

## Characters

Every Rising World UID owns up to four character slots. On spawn, the player
must select an existing character or create one in an empty slot. The selected
character name becomes the visible in-game player name and overhead name tag.
Server administrators additionally see a smaller gray `<profile name>` line
attached beneath each active character's name; ordinary players never receive
that account-identifying label.

Use `/characters` (or `/character` or `/chars`) at any time to save the active
character and reopen the slot selector without disconnecting from the server.
Each occupied slot also has a Delete button with a confirmation dialog. Deleting
a character permanently removes its saved state, inventory, balance, and claims.
Character appearance comes from Rising World's native profile editor. Edit the
profile outside the world, then join the server; the plugin captures that native
appearance before loading a roleplay character. New characters inherit it. Use
`/syncappearance` to apply the most recently captured native profile appearance
to the active character. Rejoin after changing the native profile so the plugin
can capture the new values, including facial features not writable through the
live server API.

On first use, the plugin creates slot 1 as a legacy character before changing
the player. It captures the existing name, inventory, clothing, appearance,
position, rotation, health, hunger, thirst, stamina, balance, and claims.
Character-controlled data is stored in the world-scoped CivicCore database and
autosaved every 60 seconds as well as on disconnect and plugin shutdown.

Balances, salary, claims, inventory, appearance, status, position, and future
skill data are character-specific. Administrator status, claim-administrator
permission, bans, and the Rising World UID remain account-specific. The admin
dashboard shows both the immutable profile name and selected character name;
Kick and Ban actions always target the UID.

## Marketplace

Use `/store` to open or close the marketplace. The scrollable store contains
only explicitly priced items from the installed Rising World API. Each row shows
the game's square item icon, its name and price, and quantity controls.
Buying deducts the price and places one unit in the player's inventory. A failed
inventory insertion is refunded. Items are sorted and grouped under the category
reported by the game; definitions without a category appear under `Other`.
Clickable category tabs, including an `All` tab, filter the product list. The
search field performs a case-insensitive item-name search within the selected
category as the player types.

Each product has minus and plus controls for quantities from 0 to 99. The live
cart bar shows the total quantity and cost and provides Clear and Checkout
buttons. Checkout charges the cart once; products that cannot fit in the
inventory are refunded and remain in the cart for the player to retry.

On first startup the plugin generates
`Plugins/CivicCore/marketplace.json`. Each item has an object like:

```json
{
  "items": {
    "123": {
      "name": "exampleitem",
      "category": "Misc",
      "enabled": true,
      "price": 100.00
    }
  }
}
```

An item is rendered only when its `price` field exists. Set the price to any
non-negative dollar amount with at most two decimal places. Remove the `price`
field to remove the item from the store, or set `enabled` to `false` to
hide it while retaining its price. Reload the plugin after editing the file.
New game items receive name/category metadata but no automatic price, so they
remain hidden until explicitly priced.

Internal placeholder items (`clothingitem`, `oldboot`, `missingitem`,
`constructionitem`, `objectkit`, `objectkitsmall`, `plantitem`, and `blueprint`)
the iconless `branch`, and all items categorized or typed as NPCs are never purchasable. Their generated
`enabled` field is forced to `false`, including in an existing configuration.

If `marketplace.json` does not exist but the old `marketplace.properties` does,
the plugin imports it once and writes the equivalent JSON file.

## Land claims

CivicCore provides character-owned chunks, protection rules, claim administrators,
and persistent chest ownership. See the [claims module documentation](src/com/example/risingworldstarter/claims/README.md)
for commands, storage, protection behavior, and the public service API.

## Subsystem documentation

- [Database](src/com/example/risingworldstarter/database/README.md) explains the
  storage abstraction, SQLite backend, schema location, and configuration boundary.
- [Economy](src/com/example/risingworldstarter/economy/README.md) covers balances,
  configuration, payroll, persistence, and the public economy API.
- [Land claims](src/com/example/risingworldstarter/claims/README.md) covers chunk
  ownership, protection, claim administrators, chests, and integrations.
- [Command system](src/com/example/risingworldstarter/commands/README.md) explains
  command registration, actions, aliases, `/help`, and external-plugin cleanup.
- [Automatic window trim](src/com/example/risingworldstarter/autotrim/README.md)
  explains the window-opening carve behavior and service integration.

## World and server isolation

Characters, inventories, balances, claims, and administrator assignments are
isolated by Rising World's own world directory. Starting another world or
server uses its separate `Worlds/<world>/CivicCore/`
directory, preventing characters and inventories from crossing between worlds. On the first launch after upgrading, legacy
global data and previous `RisingWorldStarter` world data are copied
into the currently loaded world once; the original files
remain in place as a recovery backup.

Each enabled world directory contains its own `plugin.properties`,
`economy.properties`, and `marketplace.json`. A world must explicitly contain
`Worlds/<world>/CivicCore/plugin.properties` or the plugin does
nothing for that world. When the opt-in file exists, root economy and marketplace
copies are used as templates for any missing world configuration. Set
`enabled=false` to temporarily disable an opted-in world without removing the
file or JAR.

## Project layout

```text
src/        Java source code
resources/  plugin.yml, packaged into the root of the JAR
config/     build settings and explicitly installed plugin configuration templates
build/      generated output (safe to delete)
build.gradle and settings.gradle  portable Gradle build configuration
gradlew and gradlew.bat            Gradle wrapper launchers
```

Your main class must extend `net.risingworld.api.Plugin` and implement `onEnable()` and `onDisable()`. Its full package/class name must exactly match `main:` in `resources/plugin.yml`. The build packages this definition as `resources/plugin.yml` inside the JAR, as required by Rising World.

For the current API reference, open <https://javadoc.rising-world.net/>. The official setup guide says the game ships the SDK under `Data/SDK` and its JDK under `Data/Java/JDK`.


# Rising World Starter Plugin

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

The finished plugin is `build/RisingWorldStarter.jar`.

## Install for testing

Run this to build and install it into Rising World's `Plugins/RisingWorldStarter` directory:

```text
./gradlew installPlugin -PrisingWorldPath="/path/to/Rising World"
```

`installPlugin` installs only the JAR and never copies configuration files, so
an existing server configuration is protected. To explicitly copy the project
templates from `config/` into the plugin directory, run:

```text
./gradlew installConfig -PrisingWorldPath="/path/to/Rising World"
```

`installConfig` may overwrite the root `plugin.properties`,
`economy.properties`, and `marketplace.json` templates, so use it only when you
intend to update the defaults used by newly initialized worlds. Existing
world-specific configuration is never overwritten. On Windows, use
`gradlew.bat`.

Restart the game/server. Its console/log should report `[RisingWorldStarter] Enabled ...`.
Startup also writes `[RisingWorldStarter/DEBUG]` diagnostics showing the resolved
data directory, loaded configuration, enabled marketplace item count, current
world time, payroll schedule, event registration, and available commands.

Server administrators can use `/admin` to open an in-game dashboard showing
world time, player counts, claim totals, economy settings, enabled marketplace
products, and the connected-player balance list. The dashboard includes Refresh
and Close controls and is rejected for non-administrators. Each connected player
has a Kick button and a Ban button; permanent bans require confirmation, and an
administrator cannot kick or ban their own session from the dashboard.

## Economy API

Players see their current balance on the HUD after spawning. They can also use
`/balance` or `/bal` in chat to print the balance and refresh the HUD.

The top-center HUD shows the current in-world year, month, day, and 24-hour
clock. It follows the server's world calendar and refreshes once per second.

Balances are stored as integer minor units (cents) in
`Worlds/<world>/plugins/RisingWorldStarter/balances.properties`. New players start with
`$25,000.00`, and claiming a chunk costs `$10,000.00`.

These values can be changed in `config/economy.properties` before explicitly
loading the configuration, or in the generated runtime `economy.properties`:

```properties
default-balance=25000.00
claim-cost=10000.00
base-salary=1000.00
```

Every connected player receives the base salary every eight in-world hours, at
00:00, 08:00, and 16:00. Payday detects entry into a new eight-hour period rather
than requiring an exact clock tick. It also checks again after Rising World
skips the night, so sleeping through midnight cannot miss payday.
Other plugins can access the API through the loaded plugin instance:

```java
RisingWorldStarter economyPlugin =
        (RisingWorldStarter) getPluginByName("RisingWorldStarter");
EconomyApi economy = economyPlugin.getEconomyApi();
String characterKey = economyPlugin.getActiveCharacterKey(player);

economy.deposit(characterKey, 500);       // adds $5.00
boolean paid = economy.withdraw(characterKey, 250);
economyPlugin.updateBalanceLabel(player);    // refresh connected player's HUD
```

## Characters

Every Rising World UID owns up to four character slots. On spawn, the player
must select an existing character or create one in an empty slot. The selected
character name becomes the visible in-game player name.

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
Character-controlled data is stored beneath
`Worlds/<world>/plugins/RisingWorldStarter/characters/` and autosaved every 60 seconds as well
as on disconnect and plugin shutdown.

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
`Plugins/RisingWorldStarter/marketplace.json`. Each item has an object like:

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

Land ownership is stored by horizontal chunk in
`Worlds/<world>/plugins/RisingWorldStarter/claims.properties`. Available chat commands:

- `/claim` claims the chunk where you are standing.
- `/chunk` reports the current chunk coordinates and owner, and draws its boundary.
- `/claims` lists all chunks you own and toggles blue squares over all of them.
- `/unclaim` releases your current chunk.
- Running `/chunk` again while viewing the same chunk hides its boundary.

The boundary is green for unclaimed land, blue for your land, and red for land
claimed by another player. Running `/chunk` again moves the visualization to the
new current chunk. Claim-square X/Z positions remain fixed while their vertical
position follows the viewing player.

Server administrators can manage a persistent claim-administrator whitelist:

- `/claimadmin add <online-player>`
- `/claimadmin remove <online-player>`
- `/claimadmin list`

Whitelisted claim administrators can use `/unclaim` on anyone's chunk. The list
is stored by player UID in the world-scoped `claim-admins.properties`.

Claimed chunks are protected from other characters. Non-owners cannot build,
place blueprints or items, edit terrain/water/grass, harvest vegetation, or
damage, remove, recolor, or change constructions and objects in the chunk.
Server administrators and whitelisted claim administrators bypass protection.
On unclaimed chunks, players may gather natural resources such as grass, trees,
and wild plants, but cannot place items, build, plant, or modify terrain, water,
or grass. A character must claim the chunk before developing the land.
Cancelled grass cutting also suppresses the associated harvested grass reward.
Cancelled planting refunds the seed consumed by Rising World's placement action.

Chunk owners always retain access to their own claimed chunks. The `/admin`
dashboard has one session-only `ADMIN BYPASS` toggle that lets server and claim
administrators work inside other characters' claims. It resets to disabled
whenever the plugin or server reloads.

### Chest ownership

Storage objects placed inside a claimed chunk belong to that chunk's character
owner and begin unlocked. Existing chests inherit the current chunk owner the
first time they are accessed or managed. While looking directly at a chest, its
owner can use:

- `/chest status`
- `/chest lock`
- `/chest unlock`

Unlocked chests may be opened by anyone. Locked chests can only be opened or
managed by their owning character. When the session-only `ADMIN BYPASS` is on,
server and claim administrators can also open and manage them. Chest ownership
and lock state are stored in the world's `chests.properties` file and removed
when the chest is destroyed.

## World and server isolation

Characters, inventories, balances, claims, and administrator assignments are
isolated by Rising World's own world directory. Starting another world or
server uses its separate `Worlds/<world>/plugins/RisingWorldStarter/`
directory, preventing characters and inventories from crossing between worlds. On the first launch after upgrading, legacy
global data is copied into the currently loaded world once; the original files
remain in place as a recovery backup.

Each enabled world directory contains its own `plugin.properties`,
`economy.properties`, and `marketplace.json`. A world must explicitly contain
`Worlds/<world>/plugins/RisingWorldStarter/plugin.properties` or the plugin does
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

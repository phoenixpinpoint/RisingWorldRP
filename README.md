# Rising World Starter Plugin

This is a minimal Java project for the **Rising World Unity-version Plugin API**.
It compiles against the SDK included with your copy of Rising World, so its API matches your game installation.

## First build

1. Edit `resources/plugin.yml` and change `author: YourName`.
2. Set your Rising World installation in `build.config.json`:

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
variable, then `build.config.json`.

The finished plugin is `build/RisingWorldStarter.jar`.

## Install for testing

Run this to build and install it into Rising World's `Plugins/RisingWorldStarter` directory:

```text
./gradlew installPlugin -PrisingWorldPath="/path/to/Rising World"
```

Restart the game/server. Its console/log should report `[RisingWorldStarter] Enabled ...`.

## Economy API

Players see their current balance on the HUD after spawning. They can also use
`/balance` or `/bal` in chat to print the balance and refresh the HUD.

The top-center HUD shows the current in-world year, month, day, and 24-hour
clock. It follows the server's world calendar and refreshes once per second.

Balances are stored as integer minor units (cents) in
`Plugins/RisingWorldStarter/balances.properties`. New players start with
`$25,000.00`, and claiming a chunk costs `$10,000.00`.

Both values can be changed in the generated `economy.properties` file:

```properties
default-balance=25000.00
claim-cost=10000.00
base-salary=1000.00
```

Every connected player receives the base salary when the in-world date changes
at midnight. Payday detects the date change rather than requiring an exact
midnight tick, so players are still paid when sleeping skips past midnight.
Other plugins can access the API through the loaded plugin instance:

```java
RisingWorldStarter economyPlugin =
        (RisingWorldStarter) getPluginByName("RisingWorldStarter");
EconomyApi economy = economyPlugin.getEconomyApi();

economy.deposit(player.getUID(), 500);       // adds $5.00
boolean paid = economy.withdraw(player.getUID(), 250);
economyPlugin.updateBalanceLabel(player);    // refresh connected player's HUD
```

## Land claims

Land ownership is stored by horizontal chunk in
`Plugins/RisingWorldStarter/claims.properties`. Available chat commands:

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
is stored by player UID in `claim-admins.properties`.

## Project layout

```text
src/        Java source code
resources/  plugin.yml, packaged into the root of the JAR
build/      generated output (safe to delete)
build.gradle and settings.gradle  portable Gradle build configuration
gradlew and gradlew.bat            Gradle wrapper launchers
```

Your main class must extend `net.risingworld.api.Plugin` and implement `onEnable()` and `onDisable()`. Its full package/class name must exactly match `main:` in `resources/plugin.yml`. The build packages this definition as `resources/plugin.yml` inside the JAR, as required by Rising World.

For the current API reference, open <https://javadoc.rising-world.net/>. The official setup guide says the game ships the SDK under `Data/SDK` and its JDK under `Data/Java/JDK`.

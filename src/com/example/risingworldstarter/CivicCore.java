package com.example.risingworldstarter;

import com.example.risingworldstarter.autotrim.WindowTrimService;
import com.example.risingworldstarter.claims.ChestOwnership;
import com.example.risingworldstarter.claims.ChestService;
import com.example.risingworldstarter.claims.Claim;
import com.example.risingworldstarter.claims.ClaimAdminService;
import com.example.risingworldstarter.claims.ClaimedChunk;
import com.example.risingworldstarter.claims.ClaimService;
import com.example.risingworldstarter.commands.CommandAction;
import com.example.risingworldstarter.commands.CommandHelp;
import com.example.risingworldstarter.commands.CommandRegistry;
import com.example.risingworldstarter.commands.RegisteredCommand;
import com.example.risingworldstarter.database.Database;
import com.example.risingworldstarter.database.SqliteDatabase;
import com.example.risingworldstarter.economy.DatabaseEconomyService;
import com.example.risingworldstarter.economy.EconomyApi;
import com.example.risingworldstarter.economy.EconomySettings;
import com.example.risingworldstarter.groups.Group;
import com.example.risingworldstarter.groups.GroupMember;
import com.example.risingworldstarter.groups.GroupRole;
import com.example.risingworldstarter.groups.GroupService;
import com.example.risingworldstarter.journal.JournalPage;
import com.example.risingworldstarter.journal.JournalSection;
import com.example.risingworldstarter.journal.JournalService;
import com.example.risingworldstarter.userstore.UserStoreListing;
import com.example.risingworldstarter.userstore.UserStoreService;
import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.Timer;
import net.risingworld.api.World;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.Cancellable;
import net.risingworld.api.events.general.SkipNightEvent;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerChangeEquippedItemEvent;
import net.risingworld.api.events.player.PlayerChangePositionEvent;
import net.risingworld.api.events.player.PlayerDisconnectEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.events.player.PlayerStorageAccessEvent;
import net.risingworld.api.events.player.ui.PlayerUIElementClickEvent;
import net.risingworld.api.events.player.ui.PlayerUIInputTextEvent;
import net.risingworld.api.events.player.ui.PlayerUITextFieldChangeEvent;
import net.risingworld.api.events.player.inventory.PlayerInventoryAddItemEvent;
import net.risingworld.api.events.player.world.*;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items;
import net.risingworld.api.definitions.Constructions;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Inventory;
import net.risingworld.api.objects.Skin;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.MessageBoxButtons;
import net.risingworld.api.ui.UIScrollView;
import net.risingworld.api.ui.UITextField;
import net.risingworld.api.ui.UITarget;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.ScaleMode;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.WhiteSpace;
import net.risingworld.api.ui.style.Overflow;
import net.risingworld.api.ui.style.Unit;
import net.risingworld.api.utils.Utils;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import net.risingworld.api.worldelements.Area3D;
import net.risingworld.api.worldelements.GameObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal entry point for a Rising World (Unity version) plugin.
 */
public final class CivicCore extends Plugin implements Listener {
    private static final int CLAIM_OVERVIEW_RADIUS = 5;
    private static final int[] SKIN_COLORS = {
            0xF1C27D, 0xE0AC69, 0xC68642, 0xA66A3F, 0x8D5524, 0x5C3317
    };
    private static final int[] HAIR_COLORS = {
            0x17120F, 0x3B2417, 0x6B4423, 0xA56B46, 0xD8B46A, 0xB9B9B9, 0x7A1F16
    };
    private static final int[] EYE_COLORS = {
            0x2B1B12, 0x634E34, 0x3D6B3D, 0x3F6F8F, 0x7289A3, 0x58456B
    };
    private static final int[] MALE_HAIRSTYLES = {
            0, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68
    };
    private static final int[] FEMALE_HAIRSTYLES = {
            0, 100, 102, 103, 104, 105, 106, 107, 108, 109, 110,
            111, 112, 113, 114, 115, 116, 117, 118, 119
    };
    private static final int[] BEARDS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30
    };
    private final Map<String, UILabel> balanceLabels = new ConcurrentHashMap<>();
    private final Map<String, UILabel> worldTimeLabels = new ConcurrentHashMap<>();
    private final Map<String, List<Area3D>> claimVisuals = new ConcurrentHashMap<>();
    private final Map<String, String> visualModes = new ConcurrentHashMap<>();
    private final Map<String, Float> visualHeights = new ConcurrentHashMap<>();
    private final Map<String, StoreView> storeViews = new ConcurrentHashMap<>();
    private final Map<String, AdminView> adminViews = new ConcurrentHashMap<>();
    private final Map<String, AboutView> aboutViews = new ConcurrentHashMap<>();
    private final Map<String, CommandListView> commandListViews = new ConcurrentHashMap<>();
    private final Map<String, JournalView> journalViews = new ConcurrentHashMap<>();
    private final Map<String, UserStoreView> userStoreViews = new ConcurrentHashMap<>();
    private final Map<String, CharacterService.CharacterSummary> activeCharacters = new ConcurrentHashMap<>();
    private final Map<String, String> activeClaimIdentities = new ConcurrentHashMap<>();
    private final Map<String, CharacterSelectionView> characterSelectionViews = new ConcurrentHashMap<>();
    private final Map<String, AppearanceView> appearanceViews = new ConcurrentHashMap<>();
    private final Map<String, Long> claimProtectionNotices = new ConcurrentHashMap<>();
    private final Map<String, Long> deniedGrassRewardsUntil = new ConcurrentHashMap<>();
    private final Map<String, Short> lastEquippedItemTypes = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastEquippedItemVariants = new ConcurrentHashMap<>();
    private final Map<String, String> lastEquippedConstructionNames = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastEquippedConstructionIds = new ConcurrentHashMap<>();
    private final Map<String, Vector3f> lastEquippedConstructionSizes = new ConcurrentHashMap<>();
    private final Map<String, Long> autoTrimScheduledAt = new ConcurrentHashMap<>();
    private final CommandRegistry commandRegistry = new CommandRegistry();
    private Database database;
    private EconomyApi economy;
    private ClaimService claims;
    private ClaimAdminService claimAdmins;
    private ChestService chests;
    private GroupService groups;
    private JournalService journals;
    private UserStoreService userStore;
    private EconomySettings economySettings;
    private StoreCatalog storeCatalog;
    private Path marketplaceConfigPath;
    private volatile boolean storeCatalogLoaded;
    private CharacterService characterService;
    private WindowTrimService windowTrimService;
    private Timer worldClockTimer;
    private Timer characterAutosaveTimer;
    private PayPeriod lastSalaryPeriod;
    private volatile boolean claimAdminOverrideEnabled = false;

    @Override
    public void onEnable() {
        Path pluginPath = Path.of(getPath()).toAbsolutePath().normalize();
        debug("Starting plugin initialization");
        debug("Plugin data directory: " + pluginPath);

        Path worldFolder = World.getWorldFolder().toPath().toAbsolutePath().normalize();
        // Keep persistent data directly in the game's world save. Steam Cloud
        // synchronizes world content, but plugin-installation folders are not
        // part of the portable save on every platform.
        Path worldDataPath = worldFolder.resolve("CivicCore");
        debug("World-scoped data directory: " + worldDataPath);
        prepareWorldDataDirectory(pluginPath, worldDataPath);
        if (!isEnabledForWorld(worldDataPath.resolve("plugin.properties"))) {
            System.out.println("[CivicCore] Not enabled for world " + World.getName()
                    + ": create " + worldDataPath.resolve("plugin.properties") + " to opt in");
            return;
        }

        database = new SqliteDatabase(worldDataPath.resolve("civiccore.db"));
        DatabaseEconomyService databaseEconomy = new DatabaseEconomyService(database);
        economy = databaseEconomy;
        claims = new ClaimService(database);
        claimAdmins = new ClaimAdminService(database);
        chests = new ChestService(database);
        groups = new GroupService(database);
        journals = new JournalService(database);
        userStore = new UserStoreService(database);
        characterService = new CharacterService(database);
        groups.migrateLegacy(worldDataPath.resolve("groups.properties"));
        if (LegacyStateMigrator.migrate(database, worldDataPath, databaseEconomy,
                claims, claimAdmins, chests, characterService)) {
            debug("Migrated legacy mutable state into civiccore.db; original files retained as backups");
        }
        debug("Database state loaded: " + claims.getClaimCount() + " claims, "
                + claimAdmins.getAll().size() + " claim administrators");
        windowTrimService = new WindowTrimService(CivicCore::debug);
        debug("Window auto-trim service loaded");

        Path economyConfigPath = worldDataPath.resolve("economy.properties");
        economySettings = EconomySettings.load(economyConfigPath);
        debug("Economy config loaded from " + economyConfigPath);
        debug("Economy values: starting cash=" + formatBalance(economySettings.defaultBalance())
                + ", claim cost=" + formatBalance(economySettings.claimCost())
                + ", 8-hour salary=" + formatBalance(economySettings.baseSalary()));

        // Item definitions are native game data and are not ready yet while a
        // hosted world is starting. Loading them here can terminate the game
        // process without a Java exception. Defer catalog creation until the
        // first player has spawned, when the definition registry is available.
        marketplaceConfigPath = worldDataPath.resolve("marketplace.json");
        storeCatalog = StoreCatalog.empty();
        storeCatalogLoaded = false;
        debug("Marketplace config queued for world-ready loading from " + marketplaceConfigPath);

        net.risingworld.api.objects.Time currentTime = Server.getGameTime();
        lastSalaryPeriod = PayPeriod.from(currentTime);
        debug(String.format(Locale.US, "World clock initialized: %d-%d-%d %02d:%02d",
                currentTime.getYear(), currentTime.getMonth(), currentTime.getDay(),
                currentTime.getHours(), currentTime.getMinutes()));
        registerCommands();
        registerEventListener(this);
        debug("Event listener registered");
        executeDelayed(0.5f, () -> {
            for (Player player : Server.getAllPlayers()) {
                if (player.isSpawned() && !activeCharacters.containsKey(player.getUID())) {
                    initializeStoreCatalog();
                    initializeCharacterSession(player);
                }
            }
        });
        worldClockTimer = new Timer(1f, 0f, -1, this::updateWorldClockLabels);
        worldClockTimer.start();
        characterAutosaveTimer = new Timer(60f, 60f, -1, this::saveActiveCharacters);
        characterAutosaveTimer.start();
        debug("World clock and payroll timer started; payroll runs at 00:00, 08:00, and 16:00");
        debug("Commands registered: " + commandRegistry.getCommands().stream()
                .map(RegisteredCommand::name).toList());
        System.out.println("[CivicCore] Enabled on Rising World " + getGameVersion());
    }

    private void prepareWorldDataDirectory(Path pluginPath, Path worldDataPath) {
        Path worldFolder = World.getWorldFolder().toPath().toAbsolutePath().normalize();
        Path previousWorldDataPath = worldFolder.resolve("RisingWorldStarter");
        Path olderWorldDataPath = worldFolder.resolve("plugins").resolve("RisingWorldStarter");

        try {
            Files.createDirectories(worldDataPath);
            copyLegacyFile(previousWorldDataPath.resolve("plugin.properties"),
                    worldDataPath.resolve("plugin.properties"));
            copyLegacyFile(olderWorldDataPath.resolve("plugin.properties"),
                    worldDataPath.resolve("plugin.properties"));
            copyLegacyFile(previousWorldDataPath.resolve("economy.properties"),
                    worldDataPath.resolve("economy.properties"));
            copyLegacyFile(olderWorldDataPath.resolve("economy.properties"),
                    worldDataPath.resolve("economy.properties"));
            copyLegacyFile(previousWorldDataPath.resolve("marketplace.json"),
                    worldDataPath.resolve("marketplace.json"));
            copyLegacyFile(olderWorldDataPath.resolve("marketplace.json"),
                    worldDataPath.resolve("marketplace.json"));
            copyLegacyFile(previousWorldDataPath.resolve("groups.properties"),
                    worldDataPath.resolve("groups.properties"));
            copyLegacyFile(olderWorldDataPath.resolve("groups.properties"),
                    worldDataPath.resolve("groups.properties"));
            copyLegacyFile(pluginPath.resolve("economy.properties"),
                    worldDataPath.resolve("economy.properties"));
            copyLegacyFile(pluginPath.resolve("marketplace.json"),
                    worldDataPath.resolve("marketplace.json"));
            Path worldMigrationMarker = worldDataPath.resolve("migration.complete");
            if (!Files.exists(worldMigrationMarker)) {
                boolean hasPreviousWorldData = Files.exists(previousWorldDataPath.resolve("balances.properties"))
                        || Files.exists(previousWorldDataPath.resolve("claims.properties"))
                        || Files.exists(previousWorldDataPath.resolve("claim-admins.properties"))
                        || Files.exists(previousWorldDataPath.resolve("chests.properties"))
                        || Files.exists(previousWorldDataPath.resolve("groups.properties"))
                        || Files.isDirectory(previousWorldDataPath.resolve("characters"));
                boolean hasOlderWorldData = Files.exists(olderWorldDataPath.resolve("balances.properties"))
                        || Files.exists(olderWorldDataPath.resolve("claims.properties"))
                        || Files.exists(olderWorldDataPath.resolve("claim-admins.properties"))
                        || Files.exists(olderWorldDataPath.resolve("chests.properties"))
                        || Files.exists(olderWorldDataPath.resolve("groups.properties"))
                        || Files.isDirectory(olderWorldDataPath.resolve("characters"));
                boolean hasFilesInWorldRoot = Files.exists(worldFolder.resolve("balances.properties"))
                        || Files.exists(worldFolder.resolve("claims.properties"))
                        || Files.exists(worldFolder.resolve("claim-admins.properties"))
                        || Files.exists(worldFolder.resolve("chests.properties"))
                        || Files.exists(worldFolder.resolve("groups.properties"))
                        || Files.isDirectory(worldFolder.resolve("characters"));
                Path legacySource = hasPreviousWorldData ? previousWorldDataPath
                        : hasOlderWorldData ? olderWorldDataPath
                        : hasFilesInWorldRoot ? worldFolder : pluginPath;
                Path globalMigrationMarker = pluginPath.resolve("legacy-data-world.txt");
                boolean mayImportGlobalData = hasPreviousWorldData || hasOlderWorldData || hasFilesInWorldRoot
                        || !Files.exists(globalMigrationMarker);
                if (mayImportGlobalData) {
                    copyLegacyFile(legacySource.resolve("balances.properties"),
                        worldDataPath.resolve("balances.properties"));
                    copyLegacyFile(legacySource.resolve("claims.properties"),
                        worldDataPath.resolve("claims.properties"));
                    copyLegacyFile(legacySource.resolve("claim-admins.properties"),
                        worldDataPath.resolve("claim-admins.properties"));
                    copyLegacyFile(legacySource.resolve("chests.properties"),
                        worldDataPath.resolve("chests.properties"));
                    copyLegacyFile(legacySource.resolve("groups.properties"),
                        worldDataPath.resolve("groups.properties"));
                    copyLegacyDirectory(legacySource.resolve("characters"),
                        worldDataPath.resolve("characters"));
                    if (!hasPreviousWorldData && !hasFilesInWorldRoot) {
                        Files.writeString(globalMigrationMarker, World.getName() + System.lineSeparator(),
                                StandardCharsets.UTF_8);
                    }
                    debug("Migrated existing data from " + legacySource
                            + "; original files retained as backup");
                }
                Files.writeString(worldMigrationMarker, "complete" + System.lineSeparator(),
                        StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize world-scoped plugin data", exception);
        }
    }

    private static boolean isEnabledForWorld(Path configPath) {
        if (!Files.exists(configPath)) return false;
        Properties properties = new Properties();
        try (var input = Files.newInputStream(configPath)) {
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load world plugin settings from " + configPath, exception);
        }
    }

    private static void copyLegacyFile(Path source, Path destination) throws IOException {
        if (Files.exists(source) && !Files.exists(destination)) {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void copyLegacyDirectory(Path source, Path destination) throws IOException {
        if (!Files.isDirectory(source) || Files.exists(destination)) return;
        try (var paths = Files.walk(source)) {
            for (Path sourcePath : paths.toList()) {
                Path targetPath = destination.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) Files.createDirectories(targetPath);
                else Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    @Override
    public void onDisable() {
        if (characterService != null) {
            saveActiveCharacters();
        }
        if (worldClockTimer != null) {
            worldClockTimer.kill();
            worldClockTimer = null;
        }
        lastSalaryPeriod = null;
        balanceLabels.clear();
        worldTimeLabels.clear();
        claimVisuals.clear();
        visualModes.clear();
        visualHeights.clear();
        storeViews.clear();
        adminViews.clear();
        aboutViews.clear();
        commandListViews.clear();
        if (journals != null) {
            for (JournalView view : journalViews.values()) {
                try { journals.savePage(view.characterKey(), view.pageId(), view.draft()); }
                catch (RuntimeException exception) { System.err.println("[CivicCore] Could not save journal: " + exception.getMessage()); }
            }
        }
        journalViews.clear();
        userStoreViews.clear();
        activeCharacters.clear();
        activeClaimIdentities.clear();
        characterSelectionViews.clear();
        appearanceViews.clear();
        claimProtectionNotices.clear();
        deniedGrassRewardsUntil.clear();
        lastEquippedItemTypes.clear();
        lastEquippedItemVariants.clear();
        lastEquippedConstructionNames.clear();
        lastEquippedConstructionIds.clear();
        lastEquippedConstructionSizes.clear();
        autoTrimScheduledAt.clear();
        storeCatalogLoaded = false;
        if (database != null) {
            database.close();
            database = null;
        }
        System.out.println("[CivicCore] Disabled");
    }

    /** Returns this plugin's economy API for use by other plugins. */
    public EconomyApi getEconomyApi() {
        if (economy == null) {
            throw new IllegalStateException("Economy is not available before the plugin is enabled");
        }
        return economy;
    }

    /** Returns the world-scoped land-claim service for integrations with other plugins. */
    public ClaimService getClaimService() {
        if (claims == null) {
            throw new IllegalStateException("Claims are not available before the plugin is enabled");
        }
        return claims;
    }

    /** Returns the world-scoped clan service for integrations with other plugins. */
    public GroupService getGroupService() {
        if (groups == null) throw new IllegalStateException("Clans are not available before the plugin is enabled");
        return groups;
    }

    /** Returns the shared registry so other plugins can register commands and actions. */
    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    /** Returns the economy/claim identity for the player's selected character. */
    public String getActiveCharacterKey(Player player) {
        return characterKey(player);
    }

    @EventMethod
    public void onPlayerSpawn(PlayerSpawnEvent event) {
        if (!activeCharacters.containsKey(event.getPlayer().getUID())) {
            characterService.captureProfileAppearance(event.getPlayer());
            debug("Captured native profile appearance for " + event.getPlayer().getUID());
        }
        initializeStoreCatalog();
        Player player = event.getPlayer();
        debug("Player spawned; scheduling character initialization for " + player.getName());
        executeDelayed(1f, () -> {
            if (player.isSpawned()) {
                initializeCharacterSession(player);
            }
        });
    }

    private synchronized void initializeStoreCatalog() {
        if (storeCatalogLoaded) {
            return;
        }
        storeCatalog = StoreCatalog.load(marketplaceConfigPath);
        storeCatalogLoaded = true;
        debug("Marketplace config loaded from " + marketplaceConfigPath);
        debug("Marketplace enabled items: " + storeCatalog.items().size());
    }

    private void initializeCharacterSession(Player player) {
        if (activeCharacters.containsKey(player.getUID()) || characterSelectionViews.containsKey(player.getUID())) {
            return;
        }
        debug("Initializing character session for account " + player.getUID());
        CharacterService.CharacterSummary legacy = characterService.ensureLegacyCharacter(player);
        debug("Character slot data ready for account " + player.getUID());
        if (!economy.hasAccount(legacy.economyKey())) {
            long legacyBalance = economy.hasAccount(player.getUID())
                    ? economy.getBalance(player.getUID()) : economySettings.defaultBalance();
            economy.createAccount(legacy.economyKey(), legacyBalance);
        }
        claims.migrateOwner(player.getUID(), legacy.economyKey(), legacy.name());
        showCharacterSelection(player);
    }

    @EventMethod
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        CharacterService.CharacterSummary active = activeCharacters.remove(event.getPlayer().getUID());
        activeClaimIdentities.remove(event.getPlayer().getUID());
        if (active != null) characterService.saveCharacter(event.getPlayer(), active);
        balanceLabels.remove(event.getPlayer().getUID());
        worldTimeLabels.remove(event.getPlayer().getUID());
        claimVisuals.remove(event.getPlayer().getUID());
        visualModes.remove(event.getPlayer().getUID());
        visualHeights.remove(event.getPlayer().getUID());
        storeViews.remove(event.getPlayer().getUID());
        adminViews.remove(event.getPlayer().getUID());
        aboutViews.remove(event.getPlayer().getUID());
        commandListViews.remove(event.getPlayer().getUID());
        JournalView journalView = journalViews.remove(event.getPlayer().getUID());
        if (journalView != null) saveJournalPage(event.getPlayer(), journalView, false);
        userStoreViews.remove(event.getPlayer().getUID());
        characterSelectionViews.remove(event.getPlayer().getUID());
        appearanceViews.remove(event.getPlayer().getUID());
        claimProtectionNotices.remove(event.getPlayer().getUID());
        deniedGrassRewardsUntil.remove(event.getPlayer().getUID());
        lastEquippedItemTypes.remove(event.getPlayer().getUID());
        lastEquippedItemVariants.remove(event.getPlayer().getUID());
        lastEquippedConstructionNames.remove(event.getPlayer().getUID());
        lastEquippedConstructionIds.remove(event.getPlayer().getUID());
        lastEquippedConstructionSizes.remove(event.getPlayer().getUID());
        autoTrimScheduledAt.remove(event.getPlayer().getUID());
    }

    @EventMethod
    public void onPlayerChangeEquippedItem(PlayerChangeEquippedItemEvent event) {
        Item item = event.getItem();
        // Keep the last real item when the final unit disappears; the native
        // planting transaction may unequip it before its placement event fires.
        if (item != null && item.isValid()) {
            lastEquippedItemTypes.put(event.getPlayer().getUID(), item.getTypeID());
            lastEquippedItemVariants.put(event.getPlayer().getUID(), item.getVariant());
            if (item instanceof Item.ConstructionItem constructionItem) {
                String uid = event.getPlayer().getUID();
                String name = constructionItem.getConstructionName();
                if (name != null) lastEquippedConstructionNames.put(uid, name);
                lastEquippedConstructionIds.put(uid,
                        Byte.toUnsignedInt(constructionItem.getConstructionID()));
                Vector3f size = constructionItem.getSize();
                if (size != null) lastEquippedConstructionSizes.put(uid, size.copy());
            }
        }
    }

    /* Claim protection uses the chunk reported by the affected world element,
       rather than the chunk in which the player happens to be standing. */
    @EventMethod public void onPlaceConstruction(PlayerPlaceConstructionEvent event) {
        Vector3i playerChunk = event.getPlayer().getChunkPosition();
        protectOwnedLand(event, playerChunk.x, playerChunk.z);
        int constructionTypeId = Byte.toUnsignedInt(event.getTypeID());
        String equippedConstructionName = lastEquippedConstructionNames.get(event.getPlayer().getUID());
        // The bundled definitions database currently lists window1-window10 as
        // 100-109, while some runtime builds report additional window choices
        // (including 111). Reserve the contiguous extension for those windows.
        boolean windowConstruction = constructionTypeId >= 100 && constructionTypeId <= 119;
        if (!windowConstruction && event.getConstructionDefinition() != null) {
            windowConstruction = event.getConstructionDefinition().type == Constructions.Type.Window;
        }
        if (!windowConstruction && equippedConstructionName != null) {
            windowConstruction = equippedConstructionName.toLowerCase(Locale.US).startsWith("window");
        }
        if (!event.isCancelled() && windowConstruction) {
            Vector3f cachedSize = lastEquippedConstructionSizes.get(event.getPlayer().getUID());
            Vector3f openingSize = cachedSize == null ? event.getSize().copy() : cachedSize.copy();
            if (scheduleAutoTrim(event.getPlayer(), event.getPlayer().getPosition().copy(),
                    event.getRotation().copy(), openingSize, false, true)) {
                event.getPlayer().sendTextMessage("<color=#AAAAAA>Auto-trim checking window geometry...</color>");
                debug("Auto-trim scheduled for construction type " + constructionTypeId
                        + " placed by " + event.getPlayer().getName());
            }
        }
    }
    @EventMethod public void onDestroyConstruction(PlayerDestroyConstructionEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onRemoveConstruction(PlayerRemoveConstructionEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onCreativeRemoveConstruction(PlayerCreativeRemoveConstructionEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onEditConstruction(PlayerEditConstructionEvent event) {
        protect(event, event.getChunkPositionX(), event.getChunkPositionZ());
        if (!event.isCancelled()) protectOwnedLand(event, event.getNewChunkPositionX(), event.getNewChunkPositionZ());
    }
    @EventMethod public void onHitConstruction(PlayerHitConstructionEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }

    @EventMethod public void onPlaceObject(PlayerPlaceObjectEvent event) {
        Vector3i playerChunk = event.getPlayer().getChunkPosition();
        protectOwnedLand(event, playerChunk.x, playerChunk.z);
        if (!event.isCancelled() && isStorage(event.getObjectDefinition())) {
            Claim claim = claims.getClaim(event.getChunkPositionX(), event.getChunkPositionZ()).orElse(null);
            if (claim != null) chests.assign(event.getGlobalID(), event.getChunkPositionX(),
                    event.getChunkPositionY(), event.getChunkPositionZ(), claim.ownerUid(), claim.ownerName());
        }
        if (!event.isCancelled() && isWindowObject(event.getObjectDefinition())) {
            Vector3f openingSize = event.getObjectDefinition().boundscale == null
                    ? new Vector3f(1.2f, 2.1f, 0.25f)
                    : event.getObjectDefinition().boundscale.mult(event.getScale());
            if (scheduleAutoTrim(event.getPlayer(), event.getPosition().copy(), event.getRotation().copy(),
                    openingSize, false, false)) {
                event.getPlayer().sendTextMessage("<color=#AAAAAA>Auto-trim checking window geometry...</color>");
            }
        }
    }
    @EventMethod public void onDestroyObject(PlayerDestroyObjectEvent event) {
        protect(event, event.getChunkPositionX(), event.getChunkPositionZ());
        if (!event.isCancelled()) removeChestOwnership(event.getGlobalID(), event.getChunkPositionX(),
                event.getChunkPositionY(), event.getChunkPositionZ());
    }
    @EventMethod public void onRemoveObject(PlayerRemoveObjectEvent event) {
        protect(event, event.getChunkPositionX(), event.getChunkPositionZ());
        if (!event.isCancelled()) removeChestOwnership(event.getGlobalID(), event.getChunkPositionX(),
                event.getChunkPositionY(), event.getChunkPositionZ());
    }
    @EventMethod public void onCreativeRemoveObject(PlayerCreativeRemoveObjectEvent event) {
        protect(event, event.getChunkPositionX(), event.getChunkPositionZ());
        if (!event.isCancelled()) removeChestOwnership(event.getGlobalID(), event.getChunkPositionX(),
                event.getChunkPositionY(), event.getChunkPositionZ());
    }
    @EventMethod public void onHitObject(PlayerHitObjectEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onChangeObjectColor(PlayerChangeObjectColorEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onChangeObjectInfo(PlayerChangeObjectInfoEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onChangeObjectStatus(PlayerChangeObjectStatusEvent event) {
        // Opening a storage is governed by its own lock. Other object state
        // changes (doors, lights, etc.) remain covered by chunk protection.
        if (!isStorage(event.getObjectDefinition())) {
            protectInteraction(event, event.getChunkPositionX(), event.getChunkPositionZ());
        }
    }

    @EventMethod
    public void onStorageAccess(PlayerStorageAccessEvent event) {
        ChestOwnership ownership = getOrAssignChest(event.getGlobalID(), event.getChunkPositionX(),
                event.getChunkPositionY(), event.getChunkPositionZ());
        if (ownership == null || !ownership.locked()) return;
        String identity = activeClaimIdentity(event.getPlayer());
        if (canAccessOwner(identity, ownership.ownerUid())
                || (isClaimAdmin(event.getPlayer()) && claimAdminOverrideEnabled)) return;
        event.setCancelled(true);
        event.getPlayer().sendTextMessage("<color=#FF7777>This chest is locked by "
                + ownership.ownerName() + ".</color>");
    }

    @EventMethod public void onPlaceVegetation(PlayerPlaceVegetationEvent event) {
        Player player = event.getPlayer();
        Item seed = player.getEquippedItem();
        short seedType = seed != null && seed.isValid() ? seed.getTypeID()
                : lastEquippedItemTypes.getOrDefault(player.getUID(), (short) -1);
        int seedVariant = seed != null && seed.isValid() ? seed.getVariant()
                : lastEquippedItemVariants.getOrDefault(player.getUID(), 0);
        protectOwnedLand(event, event.getChunkPositionX(), event.getChunkPositionZ());
        if (event.isCancelled() && !player.isCreativeModeEnabled()) {
            refundConsumedItemAfterPlacement(player, seedType, seedVariant);
        }
    }
    @EventMethod public void onCreativePlaceVegetation(PlayerCreativePlaceVegetationEvent event) { protectOwnedLand(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onDestroyVegetation(PlayerDestroyVegetationEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onCreativeRemoveVegetation(PlayerCreativeRemoveVegetationEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onHitVegetation(PlayerHitVegetationEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }

    @EventMethod public void onPlaceTerrain(PlayerPlaceTerrainEvent event) { protectOwnedLand(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onDestroyTerrain(PlayerDestroyTerrainEvent event) { protectOwnedLand(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onCreativeTerrainEdit(PlayerCreativeTerrainEditEvent event) { protectOwnedLand(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onHitTerrain(PlayerHitTerrainEvent event) { protect(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onPlaceGrass(PlayerPlaceGrassEvent event) { protectOwnedLand(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onRemoveGrass(PlayerRemoveGrassEvent event) {
        protect(event, event.getChunkPositionX(), event.getChunkPositionZ());
        if (event.isCancelled()) {
            // Rising World awards cut grass separately from changing the grass
            // tile, so remember this denied cut long enough to reject its loot.
            deniedGrassRewardsUntil.put(event.getPlayer().getUID(),
                    System.currentTimeMillis() + 1500L);
        }
    }
    @EventMethod public void onPlaceWater(PlayerPlaceWaterEvent event) { protectOwnedLand(event, event.getChunkPositionX(), event.getChunkPositionZ()); }
    @EventMethod public void onRemoveWater(PlayerRemoveWaterEvent event) { protectOwnedLand(event, event.getChunkPositionX(), event.getChunkPositionZ()); }

    @EventMethod public void onPlaceBlueprint(PlayerPlaceBlueprintEvent event) {
        var bounds = event.getBounds();
        Vector3f center = bounds.getCenter();
        int minX = Utils.ChunkUtils.getChunkPositionX(center.x - bounds.getXExtent());
        int maxX = Utils.ChunkUtils.getChunkPositionX(center.x + bounds.getXExtent());
        int minZ = Utils.ChunkUtils.getChunkPositionZ(center.z - bounds.getZExtent());
        int maxZ = Utils.ChunkUtils.getChunkPositionZ(center.z + bounds.getZExtent());
        for (int x = minX; x <= maxX && !event.isCancelled(); x++) {
            for (int z = minZ; z <= maxZ && !event.isCancelled(); z++) protectOwnedLand(event, x, z);
        }
    }
    @EventMethod public void onPlaceItem(PlayerPlaceItemEvent event) {
        Vector3i playerChunk = event.getPlayer().getChunkPosition();
        protectOwnedLand(event, playerChunk.x, playerChunk.z);
        if (event.isCancelled()) return;
        Item inventoryItem = event.getInventoryItem();
        if (inventoryItem instanceof Item.ConstructionItem constructionItem) {
            Constructions.ConstructionDefinition definition = constructionItem.getConstructionDefinition();
            String name = constructionItem.getConstructionName();
            boolean window = (definition != null && definition.type == Constructions.Type.Window)
                    || (name != null && name.toLowerCase(Locale.US).startsWith("window"));
            if (window) {
                Vector3f size = constructionItem.getSize() == null
                        ? event.getScale().copy() : constructionItem.getSize().copy();
                if (scheduleAutoTrim(event.getPlayer(), event.getPlayer().getPosition().copy(),
                        event.getRotation().copy(), size, false, true)) {
                    event.getPlayer().sendTextMessage("<color=#AAAAAA>Auto-trim checking window item "
                            + name + "...</color>");
                    debug("Auto-trim scheduled from PlayerPlaceItemEvent for " + name + " (construction "
                            + Byte.toUnsignedInt(constructionItem.getConstructionID()) + ")");
                }
            }
        }
    }

    @EventMethod
    public void onInventoryAddItem(PlayerInventoryAddItemEvent event) {
        Long deniedUntil = deniedGrassRewardsUntil.get(event.getPlayer().getUID());
        if (deniedUntil == null) return;
        if (System.currentTimeMillis() > deniedUntil) {
            deniedGrassRewardsUntil.remove(event.getPlayer().getUID(), deniedUntil);
            return;
        }
        Item item = event.getItem();
        String itemName = item == null ? "" : item.getName();
        boolean grassReward = item != null && (item.getTypeID() == 398 || item.getTypeID() == 399
                || "grass".equalsIgnoreCase(itemName) || "grasspatch".equalsIgnoreCase(itemName));
        if (grassReward && event.getOrigin() == PlayerInventoryAddItemEvent.Origin.Harvest) {
            event.setCancelled(true);
            deniedGrassRewardsUntil.remove(event.getPlayer().getUID(), deniedUntil);
        }
    }

    private void protect(Cancellable event, Vector3f position) {
        Vector3i chunk = Utils.ChunkUtils.getChunkPosition(position);
        protect(event, chunk.x, chunk.z);
    }

    private static Vector3f toWorldPosition(int chunkX, int chunkY, int chunkZ,
                                            Vector3f localPosition) {
        Vector3f chunkOrigin = Utils.ChunkUtils.getGlobalPosition(
                new Vector3i(chunkX, chunkY, chunkZ), Vector3i.ZERO);
        return chunkOrigin.add(localPosition);
    }

    private void protectOwnedLand(Cancellable event, Vector3f position) {
        Vector3i chunk = Utils.ChunkUtils.getChunkPosition(position);
        protectOwnedLand(event, chunk.x, chunk.z);
    }

    private void protectOwnedLand(Cancellable event, int chunkX, int chunkZ) {
        protect(event, chunkX, chunkZ, true, false);
    }

    private void refundConsumedItemAfterPlacement(Player player, short typeId, int variant) {
        if (typeId < 0) {
            debug("Could not identify the consumed item for denied placement by " + player.getName());
            return;
        }
        // The native planting transaction decrements the stack after the
        // cancellable world event returns. Reconcile after that transaction.
        executeDelayed(0.15f, () -> {
            if (!player.isSpawned()) return;
            player.getInventory().addItem(typeId, variant, 1);
            player.getInventory().syncWithClient();
            debug("Refunded denied planting item " + typeId + ":" + variant
                    + " to " + player.getName());
        });
    }

    private void protect(Cancellable event, int chunkX, int chunkZ) {
        protect(event, chunkX, chunkZ, false, false);
    }

    private void protectInteraction(Cancellable event, int chunkX, int chunkZ) {
        protect(event, chunkX, chunkZ, false, true);
    }

    private void protect(Cancellable event, int chunkX, int chunkZ, boolean requiresOwnedLand,
                         boolean alwaysNotify) {
        if (event.isCancelled()) return;
        Player player = ((net.risingworld.api.events.player.PlayerEvent) event).getPlayer();
        Claim claim = claims.getClaim(chunkX, chunkZ).orElse(null);
        if (claim == null) {
            if (!requiresOwnedLand) return;
            event.setCancelled(true);
            debug("Denied " + event.getClass().getSimpleName() + " for " + player.getName()
                    + " in unclaimed chunk " + chunkX + "," + chunkZ);
            sendClaimProtectionNotice(player,
                    "Claim chunk " + chunkX + ", " + chunkZ
                            + " before placing items or modifying terrain. ["
                            + event.getClass().getSimpleName() + "]", alwaysNotify);
            return;
        }
        String activeClaimIdentity = activeClaimIdentity(player);
        boolean isOwner = canAccessOwner(activeClaimIdentity, claim.ownerUid());
        if (isOwner || (isClaimAdmin(player) && claimAdminOverrideEnabled)) return;

        event.setCancelled(true);
        debug("Denied " + event.getClass().getSimpleName() + " for " + player.getName()
                + " in chunk " + chunkX + "," + chunkZ + ": active claim identity="
                + activeClaimIdentity + ", owner=" + claim.ownerUid());
        sendClaimProtectionNotice(player, "This chunk is protected by " + claim.ownerName() + ".",
                alwaysNotify);
    }

    private void sendClaimProtectionNotice(Player player, String message) {
        sendClaimProtectionNotice(player, message, false);
    }

    private void sendClaimProtectionNotice(Player player, String message, boolean alwaysNotify) {
        if (alwaysNotify) {
            player.sendTextMessage("<color=#FF7777>" + message + "</color>");
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = claimProtectionNotices.put(player.getUID(), now);
        if (previous == null || now - previous >= 1500L) {
            player.sendTextMessage("<color=#FF7777>" + message + "</color>");
        }
    }

    @EventMethod
    public void onSkipNight(SkipNightEvent event) {
        if (!event.isCancelled()) {
            // The event fires before the clock jumps. Check shortly afterwards so
            // sleeping players have woken and the new world date is available.
            executeDelayed(1f, this::updateWorldClockLabels);
        }
    }

    @EventMethod
    public void onPlayerChangePosition(PlayerChangePositionEvent event) {
        Player player = event.getPlayer();
        List<Area3D> visuals = claimVisuals.get(player.getUID());
        if (visuals == null || visuals.isEmpty()) {
            return;
        }

        float newGroundY = event.getPosition().y - 0.15f;
        Float oldGroundY = visualHeights.get(player.getUID());
        if (oldGroundY != null && Math.abs(newGroundY - oldGroundY) < 0.1f) {
            return;
        }

        for (Area3D visual : visuals) {
            Area area = visual.getArea();
            Vector3f start = area.getStartPosition();
            Vector3f end = area.getEndPosition();
            area.setStartPosition(new Vector3f(start.x, newGroundY, start.z));
            area.setEndPosition(new Vector3f(end.x, newGroundY + 0.3f, end.z));
            visual.updateCoordinates();
        }
        visualHeights.put(player.getUID(), newGroundY);
    }

    @EventMethod
    public void onPlayerCommand(PlayerCommandEvent event) {
        String[] parts = event.getCommand().trim().split("\\s+");
        RegisteredCommand command = commandRegistry.find(parts[0]);
        if (command == null) {
            event.setCancelled(true);
            RegisteredCommand suggestion = commandRegistry.suggest(parts[0]);
            if (suggestion != null) {
                event.getPlayer().sendTextMessage("<color=#FFAA66>Unknown command " + parts[0]
                        + ". Did you mean </color><color=#77AAFF>" + suggestion.name()
                        + "</color><color=#FFAA66>?</color>");
            } else {
                event.getPlayer().sendTextMessage("<color=#FF7777>Invalid command: " + parts[0]
                        + ".</color> <color=#AAAAAA>Use </color><color=#77AAFF>/commands</color>"
                        + "<color=#AAAAAA> to see available commands.</color>");
            }
            return;
        }
        event.setCancelled(true);
        if (command.requiresCharacter() && !activeCharacters.containsKey(event.getPlayer().getUID())) {
            event.getPlayer().sendTextMessage("<color=#FFAA66>Select or create a character first.</color>");
            return;
        }
        if ((parts.length == 1 && command.usage().contains("<"))
                || (parts.length > 1 && command.usage().equalsIgnoreCase(command.name())
                && command.additionalHelp().isEmpty())) {
            sendInvalidCommand(event.getPlayer(), command);
            return;
        }
        try {
            command.action().execute(event.getPlayer(), parts);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String message = exception.getMessage();
            event.getPlayer().sendTextMessage("<color=#FF7777>"
                    + (message == null || message.isBlank() ? "Command could not be completed." : message)
                    + "</color>");
            sendCommandUsage(event.getPlayer(), command);
        }
    }

    private static void sendInvalidCommand(Player player, RegisteredCommand command) {
        player.sendTextMessage("<color=#FFAA66>Invalid or incomplete command.</color>");
        sendCommandUsage(player, command);
    }

    private static void sendCommandUsage(Player player, RegisteredCommand command) {
        player.sendTextMessage("<color=#AAAAAA>Usage:</color> <color=#77AAFF>"
                + command.usage() + "</color>");
        for (CommandHelp help : command.additionalHelp()) {
            player.sendTextMessage("<color=#AAAAAA>       </color><color=#77AAFF>"
                    + help.usage() + "</color>");
        }
    }

    private void registerCommands() {
        commandRegistry.unregisterOwner("CivicCore");
        registerCommand("General", "/help", "Show available CivicCore commands in chat.", false, List.of(),
                (player, parts) -> showHelp(player));
        registerCommand("General", "/commands", "Open the categorized command list.", false, List.of(),
                (player, parts) -> showCommands(player));
        registerCommand("General", "/about", "Show CivicCore information and version.", false, List.of(),
                (player, parts) -> showAbout(player));
        registerCommand("Character", "/journal", "Open your character journal.", true, List.of("/notes"),
                (player, parts) -> toggleJournal(player));
        registerCommand("Character", "/characters", "Open the character selector.", true,
                List.of("/character", "/chars"), (player, parts) -> openCharacterSwitcher(player));
        registerCommand("Character", "/syncappearance", "Copy your current profile appearance.", true, List.of(),
                (player, parts) -> syncProfileAppearance(player));
        registerCommand("Economy", "/balance", "Show your current cash balance.", true, List.of("/bal"),
                (player, parts) -> {
                    String formattedBalance = formatBalance(economy.getBalance(characterKey(player)));
                    player.sendTextMessage("<color=#E8C547>Cash:</color> " + formattedBalance);
                    updateBalanceLabel(player);
                });
        registerCommand("Marketplace", "/store", "Open or close the marketplace.", true, List.of(),
                (player, parts) -> toggleStore(player));
        registerCommand("Marketplace", "/userstore", "Open the player marketplace.", true, List.of("/ustore"), List.of(
                new CommandHelp("/userstore sell <price> [quantity]", "List the equipped item stack for sale.")),
                this::handleUserStoreCommand);
        registerCommand("Administration", "/admin", "Open the administrator dashboard.", true, List.of(),
                (player, parts) -> toggleAdminDashboard(player));
        registerCommand("Land Claims", "/claim", "Claim your current chunk.", true, List.of(),
                (player, parts) -> claimCurrentChunk(player));
        registerCommand("Land Claims", "/unclaim", "Release your current chunk.", true, List.of(),
                (player, parts) -> unclaimCurrentChunk(player));
        registerCommand("Land Claims", "/chunk", "Show the current chunk and its owner.", true, List.of(),
                (player, parts) -> showCurrentChunk(player, true));
        registerCommand("Land Claims", "/claims", "List and toggle your claimed chunks.", true, List.of(),
                (player, parts) -> listOwnedChunks(player));
        registerCommand("Land Claims", "/claimadmin <add|remove|list> [player]", "Manage claim administrators.", true,
                List.of(), this::handleClaimAdminCommand);
        registerCommand("Storage", "/chest <lock|unlock|status>", "Manage the chest you are looking at.", true,
                List.of(), this::handleChestCommand);
        registerCommand("Groups", "/clan", "Show clan command usage.", true, List.of("/group"), List.of(
                new CommandHelp("/clan create <name>", "Create a new clan."),
                new CommandHelp("/clan info", "Show clan members, roles, and claims."),
                new CommandHelp("/clan invite <character>", "Invite an online character."),
                new CommandHelp("/clan accept", "Accept a pending clan invitation."),
                new CommandHelp("/clan leave", "Leave your current clan."),
                new CommandHelp("/clan kick <character>", "Remove a clan member."),
                new CommandHelp("/clan promote <character>", "Promote a member to manager."),
                new CommandHelp("/clan demote <character>", "Demote a manager to member."),
                new CommandHelp("/clan balance", "View the clan treasury."),
                new CommandHelp("/clan deposit <amount>", "Deposit personal funds into the clan treasury."),
                new CommandHelp("/clan withdraw <amount>", "Withdraw clan funds to your character."),
                new CommandHelp("/clan claim", "Purchase the current chunk for the clan."),
                new CommandHelp("/clan unclaim", "Release the current clan-owned chunk."),
                new CommandHelp("/clan disband", "Disband the clan and release its claims.")), this::handleClanCommand);
    }

    private void registerCommand(String category, String usage, String description, boolean requiresCharacter,
                                 List<String> aliases, CommandAction action) {
        registerCommand(category, usage, description, requiresCharacter, aliases, List.of(), action);
    }

    private void registerCommand(String category, String usage, String description, boolean requiresCharacter,
                                 List<String> aliases, List<CommandHelp> additionalHelp, CommandAction action) {
        String primaryName = usage.split("\\s+", 2)[0];
        commandRegistry.register("CivicCore", primaryName, category, usage, description,
                requiresCharacter, aliases, additionalHelp, action);
    }

    private void showHelp(Player player) {
        player.sendTextMessage("<color=#E8C547>--- CivicCore Commands ---</color>");
        for (RegisteredCommand command : commandRegistry.getCommands().stream()
                .filter(registered -> registered.category().equalsIgnoreCase("General")).toList()) {
            String aliases = command.aliases().isEmpty()
                    ? ""
                    : " <color=#888888>(aliases: " + String.join(", ", command.aliases()) + ")</color>";
            player.sendTextMessage("<color=#77AAFF>" + command.usage() + "</color>" + aliases
                    + " - " + command.description());
        }
    }

    private void showCommands(Player player) {
        CommandListView previous = commandListViews.remove(player.getUID());
        if (previous != null) player.removeUIElement(previous.window());

        UIElement window = new UIElement();
        window.setPosition(50f, 50f, true);
        window.setPivot(Pivot.MiddleCenter);
        window.setSize(800f, 620f, false);
        window.setBackgroundColor((int) 0x161B22F8L);
        window.setBorder(2f);
        window.setBorderColor((int) 0xE8C547FFL);
        window.setBorderEdgeRadius(8f, false);

        UILabel title = new UILabel("CivicCore Commands");
        title.setPosition(24f, 14f, false);
        title.setSize(680f, 42f, false);
        title.setFontSize(28f);
        title.setFontColor((int) 0xF4E3A1FFL);
        title.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(title);

        UILabel closeButton = new UILabel("X");
        closeButton.setPosition(744f, 14f, false);
        closeButton.setSize(36f, 36f, false);
        closeButton.setFontSize(22f);
        closeButton.setTextAlign(TextAnchor.MiddleCenter);
        closeButton.setBackgroundColor((int) 0x8B2D2DFFL);
        closeButton.setClickable(true);
        window.addChild(closeButton);

        UIScrollView commandList = new UIScrollView(UIScrollView.ScrollViewMode.Vertical);
        commandList.setPosition(20f, 66f, false);
        commandList.setSize(760f, 530f, false);
        commandList.setVerticalScrollerVisibility(UIScrollView.ScrollerVisibility.Auto);
        commandList.setHorizontalScrollerVisibility(UIScrollView.ScrollerVisibility.Hidden);
        commandList.setMouseWheelScrollSize(48f);
        window.addChild(commandList);

        Map<String, List<RegisteredCommand>> commandsByCategory = new LinkedHashMap<>();
        for (RegisteredCommand command : commandRegistry.getCommands()) {
            commandsByCategory.computeIfAbsent(command.category(), ignored -> new ArrayList<>()).add(command);
        }

        float y = 0f;
        for (Map.Entry<String, List<RegisteredCommand>> category : commandsByCategory.entrySet()) {
            UILabel categoryLabel = new UILabel(category.getKey());
            categoryLabel.setPosition(8f, y, false);
            categoryLabel.setSize(720f, 34f, false);
            categoryLabel.setFontSize(20f);
            categoryLabel.setFontColor((int) 0xE8C547FFL);
            categoryLabel.setTextAlign(TextAnchor.MiddleLeft);
            categoryLabel.setBackgroundColor((int) 0x28313CFFL);
            commandList.addChild(categoryLabel);
            y += 38f;

            for (RegisteredCommand command : category.getValue()) {
                List<CommandHelp> rows = new ArrayList<>();
                rows.add(new CommandHelp(command.usage(), command.description()));
                rows.addAll(command.additionalHelp());
                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    CommandHelp row = rows.get(rowIndex);
                    String commandText = row.usage();
                    if (rowIndex == 0 && !command.aliases().isEmpty())
                        commandText += "  (" + String.join(", ", command.aliases()) + ")";
                    UILabel usage = new UILabel(commandText);
                    usage.setPosition(18f, y, false); usage.setSize(350f, 38f, false);
                    usage.setFontSize(15f); usage.setFontColor((int) 0x77AAFFFFL);
                    usage.setTextAlign(TextAnchor.MiddleLeft); commandList.addChild(usage);
                    UILabel description = new UILabel(row.description());
                    description.setPosition(376f, y, false); description.setSize(350f, 38f, false);
                    description.setFontSize(15f); description.setFontColor((int) 0xDDDDDDFFL);
                    description.setTextAlign(TextAnchor.MiddleLeft); commandList.addChild(description);
                    y += 42f;
                }
            }
            y += 8f;
        }

        commandListViews.put(player.getUID(), new CommandListView(window, closeButton));
        player.addUIElement(window);
        player.stopInput(true, true);
        player.setMouseCursorVisible(true);
    }

    private void closeCommands(Player player) {
        CommandListView view = commandListViews.remove(player.getUID());
        if (view != null) player.removeUIElement(view.window());
        boolean anotherDialogOpen = aboutViews.containsKey(player.getUID())
                || characterSelectionViews.containsKey(player.getUID())
                || appearanceViews.containsKey(player.getUID())
                || adminViews.containsKey(player.getUID())
                || storeViews.containsKey(player.getUID());
        if (!anotherDialogOpen) {
            player.stopInput(false, false);
            player.setMouseCursorVisible(false);
        }
    }

    private void showAbout(Player player) {
        AboutView previous = aboutViews.remove(player.getUID());
        if (previous != null) player.removeUIElement(previous.window());

        String version = getClass().getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) version = "0.8.3";

        UIElement window = new UIElement();
        window.setPosition(50f, 50f, true);
        window.setPivot(Pivot.MiddleCenter);
        window.setSize(680f, 340f, false);
        window.setBackgroundColor((int) 0x161B22F8L);
        window.setBorder(2f);
        window.setBorderColor((int) 0xE8C547FFL);
        window.setBorderEdgeRadius(8f, false);

        UILabel title = new UILabel("CivicCore");
        title.setPosition(28f, 22f, false);
        title.setSize(550f, 42f, false);
        title.setFontSize(29f);
        title.setFontColor((int) 0xF4E3A1FFL);
        title.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(title);

        UILabel closeButton = new UILabel("X");
        closeButton.setPosition(630f, 18f, false);
        closeButton.setSize(30f, 30f, false);
        closeButton.setFontSize(18f);
        closeButton.setFontColor((int) 0xFFFFFFFFL);
        closeButton.setTextAlign(TextAnchor.MiddleCenter);
        closeButton.setBackgroundColor((int) 0x8B2E35FFL);
        closeButton.setBorderEdgeRadius(4f, false);
        closeButton.setClickable(true);
        window.addChild(closeButton);

        UILabel versionLabel = new UILabel("Version " + version);
        versionLabel.setPosition(30f, 70f, false);
        versionLabel.setSize(620f, 30f, false);
        versionLabel.setFontSize(18f);
        versionLabel.setFontColor((int) 0xFFFFFFFFL);
        versionLabel.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(versionLabel);

        UILabel description = new UILabel("A roleplay foundation for persistent\n"
                + "characters, economy, marketplace,\n"
                + "land claims, and world administration.");
        description.setPosition(30f, 112f, false);
        description.setSize(620f, 78f, false);
        description.setFontSize(18f);
        description.setFontColor((int) 0xCCCCCCFFL);
        description.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(description);

        UILabel features = new UILabel("Persistent characters  •  Economy\n"
                + "Marketplace  •  Land claims  •  Administration tools");
        features.setPosition(30f, 198f, false);
        features.setSize(620f, 58f, false);
        features.setFontSize(16f);
        features.setFontColor((int) 0x77AAFFFFL);
        features.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(features);

        UILabel credits = new UILabel("Created by Adam Guthrie   |   MIT License");
        credits.setPosition(30f, 278f, false);
        credits.setSize(620f, 30f, false);
        credits.setFontSize(16f);
        credits.setFontColor((int) 0xAAAAAAFFL);
        credits.setTextAlign(TextAnchor.MiddleCenter);
        window.addChild(credits);

        aboutViews.put(player.getUID(), new AboutView(window, closeButton));
        player.addUIElement(window);
        player.stopInput(true, true);
        player.setMouseCursorVisible(true);
    }

    private void closeAbout(Player player) {
        AboutView view = aboutViews.remove(player.getUID());
        if (view != null) player.removeUIElement(view.window());
        boolean anotherDialogOpen = characterSelectionViews.containsKey(player.getUID())
                || appearanceViews.containsKey(player.getUID())
                || adminViews.containsKey(player.getUID())
                || storeViews.containsKey(player.getUID())
                || commandListViews.containsKey(player.getUID());
        if (!anotherDialogOpen) {
            player.stopInput(false, false);
            player.setMouseCursorVisible(false);
        }
    }

    private void toggleJournal(Player player) {
        JournalView current = journalViews.get(player.getUID());
        if (current != null) { closeJournal(player); return; }
        if (storeViews.containsKey(player.getUID())) closeStore(player);
        if (adminViews.containsKey(player.getUID())) closeAdminDashboard(player);
        if (aboutViews.containsKey(player.getUID())) closeAbout(player);
        if (commandListViews.containsKey(player.getUID())) closeCommands(player);
        openJournal(player, null, 0);
    }

    private void openJournal(Player player, Long preferredSectionId, int preferredPageIndex) {
        JournalView previous = journalViews.remove(player.getUID());
        if (previous != null) player.removeUIElement(previous.window());
        String characterKey = characterKey(player);
        List<JournalSection> sections = journals.open(characterKey);
        JournalSection section = sections.stream()
                .filter(candidate -> preferredSectionId != null && candidate.id() == preferredSectionId)
                .findFirst().orElse(sections.get(0));
        List<JournalPage> pages = journals.getPages(characterKey, section.id());
        int pageIndex = Math.max(0, Math.min(preferredPageIndex, pages.size() - 1));
        JournalPage page = pages.get(pageIndex);

        UIElement window = new UIElement();
        window.setPosition(50f, 50f, true); window.setPivot(Pivot.MiddleCenter);
        window.setSize(940f, 680f, false); window.setBackgroundColor((int) 0x171A20F8L);
        window.setBorder(2f); window.setBorderColor((int) 0xC8A96AFFL);
        window.setBorderEdgeRadius(8f, false);

        UILabel title = new UILabel("Journal — " + section.title());
        title.setPosition(24f, 14f, false); title.setSize(760f, 42f, false);
        title.setFontSize(27f); title.setFontColor((int) 0xF4E3A1FFL);
        title.setTextAlign(TextAnchor.MiddleLeft); window.addChild(title);

        UILabel close = journalButton("X", 884f, 16f, 32f, 34f);
        close.setBackgroundColor((int) 0x8B2D2DFFL); window.addChild(close);
        UILabel newSection = journalButton("+ Section", 20f, 62f, 190f, 36f);
        window.addChild(newSection);

        UIScrollView sectionList = new UIScrollView(UIScrollView.ScrollViewMode.Vertical);
        sectionList.setPosition(20f, 108f, false); sectionList.setSize(190f, 540f, false);
        sectionList.setVerticalScrollerVisibility(UIScrollView.ScrollerVisibility.Auto);
        sectionList.setHorizontalScrollerVisibility(UIScrollView.ScrollerVisibility.Hidden);
        window.addChild(sectionList);
        Map<Integer, Long> sectionsByButtonId = new ConcurrentHashMap<>();
        float sectionY = 0f;
        for (JournalSection item : sections) {
            UILabel button = journalButton(item.title(), 0f, sectionY, 176f, 38f);
            button.setBackgroundColor(item.id() == section.id() ? (int) 0x80652FFF : (int) 0x303844FFL);
            sectionList.addChild(button); sectionsByButtonId.put(button.getID(), item.id());
            sectionY += 44f;
        }

        UILabel pageLabel = new UILabel("Page " + (pageIndex + 1) + " of " + pages.size());
        pageLabel.setPosition(240f, 62f, false); pageLabel.setSize(660f, 36f, false);
        pageLabel.setFontSize(17f); pageLabel.setFontColor((int) 0xD8CBAAFFL);
        pageLabel.setTextAlign(TextAnchor.MiddleCenter); window.addChild(pageLabel);

        UITextField editor = new UITextField(page.content());
        editor.setPosition(240f, 108f, false); editor.setSize(670f, 475f, false);
        editor.setMaxCharacters(JournalService.MAX_PAGE_CHARACTERS);
        editor.setFontSize(17f); editor.setFontColor((int) 0xEEEEEEFFL);
        editor.setBackgroundColor((int) 0x222832FFL); editor.setBorder(1f);
        editor.setBorderColor((int) 0x6D7785FFL);
        editor.style.whiteSpace.set(WhiteSpace.Normal);
        editor.style.textAlign.set(TextAnchor.UpperLeft);
        editor.style.overflow.set(Overflow.Hidden);
        editor.style.paddingLeft.set(12f, Unit.Pixel);
        editor.style.paddingRight.set(12f, Unit.Pixel);
        editor.style.paddingTop.set(12f, Unit.Pixel);
        editor.style.paddingBottom.set(12f, Unit.Pixel);
        editor.style.minHeight.set(475f, Unit.Pixel);
        editor.style.maxHeight.set(475f, Unit.Pixel);
        editor.updateStyle();
        window.addChild(editor);

        UILabel previousPage = journalButton("< Previous", 240f, 600f, 130f, 40f);
        UILabel nextPage = journalButton("Next >", 380f, 600f, 130f, 40f);
        UILabel newPage = journalButton("+ Page", 600f, 600f, 120f, 40f);
        UILabel save = journalButton("Save", 730f, 600f, 180f, 40f);
        save.setBackgroundColor((int) 0x2E7D4FFF);
        previousPage.setClickable(pageIndex > 0); nextPage.setClickable(pageIndex + 1 < pages.size());
        window.addChild(previousPage); window.addChild(nextPage); window.addChild(newPage); window.addChild(save);

        JournalView view = new JournalView(window, close, newSection, previousPage, nextPage,
                newPage, save, editor, characterKey, section.id(), page.id(), pageIndex,
                pages.size(), page.content(), sectionsByButtonId);
        journalViews.put(player.getUID(), view);
        player.addUIElement(window);
        // UITextField's internal Unity control is created when the window is
        // attached. Switching modes afterward ensures the client creates a
        // multiline editor instead of stretching a single-line input shell.
        editor.setMultiLine(true);
        player.stopInput(true, true); player.setMouseCursorVisible(true);
    }

    private static UILabel journalButton(String text, float x, float y, float width, float height) {
        UILabel button = new UILabel(text); button.setPosition(x, y, false); button.setSize(width, height, false);
        button.setFontSize(16f); button.setFontColor((int) 0xFFFFFFFFL);
        button.setTextAlign(TextAnchor.MiddleCenter); button.setBackgroundColor((int) 0x3A4655FFL);
        button.setBorderEdgeRadius(4f, false); button.setClickable(true); return button;
    }

    private void saveJournalPage(Player player, JournalView view, boolean notify) {
        try {
            journals.savePage(view.characterKey(), view.pageId(), view.draft());
            if (notify) player.sendTextMessage("<color=#77FF99>Journal page saved.</color>");
        } catch (RuntimeException exception) {
            player.sendTextMessage("<color=#FF7777>Could not save journal page: " + exception.getMessage() + "</color>");
        }
    }

    private void closeJournal(Player player) {
        JournalView view = journalViews.remove(player.getUID());
        if (view == null) return;
        saveJournalPage(player, view, false); player.removeUIElement(view.window());
        boolean anotherDialogOpen = aboutViews.containsKey(player.getUID())
                || commandListViews.containsKey(player.getUID()) || characterSelectionViews.containsKey(player.getUID())
                || appearanceViews.containsKey(player.getUID()) || adminViews.containsKey(player.getUID())
                || storeViews.containsKey(player.getUID());
        if (!anotherDialogOpen) { player.stopInput(false, false); player.setMouseCursorVisible(false); }
    }

    private void handleJournalClick(Player player, JournalView view, int elementId) {
        if (elementId == view.close().getID()) { closeJournal(player); return; }
        if (elementId == view.save().getID()) { saveJournalPage(player, view, true); return; }
        if (elementId == view.newSection().getID()) {
            saveJournalPage(player, view, false);
            player.showInputMessageBox("New journal section", "Section name", "", name -> {
                if (name == null) return;
                try {
                    JournalSection section = journals.createSection(view.characterKey(), name);
                    openJournal(player, section.id(), 0);
                } catch (RuntimeException exception) {
                    player.showErrorMessageBox("Could not create section", exception.getMessage());
                }
            });
            return;
        }
        Long sectionId = view.sectionsByButtonId().get(elementId);
        if (sectionId != null && sectionId != view.sectionId()) {
            saveJournalPage(player, view, false); openJournal(player, sectionId, 0); return;
        }
        if (elementId == view.newPage().getID()) {
            saveJournalPage(player, view, false);
            journals.createPage(view.characterKey(), view.sectionId());
            openJournal(player, view.sectionId(), view.pageCount()); return;
        }
        if (elementId == view.previousPage().getID() && view.pageIndex() > 0) {
            saveJournalPage(player, view, false); openJournal(player, view.sectionId(), view.pageIndex() - 1); return;
        }
        if (elementId == view.nextPage().getID() && view.pageIndex() + 1 < view.pageCount()) {
            saveJournalPage(player, view, false); openJournal(player, view.sectionId(), view.pageIndex() + 1);
        }
    }

    private void handleChestCommand(Player player, String[] parts) {
        if (parts.length != 2 || !(parts[1].equalsIgnoreCase("lock")
                || parts[1].equalsIgnoreCase("unlock") || parts[1].equalsIgnoreCase("status"))) {
            player.sendTextMessage("Usage: /chest lock, /chest unlock, or /chest status while looking at a chest");
            return;
        }
        player.getObjectElementInLineOfSight(6f, object -> {
            if (object == null || !object.isValid() || !isStorage(object.getDefinition())) {
                player.sendTextMessage("<color=#FFAA66>Look directly at a chest or storage object within 6 meters.</color>");
                return;
            }
            ChestOwnership ownership = getOrAssignChest(object.getGlobalID(), object.getChunkPositionX(),
                    object.getChunkPositionY(), object.getChunkPositionZ());
            if (ownership == null) {
                player.sendTextMessage("<color=#FFAA66>This chest is not inside a claimed chunk.</color>");
                return;
            }
            boolean ownsChest = canAccessOwner(activeClaimIdentity(player), ownership.ownerUid());
            boolean adminCanManage = isClaimAdmin(player) && claimAdminOverrideEnabled;
            if (!ownsChest && !adminCanManage) {
                player.sendTextMessage("<color=#FF7777>This chest belongs to "
                        + ownership.ownerName() + ".</color>");
                return;
            }
            if (parts[1].equalsIgnoreCase("status")) {
                player.sendTextMessage("<color=#E8C547>Chest owner:</color> " + ownership.ownerName()
                        + "     <color=#E8C547>Status:</color> "
                        + (ownership.locked() ? "Locked" : "Unlocked"));
                return;
            }
            boolean locked = parts[1].equalsIgnoreCase("lock");
            chests.setLocked(object.getGlobalID(), object.getChunkPositionX(), object.getChunkPositionY(),
                    object.getChunkPositionZ(), ownership, locked);
            player.sendTextMessage("<color=#77FF99>Chest " + (locked ? "locked" : "unlocked") + ".</color>");
        });
    }

    private void handleClanCommand(Player player, String[] parts) {
        if (parts.length < 2) { sendClanUsage(player); return; }
        String characterKey = characterKey(player);
        String action = parts[1].toLowerCase(Locale.US);
        try {
            switch (action) {
                case "create" -> {
                    if (parts.length < 3) throw new IllegalArgumentException("Usage: /clan create <name>");
                    Group group = groups.create(joinArguments(parts, 2), characterKey, player.getName());
                    player.sendTextMessage("<color=#77FF99>Created clan " + group.name()
                            + " and joined as Owner.</color>");
                }
                case "info", "members" -> showClanInfo(player, characterKey);
                case "invite" -> {
                    Player target = requireOnlineClanTarget(parts, 2);
                    Group group = groups.findByMember(characterKey)
                            .orElseThrow(() -> new IllegalStateException("You do not belong to a clan."));
                    groups.invite(group.id(), characterKey, characterKey(target));
                    player.sendTextMessage("<color=#77FF99>Invited " + target.getName() + " to " + group.name() + ".</color>");
                    target.sendTextMessage("<color=#E8C547>" + player.getName() + " invited you to clan "
                            + group.name() + ". Use /clan accept to join.</color>");
                }
                case "accept" -> {
                    Group group = groups.acceptInvitation(characterKey, player.getName());
                    player.sendTextMessage("<color=#77FF99>Joined clan " + group.name() + ".</color>");
                }
                case "leave" -> { groups.leave(characterKey); player.sendTextMessage("<color=#77FF99>You left your clan.</color>"); }
                case "kick", "promote", "demote" -> {
                    Player target = requireOnlineClanTarget(parts, 2);
                    Group group = groups.findByMember(characterKey)
                            .orElseThrow(() -> new IllegalStateException("You do not belong to a clan."));
                    if (action.equals("kick")) groups.kick(group.id(), characterKey, characterKey(target));
                    else groups.setManager(group.id(), characterKey, characterKey(target), action.equals("promote"));
                    player.sendTextMessage("<color=#77FF99>Clan membership updated for " + target.getName() + ".</color>");
                }
                case "balance" -> {
                    Group group = requireManagedClan(characterKey);
                    player.sendTextMessage("<color=#E8C547>" + group.name() + " treasury:</color> "
                            + formatBalance(groups.getBalance(group.id(), characterKey)));
                }
                case "deposit", "withdraw" -> {
                    if (parts.length != 3) throw new IllegalArgumentException("Usage: /clan " + action + " <amount>");
                    Group group = requireManagedClan(characterKey);
                    long amount = parseCurrencyAmount(parts[2]);
                    long balance = action.equals("deposit")
                            ? groups.deposit(group.id(), characterKey, amount)
                            : groups.withdraw(group.id(), characterKey, amount);
                    updateBalanceLabel(player);
                    player.sendTextMessage("<color=#77FF99>Clan treasury balance: " + formatBalance(balance) + ".</color>");
                }
                case "claim" -> claimCurrentChunkForClan(player, characterKey);
                case "unclaim" -> unclaimCurrentChunkForClan(player, characterKey);
                case "disband" -> {
                    Group group = groups.findByMember(characterKey)
                            .orElseThrow(() -> new IllegalStateException("You do not belong to a clan."));
                    Group removed = groups.disband(group.id(), characterKey);
                    int removedClaims = claims.deleteClaimsByOwner(removed.claimOwnerId());
                    player.sendTextMessage("<color=#77FF99>Disbanded " + removed.name() + " and released "
                            + removedClaims + " clan claim(s).</color>");
                }
                default -> {
                    player.sendTextMessage("<color=#FFAA66>Unknown clan command: " + parts[1] + ".</color>");
                    sendClanUsage(player);
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendTextMessage("<color=#FF7777>" + exception.getMessage() + "</color>");
        }
        player.sendTextMessage("<color=#AAAAAA>Use </color><color=#77AAFF>/commands</color>"
                + "<color=#AAAAAA> to browse every available command by category.</color>");
    }

    private void showClanInfo(Player player, String characterKey) {
        Group group = groups.findByMember(characterKey)
                .orElseThrow(() -> new IllegalStateException("You do not belong to a clan."));
        player.sendTextMessage("<color=#E8C547>--- " + group.name() + " ---</color>");
        for (GroupMember member : group.members().values().stream()
                .sorted((left, right) -> left.role().compareTo(right.role())).toList()) {
            player.sendTextMessage("- " + member.name() + " <color=#AAAAAA>[" + formatGroupRole(member.role()) + "]</color>");
        }
        player.sendTextMessage("<color=#AAAAAA>Clan claims: "
                + claims.getClaimsByOwner(group.claimOwnerId()).size() + "</color>");
    }

    private Group requireManagedClan(String characterKey) {
        Group group = groups.findByMember(characterKey)
                .orElseThrow(() -> new IllegalStateException("You do not belong to a clan."));
        if (!groups.canManage(group.id(), characterKey))
            throw new IllegalStateException("Only a clan owner or manager can manage clan funds.");
        return group;
    }

    private void claimCurrentChunkForClan(Player player, String characterKey) {
        Group group = groups.findByMember(characterKey)
                .orElseThrow(() -> new IllegalStateException("You do not belong to a clan."));
        if (!groups.canManage(group.id(), characterKey)) throw new IllegalStateException("Only a clan owner or manager can claim land.");
        Vector3i chunk = player.getChunkPosition();
        if (claims.getClaim(chunk.x, chunk.z).isPresent()) throw new IllegalStateException("This chunk is already claimed.");
        if (!economy.withdraw(characterKey, economySettings.claimCost()))
            throw new IllegalStateException("You need " + formatBalance(economySettings.claimCost()) + " to claim this chunk for the clan.");
        if (!claims.claim(chunk.x, chunk.z, group.claimOwnerId(), group.name())) {
            economy.deposit(characterKey, economySettings.claimCost());
            throw new IllegalStateException("This chunk was claimed before the transaction completed.");
        }
        updateBalanceLabel(player);
        player.sendTextMessage("<color=#77FF99>Claimed chunk " + chunk.x + ", " + chunk.z + " for " + group.name() + ".</color>");
    }

    private void unclaimCurrentChunkForClan(Player player, String characterKey) {
        Group group = groups.findByMember(characterKey)
                .orElseThrow(() -> new IllegalStateException("You do not belong to a clan."));
        if (!groups.canManage(group.id(), characterKey)) throw new IllegalStateException("Only a clan owner or manager can release clan land.");
        Vector3i chunk = player.getChunkPosition();
        if (!claims.unclaim(chunk.x, chunk.z, group.claimOwnerId())) throw new IllegalStateException("This chunk is not owned by your clan.");
        clearClaimVisuals(player);
        player.sendTextMessage("<color=#77FF99>Released clan chunk " + chunk.x + ", " + chunk.z + ".</color>");
    }

    private Player requireOnlineClanTarget(String[] parts, int startIndex) {
        if (parts.length <= startIndex) throw new IllegalArgumentException("Specify an online character name.");
        Player target = Server.getPlayerByName(joinArguments(parts, startIndex));
        if (target == null || !activeCharacters.containsKey(target.getUID()))
            throw new IllegalArgumentException("That character must be online and active.");
        return target;
    }

    private static String joinArguments(String[] parts, int startIndex) {
        return String.join(" ", java.util.Arrays.copyOfRange(parts, startIndex, parts.length));
    }

    private static String formatGroupRole(GroupRole role) {
        String name = role.name().toLowerCase(Locale.US);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void sendClanUsage(Player player) {
        player.sendTextMessage("<color=#E8C547>Clan commands:</color> create, info, invite, accept, leave, "
                + "kick, promote, demote, balance, deposit, withdraw, claim, unclaim, disband");
    }

    private ChestOwnership getOrAssignChest(long globalId, int chunkX, int chunkY, int chunkZ) {
        ChestOwnership existing = chests.get(globalId, chunkX, chunkY, chunkZ).orElse(null);
        if (existing != null) return existing;
        Claim claim = claims.getClaim(chunkX, chunkZ).orElse(null);
        return claim == null ? null : chests.assign(globalId, chunkX, chunkY, chunkZ,
                claim.ownerUid(), claim.ownerName());
    }

    private void removeChestOwnership(long globalId, int chunkX, int chunkY, int chunkZ) {
        chests.remove(globalId, chunkX, chunkY, chunkZ);
    }

    private static boolean isStorage(net.risingworld.api.definitions.Objects.ObjectDefinition definition) {
        return definition != null && definition.type == net.risingworld.api.definitions.Objects.Type.Storage;
    }

    private static boolean isWindowObject(net.risingworld.api.definitions.Objects.ObjectDefinition definition) {
        if (definition == null) return false;
        return definition.name != null
                && definition.name.toLowerCase(Locale.US).contains("window");
    }

    private boolean scheduleAutoTrim(Player player, Vector3f position, Quaternion rotation,
                                     Vector3f placedSize, boolean door, boolean useRaycast) {
        long now = System.currentTimeMillis();
        Long previous = autoTrimScheduledAt.put(player.getUID(), now);
        if (previous != null && now - previous < 3000L) return false;
        Vector3f openingSize = door
                ? new Vector3f(Math.max(1.2f, placedSize.x), Math.max(2.15f, placedSize.y), 0.35f)
                : new Vector3f(
                        placedSize.x > 0.05f ? placedSize.x : 2.0f,
                        placedSize.y > 0.05f ? placedSize.y : 2.0f,
                        placedSize.z > 0.02f ? placedSize.z : 0.2f);
        executeDelayed(0.2f, () -> {
            if (!player.isSpawned()) return;
            player.raycast(8f, -1, false, result -> {
                Vector3f resolvedPosition = position;
                if (useRaycast && result != null && result.hasCollision()) {
                    // Construction object positions are chunk-local in this API
                    // path. The physics collision point is a true world-space
                    // coordinate and reliably identifies the surrounding wall.
                    resolvedPosition = result.getCollisionPoint();
                }
                windowTrimService.trim(player, resolvedPosition, rotation, openingSize);
            });
        });
        return true;
    }

    private void listOwnedChunks(Player player) {
        String characterKey = characterKey(player);
        List<ClaimedChunk> ownedChunks = claims.getClaimsByOwner(characterKey);
        if ("claims".equals(visualModes.get(player.getUID()))) {
            clearClaimVisuals(player);
            player.sendTextMessage("<color=#AAAAAA>Claim overview hidden.</color>");
            return;
        }
        if (ownedChunks.isEmpty()) {
            player.sendTextMessage("<color=#AAAAAA>You do not personally own any chunks.</color>");
        } else {
            player.sendTextMessage("<color=#E8C547>Your claimed chunks (" + ownedChunks.size() + "):</color>");
            StringBuilder line = new StringBuilder();
            for (ClaimedChunk chunk : ownedChunks) {
                String coordinate = "[" + chunk.x() + ", " + chunk.z() + "]";
                if (!line.isEmpty() && line.length() + coordinate.length() + 2 > 90) {
                    player.sendTextMessage(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) line.append(", ");
                line.append(coordinate);
            }
            if (!line.isEmpty()) player.sendTextMessage(line.toString());
        }

        clearClaimVisuals(player);
        Vector3i center = player.getChunkPosition();
        int minimumX = center.x - CLAIM_OVERVIEW_RADIUS;
        int maximumX = center.x + CLAIM_OVERVIEW_RADIUS;
        int minimumZ = center.z - CLAIM_OVERVIEW_RADIUS;
        int maximumZ = center.z + CLAIM_OVERVIEW_RADIUS;
        Map<ClaimedChunk, Claim> nearbyClaims = claims.getClaimsInArea(
                minimumX, maximumX, minimumZ, maximumZ);
        String clanOwnerId = groups.findByMember(characterKey).map(Group::claimOwnerId).orElse(null);
        float groundY = player.getPosition().y - 0.15f;
        int sideLength = CLAIM_OVERVIEW_RADIUS * 2 + 1;
        List<Area3D> visuals = new ArrayList<>(sideLength * sideLength);
        for (int chunkX = minimumX; chunkX <= maximumX; chunkX++) {
            for (int chunkZ = minimumZ; chunkZ <= maximumZ; chunkZ++) {
                Claim claim = nearbyClaims.get(new ClaimedChunk(chunkX, chunkZ));
                Area3D visual;
                if (claim == null) {
                    visual = createChunkVisual(chunkX, chunkZ, groundY,
                            0.15f, 0.85f, 0.30f, 0.12f,
                            0.25f, 1.0f, 0.40f, 0.95f);
                } else if (claim.ownerUid().equals(characterKey)) {
                    visual = createChunkVisual(chunkX, chunkZ, groundY,
                            0.15f, 0.45f, 1.0f, 0.12f,
                            0.30f, 0.65f, 1.0f, 0.95f);
                } else if (claim.ownerUid().equals(clanOwnerId)) {
                    visual = createChunkVisual(chunkX, chunkZ, groundY,
                            0.62f, 0.25f, 0.90f, 0.12f,
                            0.78f, 0.45f, 1.0f, 0.95f);
                } else {
                    visual = createChunkVisual(chunkX, chunkZ, groundY,
                            1.0f, 0.15f, 0.15f, 0.12f,
                            1.0f, 0.30f, 0.30f, 0.95f);
                }
                visuals.add(visual);
                player.addGameObject(visual);
            }
        }
        claimVisuals.put(player.getUID(), visuals);
        visualModes.put(player.getUID(), "claims");
        visualHeights.put(player.getUID(), groundY);
        player.sendTextMessage("<color=#77AAFF>Blue: yours</color> | <color=#77FF77>Green: available</color>"
                + " | <color=#C774FF>Purple: your clan</color> | <color=#FF5555>Red: unavailable</color>");
        player.sendTextMessage("<color=#AAAAAA>Showing a " + sideLength + "x" + sideLength
                + " chunk area around you. Use /claims again to hide it.</color>");
    }

    private void claimCurrentChunk(Player player) {
        Vector3i chunk = player.getChunkPosition();
        Claim existing = claims.getClaim(chunk.x, chunk.z).orElse(null);
        if (existing != null) {
            String owner = canAccessOwner(characterKey(player), existing.ownerUid()) ? "you or your clan" : existing.ownerName();
            player.sendTextMessage("<color=#FF7777>Chunk " + chunk.x + ", " + chunk.z
                    + " is already claimed by " + owner + ".</color>");
            showCurrentChunk(player, false);
            return;
        }

        long claimCost = economySettings.claimCost();
        String characterKey = characterKey(player);
        long balance = economy.getBalance(characterKey);
        if (balance < claimCost) {
            player.sendTextMessage("<color=#FF7777>Claiming this chunk costs " + formatBalance(claimCost)
                    + ". You only have " + formatBalance(balance) + ".</color>");
            return;
        }

        if (!claims.claim(chunk.x, chunk.z, characterKey, player.getName())) {
            player.sendTextMessage("<color=#FF7777>That chunk was claimed before your request completed.</color>");
            return;
        }
        try {
            if (!economy.withdraw(characterKey, claimCost)) {
                claims.forceUnclaim(chunk.x, chunk.z);
                player.sendTextMessage("<color=#FF7777>Your balance changed before payment completed.</color>");
                return;
            }
        } catch (RuntimeException exception) {
            claims.forceUnclaim(chunk.x, chunk.z);
            throw exception;
        }
        updateBalanceLabel(player);
        player.sendTextMessage("<color=#77FF99>Claimed chunk " + chunk.x + ", " + chunk.z
                + " for " + formatBalance(claimCost) + ".</color>");
        showCurrentChunk(player, false);
    }

    private void unclaimCurrentChunk(Player player) {
        Vector3i chunk = player.getChunkPosition();
        Claim existing = claims.getClaim(chunk.x, chunk.z).orElse(null);
        if (existing == null) {
            player.sendTextMessage("<color=#AAAAAA>This chunk is not claimed.</color>");
        } else if (!existing.ownerUid().equals(characterKey(player)) && !isClaimAdmin(player)) {
            player.sendTextMessage("<color=#FF7777>This chunk is claimed by " + existing.ownerName() + ".</color>");
        } else {
            if (existing.ownerUid().equals(characterKey(player))) {
                claims.unclaim(chunk.x, chunk.z, characterKey(player));
            } else {
                claims.forceUnclaim(chunk.x, chunk.z);
            }
            player.sendTextMessage("<color=#77FF99>Unclaimed chunk " + chunk.x + ", " + chunk.z + ".</color>");
        }
        showCurrentChunk(player, false);
    }

    private void showCurrentChunk(Player player, boolean toggle) {
        Vector3i chunk = player.getChunkPosition();
        String chunkKey = chunk.x + "," + chunk.z;
        String requestedMode = "chunk:" + chunkKey;
        String oldMode = visualModes.get(player.getUID());
        clearClaimVisuals(player);
        if (toggle && requestedMode.equals(oldMode)) {
            player.sendTextMessage("<color=#AAAAAA>Chunk highlight hidden.</color>");
            return;
        }

        Claim claim = claims.getClaim(chunk.x, chunk.z).orElse(null);
        String ownerText = claim == null ? "Unclaimed" : "Claimed by " + claim.ownerName();
        player.sendTextMessage("<color=#E8C547>Chunk:</color> " + chunk.x + ", " + chunk.z
                + " - " + ownerText);

        float groundY = player.getPosition().y - 0.15f;
        Area3D visual;
        if (claim == null) {
            visual = createChunkVisual(chunk.x, chunk.z, groundY,
                    0.15f, 0.85f, 0.30f, 0.12f,
                    0.25f, 1.0f, 0.40f, 0.95f);
        } else if (canAccessOwner(characterKey(player), claim.ownerUid())) {
            visual = createChunkVisual(chunk.x, chunk.z, groundY,
                    0.15f, 0.45f, 1.0f, 0.12f,
                    0.30f, 0.65f, 1.0f, 0.95f);
        } else {
            visual = createChunkVisual(chunk.x, chunk.z, groundY,
                    1.0f, 0.15f, 0.15f, 0.12f,
                    1.0f, 0.30f, 0.30f, 0.95f);
        }

        claimVisuals.put(player.getUID(), List.of(visual));
        visualModes.put(player.getUID(), requestedMode);
        visualHeights.put(player.getUID(), groundY);
        player.addGameObject(visual);
    }

    private Area3D createChunkVisual(int chunkX, int chunkZ, float groundY,
                                     float red, float green, float blue, float alpha,
                                     float frameRed, float frameGreen, float frameBlue, float frameAlpha) {
        Vector3f start = Utils.ChunkUtils.getGlobalPosition(new Vector3i(chunkX, 0, chunkZ), Vector3i.ZERO);
        Vector3f end = Utils.ChunkUtils.getGlobalPosition(new Vector3i(chunkX + 1, 0, chunkZ + 1), Vector3i.ZERO);
        Area area = new Area(start.x, groundY, start.z, end.x, groundY + 0.3f, end.z);
        Area3D visual = new Area3D(area);
        // Keep the overlay in world space. A null attachment explicitly prevents
        // the local transform from inheriting movement from the viewing player.
        visual.attachTo((Player) null, GameObject.AttachTarget.Root);
        visual.setAlwaysVisible(false);
        visual.setFrameVisible(true);
        visual.setColor(red, green, blue, alpha);
        visual.setFrameColor(frameRed, frameGreen, frameBlue, frameAlpha);
        return visual;
    }

    private void clearClaimVisuals(Player player) {
        List<Area3D> visuals = claimVisuals.remove(player.getUID());
        visualModes.remove(player.getUID());
        visualHeights.remove(player.getUID());
        if (visuals != null) {
            for (Area3D visual : visuals) {
                player.removeGameObject(visual);
            }
        }
    }

    private void handleClaimAdminCommand(Player sender, String[] parts) {
        if (!sender.isAdmin()) {
            sender.sendTextMessage("<color=#FF7777>Only a server administrator can manage claim admins.</color>");
            return;
        }
        if (parts.length == 2 && parts[1].equalsIgnoreCase("list")) {
            Map<String, String> allAdmins = claimAdmins.getAll();
            if (allAdmins.isEmpty()) {
                sender.sendTextMessage("<color=#AAAAAA>No whitelisted claim administrators.</color>");
                return;
            }
            sender.sendTextMessage("<color=#E8C547>Claim administrators:</color>");
            allAdmins.forEach((uid, name) -> sender.sendTextMessage("- " + name + " (" + uid + ")"));
            return;
        }
        if (parts.length == 3 && (parts[1].equalsIgnoreCase("add") || parts[1].equalsIgnoreCase("remove"))) {
            Player target = Server.getPlayerByName(parts[2]);
            if (target == null) {
                sender.sendTextMessage("<color=#FF7777>Player must be online: " + parts[2] + "</color>");
                return;
            }
            if (parts[1].equalsIgnoreCase("add")) {
                claimAdmins.add(target.getUID(), target.getName());
                sender.sendTextMessage("<color=#77FF99>Added " + target.getName() + " as a claim administrator.</color>");
                target.sendTextMessage("<color=#E8C547>You are now a claim administrator.</color>");
            } else if (claimAdmins.remove(target.getUID())) {
                sender.sendTextMessage("<color=#77FF99>Removed " + target.getName() + " from claim administrators.</color>");
            } else {
                sender.sendTextMessage("<color=#AAAAAA>" + target.getName() + " is not a claim administrator.</color>");
            }
            return;
        }
        sender.sendTextMessage("Usage: /claimadmin add <online-player>, /claimadmin remove <online-player>, or /claimadmin list");
    }

    private boolean isClaimAdmin(Player player) {
        return player.isAdmin() || claimAdmins.contains(player.getUID());
    }

    private void openCharacterSwitcher(Player player) {
        CharacterService.CharacterSummary active = activeCharacters.get(player.getUID());
        if (active == null) {
            showCharacterSelection(player);
            return;
        }

        // Persist the current character before another slot can replace the
        // player's inventory, appearance, status, and location.
        characterService.saveCharacter(player, active);
        if (storeViews.containsKey(player.getUID())) closeStore(player);
        if (adminViews.containsKey(player.getUID())) closeAdminDashboard(player);
        clearClaimVisuals(player);
        showCharacterSelection(player);
        player.sendTextMessage("<color=#E8C547>Choose a character to switch without leaving the server.</color>");
        debug("Saved " + active.name() + " and opened the character switcher for " + player.getUID());
    }

    private void showCharacterSelection(Player player) {
        CharacterSelectionView oldView = characterSelectionViews.remove(player.getUID());
        if (oldView != null) player.removeUIElement(oldView.window());

        UIElement window = new UIElement();
        window.setPosition(50f, 50f, true);
        window.setPivot(Pivot.MiddleCenter);
        window.setSize(560f, 420f, false);
        window.setBackgroundColor((int) 0x161B22F8L);
        window.setBorder(2f);
        window.setBorderColor((int) 0xE8C547FFL);
        window.setBorderEdgeRadius(8f, false);

        UILabel title = new UILabel("Choose Your Character");
        title.setPosition(20f, 14f, false);
        title.setSize(520f, 48f, false);
        title.setFontSize(28f);
        title.setFontColor((int) 0xF4E3A1FFL);
        title.setTextAlign(TextAnchor.MiddleCenter);
        window.addChild(title);

        UILabel closeButton = new UILabel("X");
        closeButton.setPosition(512f, 14f, false);
        closeButton.setSize(28f, 28f, false);
        closeButton.setFontSize(18f);
        closeButton.setFontColor((int) 0xFFFFFFFFL);
        closeButton.setTextAlign(TextAnchor.MiddleCenter);
        closeButton.setBackgroundColor((int) 0x8B2E35FFL);
        closeButton.setClickable(true);
        window.addChild(closeButton);

        Map<Integer, CharacterService.CharacterSummary> charactersByButtonId = new ConcurrentHashMap<>();
        Map<Integer, CharacterService.CharacterSummary> deleteByButtonId = new ConcurrentHashMap<>();
        Map<Integer, Integer> createSlotsByButtonId = new ConcurrentHashMap<>();
        Map<Integer, CharacterService.CharacterSummary> bySlot = new ConcurrentHashMap<>();
        characterService.getCharacters(player.getUID()).forEach(character -> bySlot.put(character.slot(), character));
        for (int slot = 1; slot <= CharacterService.MAX_SLOTS; slot++) {
            CharacterService.CharacterSummary character = bySlot.get(slot);
            UILabel slotButton = new UILabel(character == null
                    ? "Slot " + slot + "\nCreate New Character"
                    : "Slot " + slot + "\n" + character.name());
            slotButton.setPosition(40f, 78f + (slot - 1) * 78f, false);
            slotButton.setSize(character == null ? 480f : 374f, 64f, false);
            slotButton.setFontSize(19f);
            slotButton.setFontColor((int) 0xFFFFFFFFL);
            slotButton.setTextAlign(TextAnchor.MiddleCenter);
            slotButton.setBackgroundColor(character == null ? (int) 0x28313DFFL : (int) 0x345D82FFL);
            slotButton.setBorder(1f);
            slotButton.setBorderColor(character == null ? (int) 0x566273FFL : (int) 0x77AAFFFFL);
            slotButton.setBorderEdgeRadius(5f, false);
            slotButton.setClickable(true);
            window.addChild(slotButton);
            if (character == null) createSlotsByButtonId.put(slotButton.getID(), slot);
            else {
                charactersByButtonId.put(slotButton.getID(), character);
                UILabel deleteButton = new UILabel("Delete");
                deleteButton.setPosition(426f, 78f + (slot - 1) * 78f, false);
                deleteButton.setSize(94f, 64f, false);
                deleteButton.setFontSize(17f);
                deleteButton.setFontColor((int) 0xFFFFFFFFL);
                deleteButton.setTextAlign(TextAnchor.MiddleCenter);
                deleteButton.setBackgroundColor((int) 0x8B2E35FFL);
                deleteButton.setBorder(1f);
                deleteButton.setBorderColor((int) 0xE06C75FFL);
                deleteButton.setBorderEdgeRadius(5f, false);
                deleteButton.setClickable(true);
                window.addChild(deleteButton);
                deleteByButtonId.put(deleteButton.getID(), character);
            }
        }

        CharacterSelectionView view = new CharacterSelectionView(window, charactersByButtonId,
                createSlotsByButtonId, deleteByButtonId, closeButton);
        characterSelectionViews.put(player.getUID(), view);
        player.addUIElement(window);
        player.stopInput(true, true);
        player.setMouseCursorVisible(true);
    }

    private void promptCreateCharacter(Player player, int slot) {
        player.showInputMessageBox("Create Character", "Enter a character name (3-24 characters):", "",
                name -> {
                    if (name == null) return;
                    try {
                        CharacterService.CharacterSummary character = characterService.createCharacter(player, name, slot);
                        activateCharacter(player, character);
                        player.sendTextMessage("<color=#E8C547>Appearance synced from your Rising World profile.</color>");
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        player.showErrorMessageBox("Character creation failed", exception.getMessage());
                    }
                });
    }

    private void syncProfileAppearance(Player player) {
        if (!characterService.applyProfileAppearance(player)) {
            player.sendTextMessage("<color=#FFAA66>No native profile appearance has been captured. Rejoin the server and try again.</color>");
            return;
        }
        CharacterService.CharacterSummary active = activeCharacters.get(player.getUID());
        if (active != null) characterService.saveCharacter(player, active);
        player.sendTextMessage("<color=#77FF99>Active character appearance synced from your Rising World profile.</color>");
        debug("Synced native profile appearance to " + player.getUID());
    }

    private void showAppearanceEditor(Player player) {
        AppearanceView oldView = appearanceViews.remove(player.getUID());
        if (oldView != null) player.removeUIElement(oldView.window());

        UIElement window = new UIElement();
        // Keep the controls on the right so Rising World's native inventory
        // character render remains visible on the left.
        window.setPosition(99f, 50f, true);
        window.setPivot(Pivot.MiddleRight);
        window.setSize(620f, 430f, false);
        window.setBackgroundColor((int) 0x161B22F8L);
        window.setBorder(2f);
        window.setBorderColor((int) 0xE8C547FFL);
        window.setBorderEdgeRadius(8f, false);

        UILabel title = new UILabel("Customize " + player.getName());
        title.setPosition(20f, 14f, false);
        title.setSize(580f, 44f, false);
        title.setFontSize(27f);
        title.setFontColor((int) 0xF4E3A1FFL);
        title.setTextAlign(TextAnchor.MiddleCenter);
        window.addChild(title);

        UILabel close = appearanceButton("X", 572f, 14f, 28f);
        close.setSize(28f, 28f, false);
        close.setFontSize(16f);
        close.setBackgroundColor((int) 0x8B2E35FFL);
        window.addChild(close);

        UILabel gender = appearanceButton("Gender", 40f, 78f, 250f);
        UILabel skinColor = appearanceButton("Skin Color", 330f, 78f, 250f);
        UILabel hairMinus = appearanceButton("Hair -", 40f, 150f, 115f);
        UILabel hairValue = appearanceValue("Hair: 0", 165f, 150f);
        UILabel hairPlus = appearanceButton("Hair +", 465f, 150f, 115f);
        UILabel beardMinus = appearanceButton("Beard -", 40f, 222f, 115f);
        UILabel beardValue = appearanceValue("Beard: 0", 165f, 222f);
        UILabel beardPlus = appearanceButton("Beard +", 465f, 222f, 115f);
        UILabel faceUnavailable = appearanceValue("Face markings are not available through the server API",
                40f, 294f);
        faceUnavailable.setSize(540f, 52f, false);
        faceUnavailable.setFontSize(16f);
        faceUnavailable.setFontColor((int) 0xAAAAAAFFL);
        UILabel hairColor = appearanceButton("Hair Color", 40f, 366f, 160f);
        UILabel eyeColor = appearanceButton("Eye Color", 220f, 366f, 160f);
        UILabel finish = appearanceButton("Finish", 400f, 366f, 180f);
        for (UILabel element : List.of(gender, skinColor, hairMinus, hairValue, hairPlus,
                beardMinus, beardValue, beardPlus, faceUnavailable,
                hairColor, eyeColor, finish)) window.addChild(element);

        AppearanceView view = new AppearanceView(window, gender, skinColor, hairMinus, hairValue,
                hairPlus, beardMinus, beardValue, beardPlus, hairColor, eyeColor, finish, close);
        appearanceViews.put(player.getUID(), view);
        refreshAppearanceLabels(player, view);
        player.showInventory();
        player.addUIElement(window, UITarget.Inventory);
        player.stopInput(true, true);
        player.setMouseCursorVisible(true);
    }

    private static UILabel appearanceButton(String text, float x, float y, float width) {
        UILabel button = new UILabel(text);
        button.setPosition(x, y, false);
        button.setSize(width, 52f, false);
        button.setFontSize(18f);
        button.setFontColor((int) 0xFFFFFFFFL);
        button.setTextAlign(TextAnchor.MiddleCenter);
        button.setBackgroundColor((int) 0x345D82FFL);
        button.setBorder(1f);
        button.setBorderColor((int) 0x77AAFFFFL);
        button.setBorderEdgeRadius(5f, false);
        button.setClickable(true);
        return button;
    }

    private static UILabel appearanceValue(String text, float x, float y) {
        UILabel label = new UILabel(text);
        label.setPosition(x, y, false);
        label.setSize(290f, 52f, false);
        label.setFontSize(19f);
        label.setFontColor((int) 0xF4E3A1FFL);
        label.setTextAlign(TextAnchor.MiddleCenter);
        return label;
    }

    private static void refreshAppearanceLabels(Player player, AppearanceView view) {
        Skin skin = player.getSkin();
        view.gender().setText("Gender: " + skin.getGender().name());
        view.hairValue().setText("Hairstyle: " + Byte.toUnsignedInt(skin.getHairstyle()));
        view.beardValue().setText("Beard: " + Byte.toUnsignedInt(skin.getBeard()));
        view.skinColor().setText("Skin Color\n" + colorHex(skin.getSkinColor()));
        view.hairColor().setText("Hair Color\n" + colorHex(skin.getHairColor()));
        view.eyeColor().setText("Eye Color\n" + colorHex(skin.getEyeColor()));
    }

    private void handleAppearanceClick(Player player, AppearanceView view, int elementId) {
        Skin skin = player.getSkin();
        if (elementId == view.gender().getID()) {
            skin.setGender(skin.getGender() == Skin.Gender.Male ? Skin.Gender.Female : Skin.Gender.Male);
            skin.setHairstyle((byte) 0);
            skin.setBeard((byte) 0);
            debug("Appearance gender changed for " + player.getUID());
        } else if (elementId == view.hairMinus().getID()) {
            skin.setHairstyle(cycleDefinition(skin.getHairstyle(), -1,
                    hairstylesFor(skin.getGender())));
        } else if (elementId == view.hairPlus().getID()) {
            skin.setHairstyle(cycleDefinition(skin.getHairstyle(), 1,
                    hairstylesFor(skin.getGender())));
        } else if (elementId == view.beardMinus().getID()) {
            skin.setBeard(cycleDefinition(skin.getBeard(), -1, BEARDS));
        } else if (elementId == view.beardPlus().getID()) {
            skin.setBeard(cycleDefinition(skin.getBeard(), 1, BEARDS));
        } else if (elementId == view.skinColor().getID()) {
            skin.setSkinColor(nextColor(skin.getSkinColor(), SKIN_COLORS));
        } else if (elementId == view.hairColor().getID()) {
            skin.setHairColor(nextColor(skin.getHairColor(), HAIR_COLORS));
        } else if (elementId == view.eyeColor().getID()) {
            skin.setEyeColor(nextColor(skin.getEyeColor(), EYE_COLORS));
        } else if (elementId == view.finish().getID() || elementId == view.close().getID()) {
            CharacterService.CharacterSummary active = activeCharacters.get(player.getUID());
            if (active != null) characterService.saveCharacter(player, active);
            appearanceViews.remove(player.getUID());
            player.removeUIElement(view.window());
            player.hideInventory();
            player.stopInput(false, false);
            player.setMouseCursorVisible(false);
            player.sendTextMessage("<color=#77FF99>Character appearance saved.</color>");
            return;
        } else {
            return;
        }
        refreshAppearanceLabels(player, view);
        debug("Appearance values for " + player.getUID() + ": hair="
                + Byte.toUnsignedInt(skin.getHairstyle()) + ", beard="
                + Byte.toUnsignedInt(skin.getBeard()));
        refreshNativeAppearancePreview(player);
    }

    private void refreshNativeAppearancePreview(Player player) {
        // The inventory preview does not redraw for every Skin packet. Reopen it
        // on the next plugin tick to force Rising World to render the new model.
        player.hideInventory();
        executeDelayed(0.1f, () -> {
            if (appearanceViews.containsKey(player.getUID()) && player.isSpawned()) {
                player.showInventory();
                player.setMouseCursorVisible(true);
            }
        });
    }

    private static int[] hairstylesFor(Skin.Gender gender) {
        return gender == Skin.Gender.Female ? FEMALE_HAIRSTYLES : MALE_HAIRSTYLES;
    }

    private static byte cycleDefinition(byte current, int delta, int[] allowedIds) {
        int currentId = Byte.toUnsignedInt(current);
        for (int index = 0; index < allowedIds.length; index++) {
            if (allowedIds[index] == currentId) {
                return (byte) allowedIds[Math.floorMod(index + delta, allowedIds.length)];
            }
        }
        return (byte) allowedIds[delta < 0 ? allowedIds.length - 1 : 0];
    }

    private static int nextColor(int current, int[] palette) {
        int rgb = current & 0xFFFFFF;
        for (int index = 0; index < palette.length; index++) {
            if (palette[index] == rgb) return palette[(index + 1) % palette.length];
        }
        return palette[0];
    }

    private static String colorHex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    private void confirmDeleteCharacter(Player player, CharacterService.CharacterSummary character) {
        if (userStore.hasListings(character.economyKey())) {
            player.sendTextMessage("<color=#FF7777>Cancel this character's user-store listings before deleting them.</color>");
            return;
        }
        Group membership = groups.findByMember(character.economyKey()).orElse(null);
        if (membership != null && membership.members().get(character.economyKey()).role() == GroupRole.OWNER) {
            player.sendTextMessage("<color=#FF7777>Disband the clan before deleting its owner character.</color>");
            return;
        }
        player.showMessageBox(MessageBoxButtons.Yes_No, "Delete character",
                "Permanently delete " + character.name()
                        + "? Their inventory, balance, and claims will also be deleted.",
                -1, selectedButton -> {
                    if (selectedButton == null || selectedButton != 0) return;
                    try {
                        CharacterService.CharacterSummary active = activeCharacters.get(player.getUID());
                        characterService.deleteCharacter(player.getUID(), character);
                        journals.deleteJournal(character.economyKey());
                        groups.removeDeletedCharacter(character.economyKey());
                        int removedClaims = claims.deleteClaimsByOwner(character.economyKey());
                        economy.deleteAccount(character.economyKey());
                        if (active != null && active.id().equals(character.id())) {
                            activeCharacters.remove(player.getUID());
                            activeClaimIdentities.remove(player.getUID());
                            UILabel balance = balanceLabels.remove(player.getUID());
                            if (balance != null) player.removeUIElement(balance);
                        }
                        showCharacterSelection(player);
                        player.sendTextMessage("<color=#77FF99>Deleted " + character.name() + " and "
                                + removedClaims + " owned claim(s).</color>");
                        debug("Deleted character " + character.name() + " [slot " + character.slot()
                                + "] for account " + player.getUID());
                    } catch (IllegalStateException exception) {
                        player.showErrorMessageBox("Character deletion failed", exception.getMessage());
                        showCharacterSelection(player);
                    }
                });
    }

    private void activateCharacter(Player player, CharacterService.CharacterSummary character) {
        String playerUid = player.getUID();
        CharacterService.CharacterSummary previous = activeCharacters.put(playerUid, character);
        String previousClaimIdentity = activeClaimIdentities.put(playerUid, character.economyKey());
        clearClaimVisuals(player);
        claimProtectionNotices.remove(playerUid);
        deniedGrassRewardsUntil.remove(playerUid);
        lastEquippedItemTypes.remove(playerUid);
        lastEquippedItemVariants.remove(playerUid);
        lastEquippedConstructionNames.remove(playerUid);
        lastEquippedConstructionIds.remove(playerUid);
        lastEquippedConstructionSizes.remove(playerUid);
        try {
            characterService.loadCharacter(player, character);
        } catch (RuntimeException exception) {
            if (previous == null) activeCharacters.remove(playerUid, character);
            else activeCharacters.put(playerUid, previous);
            if (previousClaimIdentity == null) activeClaimIdentities.remove(playerUid, character.economyKey());
            else activeClaimIdentities.put(playerUid, previousClaimIdentity);
            throw exception;
        }
        economy.createAccount(character.economyKey(), economySettings.defaultBalance());
        CharacterSelectionView view = characterSelectionViews.remove(playerUid);
        if (view != null) player.removeUIElement(view.window());
        player.stopInput(false, false);
        player.setMouseCursorVisible(false);
        showBalance(player);
        showWorldClock(player);
        player.sendTextMessage("<color=#77FF99>Now playing as " + character.name() + ".</color>");
        debug("Profile " + character.profileName() + " (" + player.getUID() + ") selected character "
                + character.name() + " [slot " + character.slot() + ", claim identity "
                + character.economyKey() + "]");
    }

    private String characterKey(Player player) {
        CharacterService.CharacterSummary character = activeCharacters.get(player.getUID());
        if (character == null) throw new IllegalStateException("No active character for " + player.getUID());
        return character.economyKey();
    }

    private String activeClaimIdentity(Player player) {
        CharacterService.CharacterSummary character = activeCharacters.get(player.getUID());
        if (character == null) return null;
        String identity = character.economyKey();
        activeClaimIdentities.put(player.getUID(), identity);
        return identity;
    }

    private boolean canAccessOwner(String characterIdentity, String ownerIdentity) {
        return characterIdentity != null && (ownerIdentity.equals(characterIdentity)
                || groups.canAccess(characterIdentity, ownerIdentity));
    }

    private void saveActiveCharacters() {
        for (Player player : Server.getAllPlayers()) {
            CharacterService.CharacterSummary active = activeCharacters.get(player.getUID());
            if (active != null) characterService.saveCharacter(player, active);
        }
    }

    private void toggleAdminDashboard(Player player) {
        if (!player.isAdmin()) {
            player.sendTextMessage("<color=#FF7777>Only a server administrator can open the admin dashboard.</color>");
            return;
        }
        if (adminViews.containsKey(player.getUID())) {
            closeAdminDashboard(player);
        } else {
            if (storeViews.containsKey(player.getUID())) {
                closeStore(player);
            }
            openAdminDashboard(player);
        }
    }

    private void openAdminDashboard(Player player) {
        UIElement window = new UIElement();
        window.setPosition(50f, 50f, true);
        window.setPivot(Pivot.MiddleCenter);
        window.setSize(720f, 560f, false);
        window.setBackgroundColor((int) 0x161B22F2L);
        window.setBorder(2f);
        window.setBorderColor((int) 0xD45B5BFFL);
        window.setBorderEdgeRadius(8f, false);

        UILabel title = new UILabel("Administrator Dashboard");
        title.setPosition(20f, 12f, false);
        title.setSize(560f, 42f, false);
        title.setFontSize(27f);
        title.setFontColor((int) 0xFFD0D0FFL);
        title.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(title);

        UILabel refreshButton = new UILabel("REFRESH");
        refreshButton.setPosition(576f, 14f, false);
        refreshButton.setSize(90f, 36f, false);
        refreshButton.setFontSize(14f);
        refreshButton.setTextAlign(TextAnchor.MiddleCenter);
        refreshButton.setBackgroundColor((int) 0x345D82FFL);
        refreshButton.setClickable(true);
        window.addChild(refreshButton);

        UILabel closeButton = new UILabel("X");
        closeButton.setPosition(670f, 14f, false);
        closeButton.setSize(32f, 36f, false);
        closeButton.setFontSize(20f);
        closeButton.setTextAlign(TextAnchor.MiddleCenter);
        closeButton.setBackgroundColor((int) 0x8B2D2DFFL);
        closeButton.setClickable(true);
        window.addChild(closeButton);

        UILabel summary = new UILabel();
        summary.setPosition(20f, 65f, false);
        summary.setSize(680f, 105f, false);
        summary.setFontSize(17f);
        summary.setFontColor((int) 0xFFFFFFFFL);
        summary.setTextAlign(TextAnchor.UpperLeft);
        summary.setBackgroundColor((int) 0x202832FFL);
        summary.setBorderEdgeRadius(5f, false);
        window.addChild(summary);

        UILabel adminOverrideButton = new UILabel();
        adminOverrideButton.setPosition(20f, 178f, false);
        adminOverrideButton.setSize(220f, 34f, false);
        adminOverrideButton.setFontSize(14f);
        adminOverrideButton.setTextAlign(TextAnchor.MiddleCenter);
        adminOverrideButton.setClickable(true);
        window.addChild(adminOverrideButton);

        UILabel playersTitle = new UILabel("Connected Players");
        playersTitle.setPosition(20f, 220f, false);
        playersTitle.setSize(680f, 34f, false);
        playersTitle.setFontSize(20f);
        playersTitle.setFontColor((int) 0xF4E3A1FFL);
        playersTitle.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(playersTitle);

        UIScrollView playerList = new UIScrollView(UIScrollView.ScrollViewMode.Vertical);
        playerList.setPosition(20f, 258f, false);
        playerList.setSize(680f, 282f, false);
        playerList.setVerticalScrollerVisibility(UIScrollView.ScrollerVisibility.Auto);
        playerList.setHorizontalScrollerVisibility(UIScrollView.ScrollerVisibility.Hidden);
        playerList.setMouseWheelScrollSize(42f);
        window.addChild(playerList);

        AdminView view = new AdminView(window, closeButton, refreshButton, summary,
                adminOverrideButton, playerList,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        adminViews.put(player.getUID(), view);
        refreshAdminDashboard(view);
        player.addUIElement(window);
        player.setMouseCursorVisible(true);
    }

    private void refreshAdminDashboard(AdminView view) {
        net.risingworld.api.objects.Time time = Server.getGameTime();
        view.summary().setText(String.format(Locale.US,
                "World: %s     Time: Y%d M%d D%d %02d:%02d\n"
                        + "Players: %d / %d     Claims: %d     Claim admins: %d\n"
                        + "Starting cash: %s     Claim cost: %s     8-hour salary: %s\n"
                        + "Marketplace products enabled: %d",
                World.getName(), time.getYear(), time.getMonth(), time.getDay(),
                time.getHours(), time.getMinutes(), Server.getPlayerCount(), Server.getMaxPlayerCount(),
                claims.getClaimCount(), claimAdmins.getAll().size(),
                formatBalance(economySettings.defaultBalance()), formatBalance(economySettings.claimCost()),
                formatBalance(economySettings.baseSalary()), storeCatalog.items().size()));

        styleProtectionToggle(view.adminOverrideButton(), "ADMIN BYPASS", claimAdminOverrideEnabled);

        view.playerList().removeAllChilds();
        view.kickTargetsByButtonId().clear();
        view.banTargetsByButtonId().clear();
        Player[] players = Server.getAllPlayers();
        for (int index = 0; index < players.length; index++) {
            Player connectedPlayer = players[index];
            CharacterService.CharacterSummary activeCharacter = activeCharacters.get(connectedPlayer.getUID());
            String profileName = activeCharacter == null ? connectedPlayer.getName() : activeCharacter.profileName();
            String characterName = activeCharacter == null ? "Selecting character" : activeCharacter.name();
            String role = connectedPlayer.isAdmin() ? "ADMIN" : "PLAYER";
            UILabel row = new UILabel(profileName + "  [" + role + "]  |  Character: " + characterName + "\n"
                    + "UID: " + connectedPlayer.getUID() + "     Balance: "
                    + (activeCharacter != null
                    ? formatBalance(economy.getBalance(characterKey(connectedPlayer))) : "Selecting character"));
            row.setPosition(0f, index * 58f, false);
            row.setSize(650f, 52f, false);
            row.setFontSize(16f);
            row.setFontColor((int) 0xFFFFFFFFL);
            row.setTextAlign(TextAnchor.MiddleLeft);
            row.setBackgroundColor(index % 2 == 0 ? (int) 0x28313DFFL : (int) 0x202832FFL);
            row.setBorderEdgeRadius(4f, false);
            view.playerList().addChild(row);

            UILabel kickButton = new UILabel("KICK");
            kickButton.setPosition(470f, 8f, false);
            kickButton.setSize(78f, 36f, false);
            kickButton.setFontSize(14f);
            kickButton.setTextAlign(TextAnchor.MiddleCenter);
            kickButton.setBackgroundColor((int) 0x9A6A24FFL);
            kickButton.setClickable(true);
            row.addChild(kickButton);
            view.kickTargetsByButtonId().put(kickButton.getID(), connectedPlayer.getUID());

            UILabel banButton = new UILabel("BAN");
            banButton.setPosition(558f, 8f, false);
            banButton.setSize(78f, 36f, false);
            banButton.setFontSize(14f);
            banButton.setTextAlign(TextAnchor.MiddleCenter);
            banButton.setBackgroundColor((int) 0x8B2D2DFFL);
            banButton.setClickable(true);
            row.addChild(banButton);
            view.banTargetsByButtonId().put(banButton.getID(), connectedPlayer.getUID());
        }
    }

    private static void styleProtectionToggle(UILabel button, String label, boolean enabled) {
        button.setText(label + ": " + (enabled ? "ON" : "OFF"));
        button.setBackgroundColor(enabled ? (int) 0x2E7D4FFF : (int) 0x8B2D2DFFL);
        button.setFontColor((int) 0xFFFFFFFFL);
    }

    private void kickPlayerFromDashboard(Player administrator, String targetUid) {
        if (!administrator.isAdmin() || administrator.getUID().equals(targetUid)) {
            administrator.sendTextMessage("<color=#FF7777>You cannot kick that player.</color>");
            return;
        }
        Player target = Server.getPlayerByUID(targetUid);
        if (target == null) {
            administrator.sendTextMessage("<color=#AAAAAA>That player is no longer connected.</color>");
            return;
        }
        String targetName = target.getName();
        target.kick("Kicked by administrator " + administrator.getName());
        System.out.println("[CivicCore] " + administrator.getName() + " kicked " + targetName
                + " (" + targetUid + ") from the admin dashboard");
        executeDelayed(0.5f, () -> {
            AdminView view = adminViews.get(administrator.getUID());
            if (view != null) refreshAdminDashboard(view);
        });
    }

    private void confirmBanFromDashboard(Player administrator, String targetUid) {
        if (!administrator.isAdmin() || administrator.getUID().equals(targetUid)) {
            administrator.sendTextMessage("<color=#FF7777>You cannot ban that player.</color>");
            return;
        }
        Player target = Server.getPlayerByUID(targetUid);
        if (target == null) {
            administrator.sendTextMessage("<color=#AAAAAA>That player is no longer connected.</color>");
            return;
        }
        String targetName = target.getName();
        administrator.showMessageBox(MessageBoxButtons.Yes_No, "Confirm permanent ban",
                "Ban " + targetName + " from this server?", -1, selectedButton -> {
                    if (selectedButton == null || selectedButton != 0 || !administrator.isAdmin()) return;
                    Player currentTarget = Server.getPlayerByUID(targetUid);
                    if (currentTarget == null) {
                        administrator.sendTextMessage("<color=#AAAAAA>That player is no longer connected.</color>");
                        return;
                    }
                    Server.banPlayer(targetUid, "Banned by administrator " + administrator.getName(), -1);
                    System.out.println("[CivicCore] " + administrator.getName() + " banned "
                            + targetName + " (" + targetUid + ") from the admin dashboard");
                    executeDelayed(0.5f, () -> {
                        AdminView view = adminViews.get(administrator.getUID());
                        if (view != null) refreshAdminDashboard(view);
                    });
                });
    }

    private void closeAdminDashboard(Player player) {
        AdminView view = adminViews.remove(player.getUID());
        if (view != null) {
            player.removeUIElement(view.window());
        }
        player.setMouseCursorVisible(false);
    }

    private void handleUserStoreCommand(Player player, String[] parts) {
        if (parts.length == 1) { toggleUserStore(player); return; }
        if (!parts[1].equalsIgnoreCase("sell") || parts.length < 3 || parts.length > 4) {
            player.sendTextMessage("Usage: /userstore or /userstore sell <price> [quantity]"); return;
        }
        try {
            long price = parseCurrencyAmount(parts[2]);
            Inventory inventory = player.getInventory();
            int slot = inventory.getEquippedItemSlot();
            Inventory.SlotType slotType = inventory.getEquippedItemSlotType();
            Item equipped = inventory.getItem(slot, slotType);
            if (equipped == null || !equipped.isValid()) throw new IllegalStateException("Equip the item you want to sell.");
            StoreCatalog.StoreItem catalogItem = storeCatalog.find(equipped.getTypeID());
            if (catalogItem == null) throw new IllegalStateException("That item cannot be listed in the user store.");
            int quantity = parts.length == 4 ? Integer.parseInt(parts[3]) : equipped.getStack();
            if (quantity <= 0 || quantity > equipped.getStack())
                throw new IllegalArgumentException("Quantity must be between 1 and " + equipped.getStack() + ".");
            short itemType = equipped.getTypeID(); int variant = equipped.getVariant();
            if (!inventory.removeItem(slot, slotType, quantity)) throw new IllegalStateException("Could not remove the item from inventory.");
            try {
                UserStoreListing listing = userStore.create(characterKey(player), player.getName(),
                        itemType, variant, quantity, price);
                player.sendTextMessage("<color=#77FF99>Listed " + quantity + " × " + catalogItem.name()
                        + " for " + formatBalance(price) + " (listing #" + listing.id() + ").</color>");
            } catch (RuntimeException exception) {
                inventory.addItem(itemType, variant, quantity); throw exception;
            }
        } catch (NumberFormatException exception) {
            player.sendTextMessage("<color=#FF7777>Quantity must be a whole number.</color>");
        } catch (RuntimeException exception) {
            player.sendTextMessage("<color=#FF7777>" + exception.getMessage() + "</color>");
        }
    }

    private void toggleUserStore(Player player) {
        if (userStoreViews.containsKey(player.getUID())) { closeUserStore(player); return; }
        if (storeViews.containsKey(player.getUID())) closeStore(player);
        if (journalViews.containsKey(player.getUID())) closeJournal(player);
        openUserStore(player);
    }

    private void openUserStore(Player player) {
        UserStoreView old = userStoreViews.remove(player.getUID());
        if (old != null) player.removeUIElement(old.window());
        UIElement window = new UIElement(); window.setPosition(50f,50f,true); window.setPivot(Pivot.MiddleCenter);
        window.setSize(780f,620f,false); window.setBackgroundColor((int)0x161B22F5L);
        window.setBorder(2f); window.setBorderColor((int)0xE8C547FFL); window.setBorderEdgeRadius(8f,false);
        UILabel title = new UILabel("User Store"); title.setPosition(20f,12f,false); title.setSize(620f,42f,false);
        title.setFontSize(28f); title.setFontColor((int)0xF4E3A1FFL); title.setTextAlign(TextAnchor.MiddleLeft); window.addChild(title);
        UILabel hint = new UILabel("Equip an item, then use /userstore sell <price> [quantity] to list it.");
        hint.setPosition(20f,56f,false); hint.setSize(680f,34f,false); hint.setFontSize(15f);
        hint.setFontColor((int)0xBBBBBBFFL); hint.setTextAlign(TextAnchor.MiddleLeft); window.addChild(hint);
        UILabel close = journalButton("X",724f,14f,34f,34f); close.setBackgroundColor((int)0x8B2D2DFFL); window.addChild(close);
        UILabel refresh = journalButton("Refresh",620f,56f,138f,34f); window.addChild(refresh);
        UIScrollView list = new UIScrollView(UIScrollView.ScrollViewMode.Vertical);
        list.setPosition(20f,100f,false); list.setSize(738f,498f,false);
        list.setVerticalScrollerVisibility(UIScrollView.ScrollerVisibility.Auto);
        list.setHorizontalScrollerVisibility(UIScrollView.ScrollerVisibility.Hidden); window.addChild(list);
        Map<Integer,Long> listingByButton = new ConcurrentHashMap<>();
        int index=0; float y=0f; String buyerKey=characterKey(player);
        for(UserStoreListing listing:userStore.getListings()){
            StoreCatalog.StoreItem item=storeCatalog.find(listing.itemType());
            if(item==null)continue;
            UIElement row=new UIElement(); row.setPosition(0f,y,false); row.setSize(710f,78f,false);
            row.setBackgroundColor(index++%2==0?(int)0x28313DFFL:(int)0x202832FFL); list.addChild(row);
            UILabel details=new UILabel(listing.quantity()+" × "+item.name()+"\nSeller: "+listing.sellerName());
            details.setPosition(16f,5f,false); details.setSize(390f,68f,false); details.setFontSize(17f);
            details.setFontColor((int)0xFFFFFFFFL); details.setTextAlign(TextAnchor.MiddleLeft); row.addChild(details);
            UILabel price=new UILabel(formatBalance(listing.price())); price.setPosition(414f,16f,false);
            price.setSize(130f,46f,false); price.setFontSize(18f); price.setFontColor((int)0xF4E3A1FFL);
            price.setTextAlign(TextAnchor.MiddleCenter); row.addChild(price);
            boolean own=listing.sellerKey().equals(buyerKey);
            UILabel action=journalButton(own?"CANCEL":"BUY",554f,16f,136f,46f);
            action.setBackgroundColor(own?(int)0x8B2D2DFFL:(int)0x2D7D46FFL); row.addChild(action);
            listingByButton.put(action.getID(),listing.id()); y+=84f;
        }
        if(index==0){UILabel empty=new UILabel("No player listings are currently available.");empty.setPosition(0f,30f,false);
            empty.setSize(710f,50f,false);empty.setFontSize(18f);empty.setFontColor((int)0xAAAAAAFFL);
            empty.setTextAlign(TextAnchor.MiddleCenter);list.addChild(empty);}
        UserStoreView view=new UserStoreView(window,close,refresh,listingByButton);
        userStoreViews.put(player.getUID(),view);player.addUIElement(window);player.setMouseCursorVisible(true);
    }

    private void closeUserStore(Player player){UserStoreView view=userStoreViews.remove(player.getUID());if(view!=null)player.removeUIElement(view.window());player.setMouseCursorVisible(false);}

    private void handleUserStoreClick(Player player,UserStoreView view,int elementId){
        if(elementId==view.close().getID()){closeUserStore(player);return;}
        if(elementId==view.refresh().getID()){openUserStore(player);return;}
        Long listingId=view.listingByButton().get(elementId);if(listingId==null)return;
        String buyerKey=characterKey(player);
        UserStoreListing listing=userStore.getListings().stream().filter(item->item.id()==listingId).findFirst().orElse(null);
        if(listing==null){player.sendTextMessage("<color=#FFAA66>That listing is no longer available.</color>");openUserStore(player);return;}
        try{
            if(listing.sellerKey().equals(buyerKey)){
                UserStoreListing cancelled=userStore.cancel(listingId,buyerKey).orElseThrow();
                if(player.getInventory().addItem(cancelled.itemType(),cancelled.itemVariant(),cancelled.quantity())==null){
                    userStore.create(cancelled.sellerKey(),cancelled.sellerName(),cancelled.itemType(),cancelled.itemVariant(),cancelled.quantity(),cancelled.price());
                    throw new IllegalStateException("Your inventory is full; the item was relisted.");
                }
                player.sendTextMessage("<color=#77FF99>Listing cancelled and item returned.</color>");
            }else{
                UserStoreListing purchased=userStore.purchase(listingId,buyerKey);
                if(player.getInventory().addItem(purchased.itemType(),purchased.itemVariant(),purchased.quantity())==null){
                    userStore.reversePurchase(purchased,buyerKey);throw new IllegalStateException("Your inventory is full; the purchase was refunded.");
                }
                updateBalanceLabel(player);
                for (Player connected : Server.getAllPlayers()) {
                    CharacterService.CharacterSummary active = activeCharacters.get(connected.getUID());
                    if (active != null && active.economyKey().equals(purchased.sellerKey()))
                        updateBalanceLabel(connected);
                }
                player.sendTextMessage("<color=#77FF99>Purchased listing for "+formatBalance(purchased.price())+".</color>");
            }
        }catch(RuntimeException exception){player.sendTextMessage("<color=#FF7777>"+exception.getMessage()+"</color>");}
        openUserStore(player);
    }

    private void toggleStore(Player player) {
        if (storeViews.containsKey(player.getUID())) {
            closeStore(player);
        } else {
            if (userStoreViews.containsKey(player.getUID())) closeUserStore(player);
            if (adminViews.containsKey(player.getUID())) {
                closeAdminDashboard(player);
            }
            openStore(player);
        }
    }

    private void openStore(Player player) {
        UIElement window = new UIElement();
        window.setPosition(50f, 50f, true);
        window.setPivot(Pivot.MiddleCenter);
        window.setSize(760f, 620f, false);
        window.setBackgroundColor((int) 0x161B22F2L);
        window.setBorder(2f);
        window.setBorderColor((int) 0xE8C547FFL);
        window.setBorderEdgeRadius(8f, false);

        UILabel title = new UILabel("Marketplace");
        title.setPosition(20f, 12f, false);
        title.setSize(650f, 42f, false);
        title.setFontSize(28f);
        title.setFontColor((int) 0xF4E3A1FFL);
        title.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(title);

        UILabel closeButton = new UILabel("X");
        closeButton.setPosition(704f, 12f, false);
        closeButton.setSize(36f, 36f, false);
        closeButton.setFontSize(22f);
        closeButton.setTextAlign(TextAnchor.MiddleCenter);
        closeButton.setBackgroundColor((int) 0x8B2D2DFFL);
        closeButton.setClickable(true);
        window.addChild(closeButton);

        UIScrollView categoryTabs = new UIScrollView(UIScrollView.ScrollViewMode.Horizontal);
        categoryTabs.setPosition(20f, 62f, false);
        categoryTabs.setSize(720f, 46f, false);
        categoryTabs.setVerticalScrollerVisibility(UIScrollView.ScrollerVisibility.Hidden);
        categoryTabs.setHorizontalScrollerVisibility(UIScrollView.ScrollerVisibility.Auto);
        categoryTabs.setMouseWheelScrollSize(130f);
        window.addChild(categoryTabs);

        UILabel searchLabel = new UILabel("Search:");
        searchLabel.setPosition(20f, 116f, false);
        searchLabel.setSize(82f, 38f, false);
        searchLabel.setFontSize(18f);
        searchLabel.setFontColor((int) 0xF4E3A1FFL);
        searchLabel.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(searchLabel);

        UITextField searchField = new UITextField("");
        searchField.setPosition(102f, 116f, false);
        searchField.setSize(638f, 38f, false);
        searchField.setFontSize(18f);
        searchField.setFontColor((int) 0xFFFFFFFFL);
        searchField.setBackgroundColor((int) 0x202832FFL);
        searchField.setBorder(1f);
        searchField.setBorderColor((int) 0x566273FFL);
        searchField.setBorderEdgeRadius(4f, false);
        searchField.setMaxCharacters(80);
        window.addChild(searchField);

        UILabel cartSummary = new UILabel("Cart: 0 items     $0.00");
        cartSummary.setPosition(20f, 162f, false);
        cartSummary.setSize(420f, 38f, false);
        cartSummary.setFontSize(18f);
        cartSummary.setFontColor((int) 0xF4E3A1FFL);
        cartSummary.setTextAlign(TextAnchor.MiddleLeft);
        window.addChild(cartSummary);

        UILabel clearCartButton = new UILabel("CLEAR");
        clearCartButton.setPosition(450f, 162f, false);
        clearCartButton.setSize(100f, 38f, false);
        clearCartButton.setFontSize(16f);
        clearCartButton.setTextAlign(TextAnchor.MiddleCenter);
        clearCartButton.setBackgroundColor((int) 0x8B2D2DFFL);
        clearCartButton.setClickable(true);
        window.addChild(clearCartButton);

        UILabel checkoutButton = new UILabel("CHECKOUT");
        checkoutButton.setPosition(560f, 162f, false);
        checkoutButton.setSize(180f, 38f, false);
        checkoutButton.setFontSize(16f);
        checkoutButton.setTextAlign(TextAnchor.MiddleCenter);
        checkoutButton.setBackgroundColor((int) 0x2D7D46FFL);
        checkoutButton.setClickable(true);
        window.addChild(checkoutButton);

        UIScrollView itemList = new UIScrollView(UIScrollView.ScrollViewMode.Vertical);
        itemList.setPosition(20f, 210f, false);
        itemList.setSize(720f, 390f, false);
        itemList.setVerticalScrollerVisibility(UIScrollView.ScrollerVisibility.Auto);
        itemList.setHorizontalScrollerVisibility(UIScrollView.ScrollerVisibility.Hidden);
        itemList.setMouseWheelScrollSize(46f);
        window.addChild(itemList);

        StoreView view = new StoreView(window, closeButton, searchField, itemList,
                cartSummary, clearCartButton, checkoutButton);
        List<String> categories = new ArrayList<>();
        categories.add("All");
        storeCatalog.items().stream().map(StoreCatalog.StoreItem::category).distinct().forEach(categories::add);
        float tabX = 0f;
        for (String category : categories) {
            UILabel tab = new UILabel(formatCategoryName(category));
            tab.setPosition(tabX, 0f, false);
            tab.setSize(130f, 38f, false);
            tab.setFontSize(16f);
            tab.setFontColor((int) 0xFFFFFFFFL);
            tab.setTextAlign(TextAnchor.MiddleCenter);
            tab.setBorderEdgeRadius(4f, false);
            tab.setClickable(true);
            categoryTabs.addChild(tab);
            view.categoriesByButtonId().put(tab.getID(), category);
            view.categoryButtons().put(category, tab);
            tabX += 136f;
        }
        updateStoreCategoryStyles(view);
        rebuildStoreItems(view);

        storeViews.put(player.getUID(), view);
        player.addUIElement(window);
        player.setMouseCursorVisible(true);
    }

    private void rebuildStoreItems(StoreView view) {
        view.itemList().removeAllChilds();
        view.itemsByButtonId().clear();
        view.incrementByButtonId().clear();
        view.decrementByButtonId().clear();
        view.quantityLabels().clear();
        view.cartStatusLabels().clear();
        int itemIndex = 0;
        float yOffset = 0f;
        Set<Short> userListedTypes = userStore.getListedItemTypes();
        for (StoreCatalog.StoreItem storeItem : storeCatalog.items()) {
            if (!"All".equals(view.selectedCategory())
                    && !storeItem.category().equals(view.selectedCategory())) {
                continue;
            }
            if (!view.searchText().isBlank()
                    && !storeItem.name().toLowerCase(Locale.US).contains(view.searchText())) {
                continue;
            }

            UIElement itemRow = new UIElement();
            itemRow.setPosition(0f, yOffset, false);
            itemRow.setSize(690f, 86f, false);
            itemRow.setBackgroundColor(itemIndex % 2 == 0
                    ? (int) 0x28313DFFL : (int) 0x202832FFL);
            itemRow.setBorderEdgeRadius(6f, false);
            view.itemList().addChild(itemRow);

            UIElement icon = new UIElement();
            icon.setPosition(6f, 5f, false);
            icon.setSize(76f, 76f, false);
            icon.setBackgroundColor((int) 0x11161DFFL);
            icon.setBorder(1f);
            icon.setBorderColor((int) 0x566273FFL);
            icon.setBorderEdgeRadius(5f, false);
            Items.ItemDefinition definition = Definitions.getItemDefinition(storeItem.id());
            TextureAsset iconTexture = definition == null ? null : definition.getIcon(0);
            if (iconTexture != null) {
                icon.style.backgroundImage.set(iconTexture);
                icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
            }
            itemRow.addChild(icon);

            boolean outOfStock = userListedTypes.contains(storeItem.id());
            if (outOfStock) view.cart().remove(storeItem.id());
            UILabel itemDetails = new UILabel(storeItem.name() + "\n"
                    + (outOfStock ? "<color=#FF7777>OUT OF STOCK — available in User Store</color>"
                    : formatBalance(storeItem.price())));
            itemDetails.setPosition(98f, 7f, false);
            itemDetails.setSize(280f, 72f, false);
            itemDetails.setFontSize(18f);
            itemDetails.setFontColor((int) 0xFFFFFFFFL);
            itemDetails.setTextAlign(TextAnchor.MiddleLeft);
            itemRow.addChild(itemDetails);

            UILabel minusButton = createCartQuantityButton("-");
            minusButton.setPosition(402f, 20f, false);
            minusButton.setClickable(!outOfStock);
            if (outOfStock) minusButton.setBackgroundColor((int) 0x333333FFL);
            itemRow.addChild(minusButton);
            if (!outOfStock) view.decrementByButtonId().put(minusButton.getID(), storeItem);

            int quantity = !outOfStock && view.cart().containsKey(storeItem.id())
                    ? view.cart().get(storeItem.id()).quantity() : 0;
            UILabel quantityLabel = new UILabel(Integer.toString(quantity));
            quantityLabel.setPosition(452f, 20f, false);
            quantityLabel.setSize(56f, 46f, false);
            quantityLabel.setFontSize(18f);
            quantityLabel.setFontColor((int) 0xFFFFFFFFL);
            quantityLabel.setTextAlign(TextAnchor.MiddleCenter);
            quantityLabel.setBackgroundColor((int) 0x11161DFFL);
            itemRow.addChild(quantityLabel);
            view.quantityLabels().put(storeItem.id(), quantityLabel);

            UILabel plusButton = createCartQuantityButton("+");
            plusButton.setPosition(518f, 20f, false);
            plusButton.setClickable(!outOfStock);
            if (outOfStock) plusButton.setBackgroundColor((int) 0x333333FFL);
            itemRow.addChild(plusButton);
            if (!outOfStock) view.incrementByButtonId().put(plusButton.getID(), storeItem);

            UILabel inCartLabel = new UILabel("IN CART");
            inCartLabel.setPosition(578f, 20f, false);
            inCartLabel.setSize(92f, 46f, false);
            inCartLabel.setFontSize(14f);
            inCartLabel.setFontColor((int) 0x77FF99FFL);
            inCartLabel.setTextAlign(TextAnchor.MiddleCenter);
            inCartLabel.setVisible(quantity > 0);
            itemRow.addChild(inCartLabel);
            view.cartStatusLabels().put(storeItem.id(), inCartLabel);
            itemIndex++;
            yOffset += 92f;
        }

        if (itemIndex == 0) {
            UILabel empty = new UILabel("No items match this category and search.");
            empty.setPosition(0f, 20f, false);
            empty.setSize(690f, 50f, false);
            empty.setFontSize(18f);
            empty.setFontColor((int) 0xAAAAAAFFL);
            empty.setTextAlign(TextAnchor.MiddleCenter);
            view.itemList().addChild(empty);
        }
    }

    private static void updateStoreCategoryStyles(StoreView view) {
        view.categoryButtons().forEach((category, button) -> button.setBackgroundColor(
                category.equals(view.selectedCategory()) ? (int) 0x9A7B24FFL : (int) 0x28313DFFL));
    }

    private static UILabel createCartQuantityButton(String text) {
        UILabel button = new UILabel(text);
        button.setSize(40f, 46f, false);
        button.setFontSize(22f);
        button.setFontColor((int) 0xFFFFFFFFL);
        button.setTextAlign(TextAnchor.MiddleCenter);
        button.setBackgroundColor((int) 0x3A4655FFL);
        button.setBorderEdgeRadius(4f, false);
        button.setClickable(true);
        return button;
    }

    private void closeStore(Player player) {
        StoreView view = storeViews.remove(player.getUID());
        if (view != null) {
            player.removeUIElement(view.window());
        }
        player.setMouseCursorVisible(false);
    }

    private void changeCartQuantity(StoreView view, StoreCatalog.StoreItem item, int delta) {
        int oldQuantity = view.cart().containsKey(item.id()) ? view.cart().get(item.id()).quantity() : 0;
        int newQuantity = Math.max(0, Math.min(99, oldQuantity + delta));
        if (newQuantity == 0) {
            view.cart().remove(item.id());
        } else {
            view.cart().put(item.id(), new CartLine(item, newQuantity));
        }
        UILabel quantityLabel = view.quantityLabels().get(item.id());
        if (quantityLabel != null) {
            quantityLabel.setText(Integer.toString(newQuantity));
        }
        UILabel statusLabel = view.cartStatusLabels().get(item.id());
        if (statusLabel != null) {
            statusLabel.setVisible(newQuantity > 0);
        }
        updateCartSummary(view);
    }

    private static void updateCartSummary(StoreView view) {
        int itemCount = 0;
        long total = 0L;
        for (CartLine line : view.cart().values()) {
            itemCount += line.quantity();
            total = Math.addExact(total, Math.multiplyExact(line.item().price(), line.quantity()));
        }
        view.cartSummary().setText("Cart: " + itemCount + " item(s)     " + formatBalance(total));
    }

    private void clearCart(StoreView view) {
        view.cart().clear();
        view.quantityLabels().values().forEach(label -> label.setText("0"));
        view.cartStatusLabels().values().forEach(label -> label.setVisible(false));
        updateCartSummary(view);
    }

    private void checkoutCart(Player player, StoreView view) {
        if (view.cart().isEmpty()) {
            player.sendTextMessage("<color=#AAAAAA>Your shopping cart is empty.</color>");
            return;
        }

        Set<Short> userListedTypes = userStore.getListedItemTypes();
        boolean removedUnavailable = view.cart().keySet().removeIf(userListedTypes::contains);
        if (removedUnavailable) {
            rebuildStoreItems(view);
            updateCartSummary(view);
            player.sendTextMessage("<color=#FFAA66>Items now offered in the User Store were removed from your cart.</color>");
            if (view.cart().isEmpty()) return;
        }

        long total = 0L;
        for (CartLine line : view.cart().values()) {
            total = Math.addExact(total, Math.multiplyExact(line.item().price(), line.quantity()));
        }
        if (!economy.withdraw(characterKey(player), total)) {
            player.sendTextMessage("<color=#FF7777>You cannot afford this cart total of "
                    + formatBalance(total) + ".</color>");
            return;
        }

        long refund = 0L;
        int purchasedCount = 0;
        List<Short> completedItems = new ArrayList<>();
        for (CartLine line : view.cart().values()) {
            Item addedItem = player.getInventory().addItem(line.item().id(), 0, line.quantity());
            if (addedItem == null) {
                refund = Math.addExact(refund, Math.multiplyExact(line.item().price(), line.quantity()));
            } else {
                purchasedCount += line.quantity();
                completedItems.add(line.item().id());
            }
        }
        completedItems.forEach(view.cart()::remove);
        if (refund > 0) {
            economy.deposit(characterKey(player), refund);
            player.sendTextMessage("<color=#FFAA66>Some items did not fit. Refunded "
                    + formatBalance(refund) + "; those items remain in your cart.</color>");
        }
        updateBalanceLabel(player);
        if (purchasedCount > 0) {
            player.sendTextMessage("<color=#77FF99>Checkout complete:</color> " + purchasedCount
                    + " item(s) purchased for " + formatBalance(total - refund) + ".");
        }
        rebuildStoreItems(view);
        updateCartSummary(view);
    }

    /** Refreshes the HUD after another plugin changes a connected player's balance. */
    public void updateBalanceLabel(Player player) {
        UILabel label = balanceLabels.get(player.getUID());
        if (label != null) {
            label.setText("Cash: " + formatBalance(economy.getBalance(characterKey(player))));
        }
    }

    private void showBalance(Player player) {
        UILabel oldLabel = balanceLabels.remove(player.getUID());
        if (oldLabel != null) {
            player.removeUIElement(oldLabel);
        }

        UILabel label = new UILabel();
        label.setTextAlign(TextAnchor.MiddleCenter);
        label.setFontSize(22f);
        label.setFontColor((int) 0xF4E3A1FFL);
        //label.setBackgroundColor((int) 0x161B22CCL);
        //label.setBorder(2f);
        //label.setBorderColor((int) 0xE8C547FFL);
        label.setPosition(12f, 12f, false);
        label.setPivot(Pivot.UpperLeft);
        label.setSize(260f, 42f, false);

        balanceLabels.put(player.getUID(), label);
        updateBalanceLabel(player);
        player.addUIElement(label);
    }

    private void showWorldClock(Player player) {
        UILabel oldLabel = worldTimeLabels.remove(player.getUID());
        if (oldLabel != null) {
            player.removeUIElement(oldLabel);
        }

        UILabel label = new UILabel();
        label.setTextAlign(TextAnchor.MiddleCenter);
        label.setFontSize(20f);
        label.setFontColor((int) 0xF4E3A1FFL);
        label.setPosition(50f, 0.5f, true);
        label.setPivot(Pivot.UpperCenter);
        label.setSize(460f, 42f, false);

        worldTimeLabels.put(player.getUID(), label);
        updateWorldClockLabel(label, Server.getGameTime());
        player.addUIElement(label);
    }

    private void updateWorldClockLabels() {
        net.risingworld.api.objects.Time time = Server.getGameTime();
        paySalaryWhenPeriodChanges(time);
        for (UILabel label : worldTimeLabels.values()) {
            updateWorldClockLabel(label, time);
        }
    }

    private void paySalaryWhenPeriodChanges(net.risingworld.api.objects.Time time) {
        PayPeriod currentPeriod = PayPeriod.from(time);
        if (lastSalaryPeriod == null) {
            lastSalaryPeriod = currentPeriod;
            return;
        }
        if (currentPeriod.equals(lastSalaryPeriod)) {
            return;
        }

        lastSalaryPeriod = currentPeriod;
        long salary = economySettings.baseSalary();
        Player[] players = Server.getAllPlayers();
        System.out.println("[CivicCore] Running 8-hour payroll for " + players.length
                + " connected player(s) at " + currentPeriod.periodStartHour() + ":00 on "
                + currentPeriod.year() + "-" + currentPeriod.month() + "-" + currentPeriod.day());
        for (Player player : players) {
            if (!activeCharacters.containsKey(player.getUID())) continue;
            String characterKey = characterKey(player);
            economy.createAccount(characterKey, economySettings.defaultBalance());
            long newBalance = economy.deposit(characterKey, salary);
            updateBalanceLabel(player);
            player.sendTextMessage("<color=#77FF99>8-hour salary paid:</color> " + formatBalance(salary));
            System.out.println("[CivicCore] Paid " + player.getName() + " " + formatBalance(salary)
                    + "; new balance " + formatBalance(newBalance));
        }
    }

    private static void updateWorldClockLabel(UILabel label, net.risingworld.api.objects.Time time) {
        label.setText(String.format(Locale.US, "Year %d  •  Month %d  •  Day %d     %02d:%02d",
                time.getYear(), time.getMonth(), time.getDay(), time.getHours(), time.getMinutes()));
    }

    private static String formatBalance(long minorUnits) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        return currency.format(minorUnits / 100.0);
    }

    private static long parseCurrencyAmount(String value) {
        try {
            long amount = new BigDecimal(value).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (amount <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
            return amount;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid amount with no more than two decimal places.");
        }
    }

    private record PayPeriod(int year, int month, int day, int period) {
        private static PayPeriod from(net.risingworld.api.objects.Time time) {
            return new PayPeriod(time.getYear(), time.getMonth(), time.getDay(), time.getHours() / 8);
        }

        private int periodStartHour() {
            return period * 8;
        }
    }

    private static String formatCategoryName(String category) {
        String words = category.replace('_', ' ').toLowerCase(Locale.US);
        return words.isEmpty() ? "Other" : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static void debug(String message) {
        System.out.println("[CivicCore/DEBUG] " + message);
    }

    @EventMethod
    public void onStoreClick(PlayerUIElementClickEvent event) {
        Player player = event.getPlayer();
        JournalView journalView = journalViews.get(player.getUID());
        if (journalView != null) {
            handleJournalClick(player, journalView, event.getUIElement().getID());
            return;
        }

        UserStoreView userStoreView = userStoreViews.get(player.getUID());
        if (userStoreView != null) {
            handleUserStoreClick(player, userStoreView, event.getUIElement().getID());
            return;
        }
        CommandListView commandListView = commandListViews.get(player.getUID());
        if (commandListView != null) {
            if (event.getUIElement().getID() == commandListView.closeButton().getID()) {
                closeCommands(player);
            }
            return;
        }
        AboutView aboutView = aboutViews.get(player.getUID());
        if (aboutView != null) {
            if (event.getUIElement().getID() == aboutView.closeButton().getID()) {
                closeAbout(player);
            }
            return;
        }
        AppearanceView appearanceView = appearanceViews.get(player.getUID());
        if (appearanceView != null) {
            handleAppearanceClick(player, appearanceView, event.getUIElement().getID());
            return;
        }
        CharacterSelectionView selectionView = characterSelectionViews.get(player.getUID());
        if (selectionView != null) {
            int selectionElementId = event.getUIElement().getID();
            if (selectionElementId == selectionView.closeButton().getID()) {
                closeCharacterSelection(player, selectionView);
                return;
            }
            CharacterService.CharacterSummary character = selectionView.charactersByButtonId()
                    .get(selectionElementId);
            if (character != null) {
                activateCharacter(player, character);
                return;
            }
            CharacterService.CharacterSummary deleteCharacter = selectionView.deleteByButtonId()
                    .get(selectionElementId);
            if (deleteCharacter != null) {
                confirmDeleteCharacter(player, deleteCharacter);
                return;
            }
            Integer createSlot = selectionView.createSlotsByButtonId().get(selectionElementId);
            if (createSlot != null) {
                promptCreateCharacter(player, createSlot);
                return;
            }
        }
        AdminView adminView = adminViews.get(player.getUID());
        if (adminView != null) {
            int adminElementId = event.getUIElement().getID();
            if (adminElementId == adminView.closeButton().getID()) {
                closeAdminDashboard(player);
                return;
            }
            if (adminElementId == adminView.refreshButton().getID()) {
                refreshAdminDashboard(adminView);
                return;
            }
            if (adminElementId == adminView.adminOverrideButton().getID()) {
                claimAdminOverrideEnabled = !claimAdminOverrideEnabled;
                refreshAdminDashboard(adminView);
                player.sendTextMessage("<color=#E8C547>Claim administrator bypass is now "
                        + (claimAdminOverrideEnabled ? "enabled" : "disabled")
                        + " for this server session.</color>");
                return;
            }
            String kickTarget = adminView.kickTargetsByButtonId().get(adminElementId);
            if (kickTarget != null) {
                kickPlayerFromDashboard(player, kickTarget);
                return;
            }
            String banTarget = adminView.banTargetsByButtonId().get(adminElementId);
            if (banTarget != null) {
                confirmBanFromDashboard(player, banTarget);
                return;
            }
        }

        StoreView view = storeViews.get(player.getUID());
        if (view == null) {
            return;
        }
        int elementId = event.getUIElement().getID();
        if (elementId == view.closeButton().getID()) {
            closeStore(player);
            return;
        }
        if (elementId == view.clearCartButton().getID()) {
            clearCart(view);
            return;
        }
        if (elementId == view.checkoutButton().getID()) {
            checkoutCart(player, view);
            return;
        }
        String category = view.categoriesByButtonId().get(elementId);
        if (category != null) {
            view.setSelectedCategory(category);
            updateStoreCategoryStyles(view);
            rebuildStoreItems(view);
            return;
        }
        StoreCatalog.StoreItem incrementItem = view.incrementByButtonId().get(elementId);
        if (incrementItem != null) {
            changeCartQuantity(view, incrementItem, 1);
            return;
        }
        StoreCatalog.StoreItem decrementItem = view.decrementByButtonId().get(elementId);
        if (decrementItem != null) {
            changeCartQuantity(view, decrementItem, -1);
        }
    }

    private void closeCharacterSelection(Player player, CharacterSelectionView view) {
        if (!activeCharacters.containsKey(player.getUID())) {
            List<CharacterService.CharacterSummary> characters = characterService.getCharacters(player.getUID());
            if (!characters.isEmpty()) {
                // Closing the mandatory initial selector keeps isolation intact
                // by continuing with the first existing (legacy) character.
                activateCharacter(player, characters.get(0));
                return;
            }
            player.sendTextMessage("<color=#FFAA66>Create a character before closing this menu.</color>");
            return;
        }
        characterSelectionViews.remove(player.getUID());
        player.removeUIElement(view.window());
        player.stopInput(false, false);
        player.setMouseCursorVisible(false);
    }

    @EventMethod
    public void onStoreSearchChanged(PlayerUITextFieldChangeEvent event) {
        JournalView journalView = journalViews.get(event.getPlayer().getUID());
        if (journalView != null && event.getUITextField().getID() == journalView.editor().getID()) {
            journalView.setDraft(event.getNewText() == null ? "" : event.getNewText());
            return;
        }
        StoreView view = storeViews.get(event.getPlayer().getUID());
        if (view == null || event.getUITextField().getID() != view.searchField().getID()) {
            return;
        }
        String search = event.getNewText() == null ? "" : event.getNewText();
        view.setSearchText(search.trim().toLowerCase(Locale.US));
        rebuildStoreItems(view);
    }

    @EventMethod
    public void onJournalTextInput(PlayerUIInputTextEvent event) {
        JournalView view = journalViews.get(event.getPlayer().getUID());
        if (view != null && event.getUITextField().getID() == view.editor().getID())
            view.setDraft(event.getText() == null ? "" : event.getText());
    }

    private static final class StoreView {
        private final UIElement window;
        private final UILabel closeButton;
        private final UITextField searchField;
        private final UIScrollView itemList;
        private final UILabel cartSummary;
        private final UILabel clearCartButton;
        private final UILabel checkoutButton;
        private final Map<Integer, StoreCatalog.StoreItem> itemsByButtonId = new ConcurrentHashMap<>();
        private final Map<Integer, StoreCatalog.StoreItem> incrementByButtonId = new ConcurrentHashMap<>();
        private final Map<Integer, StoreCatalog.StoreItem> decrementByButtonId = new ConcurrentHashMap<>();
        private final Map<Short, UILabel> quantityLabels = new ConcurrentHashMap<>();
        private final Map<Short, UILabel> cartStatusLabels = new ConcurrentHashMap<>();
        private final Map<Short, CartLine> cart = new ConcurrentHashMap<>();
        private final Map<Integer, String> categoriesByButtonId = new ConcurrentHashMap<>();
        private final Map<String, UILabel> categoryButtons = new ConcurrentHashMap<>();
        private String selectedCategory = "All";
        private String searchText = "";

        private StoreView(UIElement window, UILabel closeButton, UITextField searchField,
                          UIScrollView itemList, UILabel cartSummary, UILabel clearCartButton,
                          UILabel checkoutButton) {
            this.window = window;
            this.closeButton = closeButton;
            this.searchField = searchField;
            this.itemList = itemList;
            this.cartSummary = cartSummary;
            this.clearCartButton = clearCartButton;
            this.checkoutButton = checkoutButton;
        }

        private UIElement window() { return window; }
        private UILabel closeButton() { return closeButton; }
        private UITextField searchField() { return searchField; }
        private UIScrollView itemList() { return itemList; }
        private UILabel cartSummary() { return cartSummary; }
        private UILabel clearCartButton() { return clearCartButton; }
        private UILabel checkoutButton() { return checkoutButton; }
        private Map<Integer, StoreCatalog.StoreItem> itemsByButtonId() { return itemsByButtonId; }
        private Map<Integer, StoreCatalog.StoreItem> incrementByButtonId() { return incrementByButtonId; }
        private Map<Integer, StoreCatalog.StoreItem> decrementByButtonId() { return decrementByButtonId; }
        private Map<Short, UILabel> quantityLabels() { return quantityLabels; }
        private Map<Short, UILabel> cartStatusLabels() { return cartStatusLabels; }
        private Map<Short, CartLine> cart() { return cart; }
        private Map<Integer, String> categoriesByButtonId() { return categoriesByButtonId; }
        private Map<String, UILabel> categoryButtons() { return categoryButtons; }
        private String selectedCategory() { return selectedCategory; }
        private void setSelectedCategory(String selectedCategory) { this.selectedCategory = selectedCategory; }
        private String searchText() { return searchText; }
        private void setSearchText(String searchText) { this.searchText = searchText; }
    }

    private record CartLine(StoreCatalog.StoreItem item, int quantity) {
    }

    private record AdminView(UIElement window, UILabel closeButton, UILabel refreshButton,
                             UILabel summary, UILabel adminOverrideButton, UIScrollView playerList,
                             Map<Integer, String> kickTargetsByButtonId,
                             Map<Integer, String> banTargetsByButtonId) {
    }

    private record AboutView(UIElement window, UILabel closeButton) {
    }

    private record CommandListView(UIElement window, UILabel closeButton) {
    }

    private record UserStoreView(UIElement window, UILabel close, UILabel refresh,
                                 Map<Integer, Long> listingByButton) { }

    private static final class JournalView {
        private final UIElement window;
        private final UILabel close;
        private final UILabel newSection;
        private final UILabel previousPage;
        private final UILabel nextPage;
        private final UILabel newPage;
        private final UILabel save;
        private final UITextField editor;
        private final String characterKey;
        private final long sectionId;
        private final long pageId;
        private final int pageIndex;
        private final int pageCount;
        private final Map<Integer, Long> sectionsByButtonId;
        private String draft;

        private JournalView(UIElement window, UILabel close, UILabel newSection, UILabel previousPage,
                            UILabel nextPage, UILabel newPage, UILabel save, UITextField editor,
                            String characterKey, long sectionId, long pageId, int pageIndex,
                            int pageCount, String draft, Map<Integer, Long> sectionsByButtonId) {
            this.window = window; this.close = close; this.newSection = newSection;
            this.previousPage = previousPage; this.nextPage = nextPage; this.newPage = newPage;
            this.save = save; this.editor = editor; this.characterKey = characterKey;
            this.sectionId = sectionId; this.pageId = pageId; this.pageIndex = pageIndex;
            this.pageCount = pageCount; this.draft = draft; this.sectionsByButtonId = sectionsByButtonId;
        }
        private UIElement window() { return window; }
        private UILabel close() { return close; }
        private UILabel newSection() { return newSection; }
        private UILabel previousPage() { return previousPage; }
        private UILabel nextPage() { return nextPage; }
        private UILabel newPage() { return newPage; }
        private UILabel save() { return save; }
        private UITextField editor() { return editor; }
        private String characterKey() { return characterKey; }
        private long sectionId() { return sectionId; }
        private long pageId() { return pageId; }
        private int pageIndex() { return pageIndex; }
        private int pageCount() { return pageCount; }
        private String draft() { return draft; }
        private void setDraft(String draft) { this.draft = draft; }
        private Map<Integer, Long> sectionsByButtonId() { return sectionsByButtonId; }
    }

    private record CharacterSelectionView(UIElement window,
                                          Map<Integer, CharacterService.CharacterSummary> charactersByButtonId,
                                          Map<Integer, Integer> createSlotsByButtonId,
                                          Map<Integer, CharacterService.CharacterSummary> deleteByButtonId,
                                          UILabel closeButton) {
    }

    private record AppearanceView(UIElement window, UILabel gender, UILabel skinColor,
                                  UILabel hairMinus, UILabel hairValue, UILabel hairPlus,
                                  UILabel beardMinus, UILabel beardValue, UILabel beardPlus,
                                  UILabel hairColor, UILabel eyeColor, UILabel finish, UILabel close) {
    }
}

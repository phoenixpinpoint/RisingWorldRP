package com.example.risingworldstarter;

import com.example.risingworldstarter.autotrim.WindowTrimService;
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
import net.risingworld.api.utils.Utils;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import net.risingworld.api.worldelements.Area3D;
import net.risingworld.api.worldelements.GameObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal entry point for a Rising World (Unity version) plugin.
 */
public final class RisingWorldStarter extends Plugin implements Listener {
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
    private EconomyApi economy;
    private ClaimService claims;
    private ClaimAdminService claimAdmins;
    private ChestService chests;
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

        Path worldDataPath = World.getWorldFolder().toPath().toAbsolutePath().normalize()
                .resolve("plugins").resolve("RisingWorldStarter");
        debug("World-scoped data directory: " + worldDataPath);
        if (!isEnabledForWorld(worldDataPath.resolve("plugin.properties"))) {
            System.out.println("[RisingWorldStarter] Not enabled for world " + World.getName()
                    + ": create " + worldDataPath.resolve("plugin.properties") + " to opt in");
            return;
        }
        prepareWorldDataDirectory(pluginPath, worldDataPath);

        economy = new FileEconomyService(worldDataPath.resolve("balances.properties"));
        debug("Economy balances loaded");
        claims = new ClaimService(worldDataPath.resolve("claims.properties"));
        debug("Land claims loaded");
        claimAdmins = new ClaimAdminService(worldDataPath.resolve("claim-admins.properties"));
        debug("Claim administrators loaded: " + claimAdmins.getAll().size());
        chests = new ChestService(worldDataPath.resolve("chests.properties"));
        debug("Chest ownership and locks loaded");
        characterService = new CharacterService(worldDataPath.resolve("characters"));
        debug("Character service loaded with four slots per account");
        windowTrimService = new WindowTrimService(RisingWorldStarter::debug);
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
        debug("Commands registered: /characters, /syncappearance, /balance, /bal, /store, /admin, /claim, /unclaim, /chunk, /claims, /claimadmin, /chest");
        System.out.println("[RisingWorldStarter] Enabled on Rising World " + getGameVersion());
    }

    private void prepareWorldDataDirectory(Path pluginPath, Path worldDataPath) {
        Path worldFolder = World.getWorldFolder().toPath().toAbsolutePath().normalize();

        try {
            Files.createDirectories(worldDataPath);
            copyLegacyFile(pluginPath.resolve("economy.properties"),
                    worldDataPath.resolve("economy.properties"));
            copyLegacyFile(pluginPath.resolve("marketplace.json"),
                    worldDataPath.resolve("marketplace.json"));
            Path worldMigrationMarker = worldDataPath.resolve("migration.complete");
            if (!Files.exists(worldMigrationMarker)) {
                boolean hasFilesInWorldRoot = Files.exists(worldFolder.resolve("balances.properties"))
                        || Files.exists(worldFolder.resolve("claims.properties"))
                        || Files.isDirectory(worldFolder.resolve("characters"));
                Path legacySource = hasFilesInWorldRoot ? worldFolder : pluginPath;
                Path globalMigrationMarker = pluginPath.resolve("legacy-data-world.txt");
                boolean mayImportGlobalData = hasFilesInWorldRoot || !Files.exists(globalMigrationMarker);
                if (mayImportGlobalData) {
                    copyLegacyFile(legacySource.resolve("balances.properties"),
                        worldDataPath.resolve("balances.properties"));
                    copyLegacyFile(legacySource.resolve("claims.properties"),
                        worldDataPath.resolve("claims.properties"));
                    copyLegacyFile(legacySource.resolve("claim-admins.properties"),
                        worldDataPath.resolve("claim-admins.properties"));
                    copyLegacyDirectory(legacySource.resolve("characters"),
                        worldDataPath.resolve("characters"));
                    if (!hasFilesInWorldRoot) {
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
        System.out.println("[RisingWorldStarter] Disabled");
    }

    /** Returns this plugin's economy API for use by other plugins. */
    public EconomyApi getEconomyApi() {
        if (economy == null) {
            throw new IllegalStateException("Economy is not available before the plugin is enabled");
        }
        return economy;
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
            protect(event, event.getChunkPositionX(), event.getChunkPositionZ());
        }
    }

    @EventMethod
    public void onStorageAccess(PlayerStorageAccessEvent event) {
        ChestOwnership ownership = getOrAssignChest(event.getGlobalID(), event.getChunkPositionX(),
                event.getChunkPositionY(), event.getChunkPositionZ());
        if (ownership == null || !ownership.locked()) return;
        String identity = activeClaimIdentity(event.getPlayer());
        if (ownership.ownerUid().equals(identity)
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
        protect(event, chunkX, chunkZ, true);
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
        protect(event, chunkX, chunkZ, false);
    }

    private void protect(Cancellable event, int chunkX, int chunkZ, boolean requiresOwnedLand) {
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
                            + event.getClass().getSimpleName() + "]");
            return;
        }
        String activeClaimIdentity = activeClaimIdentity(player);
        boolean isOwner = claim.ownerUid().equals(activeClaimIdentity);
        if (isOwner || (isClaimAdmin(player) && claimAdminOverrideEnabled)) return;

        event.setCancelled(true);
        debug("Denied " + event.getClass().getSimpleName() + " for " + player.getName()
                + " in chunk " + chunkX + "," + chunkZ + ": active claim identity="
                + activeClaimIdentity + ", owner=" + claim.ownerUid());
        sendClaimProtectionNotice(player, "This chunk is protected by " + claim.ownerName() + ".");
    }

    private void sendClaimProtectionNotice(Player player, String message) {
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
        String[] parts = event.getCommand().trim().split("\\s+", 3);
        String command = parts[0];
        if (!activeCharacters.containsKey(event.getPlayer().getUID())) {
            event.setCancelled(true);
            event.getPlayer().sendTextMessage("<color=#FFAA66>Select or create a character first.</color>");
            return;
        }
        if (command.equalsIgnoreCase("/characters") || command.equalsIgnoreCase("/character")
                || command.equalsIgnoreCase("/chars")) {
            event.setCancelled(true);
            openCharacterSwitcher(event.getPlayer());
        } else if (command.equalsIgnoreCase("/syncappearance")) {
            event.setCancelled(true);
            syncProfileAppearance(event.getPlayer());
        } else if (command.equalsIgnoreCase("/balance") || command.equalsIgnoreCase("/bal")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            String formattedBalance = formatBalance(economy.getBalance(characterKey(player)));
            player.sendTextMessage("<color=#E8C547>Cash:</color> " + formattedBalance);
            updateBalanceLabel(player);
        } else if (command.equalsIgnoreCase("/store")) {
            event.setCancelled(true);
            toggleStore(event.getPlayer());
        } else if (command.equalsIgnoreCase("/admin")) {
            event.setCancelled(true);
            toggleAdminDashboard(event.getPlayer());
        } else if (command.equalsIgnoreCase("/claim")) {
            event.setCancelled(true);
            claimCurrentChunk(event.getPlayer());
        } else if (command.equalsIgnoreCase("/unclaim")) {
            event.setCancelled(true);
            unclaimCurrentChunk(event.getPlayer());
        } else if (command.equalsIgnoreCase("/chunk")) {
            event.setCancelled(true);
            showCurrentChunk(event.getPlayer(), true);
        } else if (command.equalsIgnoreCase("/claims")) {
            event.setCancelled(true);
            listOwnedChunks(event.getPlayer());
        } else if (command.equalsIgnoreCase("/claimadmin")) {
            event.setCancelled(true);
            handleClaimAdminCommand(event.getPlayer(), parts);
        } else if (command.equalsIgnoreCase("/chest")) {
            event.setCancelled(true);
            handleChestCommand(event.getPlayer(), parts);
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
            boolean ownsChest = ownership.ownerUid().equals(activeClaimIdentity(player));
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
            clearClaimVisuals(player);
            player.sendTextMessage("<color=#AAAAAA>You do not own any chunks.</color>");
            return;
        }

        player.sendTextMessage("<color=#E8C547>Your claimed chunks (" + ownedChunks.size() + "):</color>");
        StringBuilder line = new StringBuilder();
        for (ClaimedChunk chunk : ownedChunks) {
            String coordinate = "[" + chunk.x() + ", " + chunk.z() + "]";
            if (!line.isEmpty() && line.length() + coordinate.length() + 2 > 90) {
                player.sendTextMessage(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(", ");
            }
            line.append(coordinate);
        }
        if (!line.isEmpty()) {
            player.sendTextMessage(line.toString());
        }

        clearClaimVisuals(player);
        float groundY = player.getPosition().y - 0.15f;
        List<Area3D> visuals = new ArrayList<>(ownedChunks.size());
        for (ClaimedChunk chunk : ownedChunks) {
            Area3D visual = createChunkVisual(chunk.x(), chunk.z(), groundY,
                    0.15f, 0.45f, 1.0f, 0.12f,
                    0.30f, 0.65f, 1.0f, 0.95f);
            visuals.add(visual);
            player.addGameObject(visual);
        }
        claimVisuals.put(player.getUID(), visuals);
        visualModes.put(player.getUID(), "claims");
        visualHeights.put(player.getUID(), groundY);
        player.sendTextMessage("<color=#77AAFF>Showing all owned claim squares. Use /claims again to hide them.</color>");
    }

    private void claimCurrentChunk(Player player) {
        Vector3i chunk = player.getChunkPosition();
        Claim existing = claims.getClaim(chunk.x, chunk.z).orElse(null);
        if (existing != null) {
            String owner = existing.ownerUid().equals(characterKey(player)) ? "you" : existing.ownerName();
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
        } else if (claim.ownerUid().equals(characterKey(player))) {
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
        player.showMessageBox(MessageBoxButtons.Yes_No, "Delete character",
                "Permanently delete " + character.name()
                        + "? Their inventory, balance, and claims will also be deleted.",
                -1, selectedButton -> {
                    if (selectedButton == null || selectedButton != 0) return;
                    try {
                        CharacterService.CharacterSummary active = activeCharacters.get(player.getUID());
                        characterService.deleteCharacter(player.getUID(), character);
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
        System.out.println("[RisingWorldStarter] " + administrator.getName() + " kicked " + targetName
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
                    System.out.println("[RisingWorldStarter] " + administrator.getName() + " banned "
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

    private void toggleStore(Player player) {
        if (storeViews.containsKey(player.getUID())) {
            closeStore(player);
        } else {
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

            UILabel itemDetails = new UILabel(storeItem.name() + "\n" + formatBalance(storeItem.price()));
            itemDetails.setPosition(98f, 7f, false);
            itemDetails.setSize(280f, 72f, false);
            itemDetails.setFontSize(18f);
            itemDetails.setFontColor((int) 0xFFFFFFFFL);
            itemDetails.setTextAlign(TextAnchor.MiddleLeft);
            itemRow.addChild(itemDetails);

            UILabel minusButton = createCartQuantityButton("-");
            minusButton.setPosition(402f, 20f, false);
            itemRow.addChild(minusButton);
            view.decrementByButtonId().put(minusButton.getID(), storeItem);

            int quantity = view.cart().containsKey(storeItem.id())
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
            itemRow.addChild(plusButton);
            view.incrementByButtonId().put(plusButton.getID(), storeItem);

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
        System.out.println("[RisingWorldStarter] Running 8-hour payroll for " + players.length
                + " connected player(s) at " + currentPeriod.periodStartHour() + ":00 on "
                + currentPeriod.year() + "-" + currentPeriod.month() + "-" + currentPeriod.day());
        for (Player player : players) {
            if (!activeCharacters.containsKey(player.getUID())) continue;
            String characterKey = characterKey(player);
            economy.createAccount(characterKey, economySettings.defaultBalance());
            long newBalance = economy.deposit(characterKey, salary);
            updateBalanceLabel(player);
            player.sendTextMessage("<color=#77FF99>8-hour salary paid:</color> " + formatBalance(salary));
            System.out.println("[RisingWorldStarter] Paid " + player.getName() + " " + formatBalance(salary)
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
        System.out.println("[RisingWorldStarter/DEBUG] " + message);
    }

    @EventMethod
    public void onStoreClick(PlayerUIElementClickEvent event) {
        Player player = event.getPlayer();
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
        StoreView view = storeViews.get(event.getPlayer().getUID());
        if (view == null || event.getUITextField().getID() != view.searchField().getID()) {
            return;
        }
        String search = event.getNewText() == null ? "" : event.getNewText();
        view.setSearchText(search.trim().toLowerCase(Locale.US));
        rebuildStoreItems(view);
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

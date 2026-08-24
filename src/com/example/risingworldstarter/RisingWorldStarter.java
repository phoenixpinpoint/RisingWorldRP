package com.example.risingworldstarter;

import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.Timer;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.general.SkipNightEvent;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerChangePositionEvent;
import net.risingworld.api.events.player.PlayerDisconnectEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.events.player.ui.PlayerUIElementClickEvent;
import net.risingworld.api.events.player.ui.PlayerUITextFieldChangeEvent;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Item;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIScrollView;
import net.risingworld.api.ui.UITextField;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.ScaleMode;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.utils.Utils;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;
import net.risingworld.api.worldelements.Area3D;
import net.risingworld.api.worldelements.GameObject;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal entry point for a Rising World (Unity version) plugin.
 */
public final class RisingWorldStarter extends Plugin implements Listener {
    private final Map<String, UILabel> balanceLabels = new ConcurrentHashMap<>();
    private final Map<String, UILabel> worldTimeLabels = new ConcurrentHashMap<>();
    private final Map<String, List<Area3D>> claimVisuals = new ConcurrentHashMap<>();
    private final Map<String, String> visualModes = new ConcurrentHashMap<>();
    private final Map<String, Float> visualHeights = new ConcurrentHashMap<>();
    private final Map<String, StoreView> storeViews = new ConcurrentHashMap<>();
    private EconomyApi economy;
    private ClaimService claims;
    private ClaimAdminService claimAdmins;
    private EconomySettings economySettings;
    private StoreCatalog storeCatalog;
    private Timer worldClockTimer;
    private PayPeriod lastSalaryPeriod;

    @Override
    public void onEnable() {
        Path pluginPath = Path.of(getPath()).toAbsolutePath().normalize();
        debug("Starting plugin initialization");
        debug("Plugin data directory: " + pluginPath);

        economy = new FileEconomyService(pluginPath.resolve("balances.properties"));
        debug("Economy balances loaded");
        claims = new ClaimService(pluginPath.resolve("claims.properties"));
        debug("Land claims loaded");
        claimAdmins = new ClaimAdminService(pluginPath.resolve("claim-admins.properties"));
        debug("Claim administrators loaded: " + claimAdmins.getAll().size());

        Path economyConfigPath = pluginPath.resolve("economy.properties");
        economySettings = EconomySettings.load(economyConfigPath);
        debug("Economy config loaded from " + economyConfigPath);
        debug("Economy values: starting cash=" + formatBalance(economySettings.defaultBalance())
                + ", claim cost=" + formatBalance(economySettings.claimCost())
                + ", 8-hour salary=" + formatBalance(economySettings.baseSalary()));

        Path marketplaceConfigPath = pluginPath.resolve("marketplace.json");
        storeCatalog = StoreCatalog.load(marketplaceConfigPath);
        debug("Marketplace config loaded from " + marketplaceConfigPath);
        debug("Marketplace enabled items: " + storeCatalog.items().size());

        net.risingworld.api.objects.Time currentTime = Server.getGameTime();
        lastSalaryPeriod = PayPeriod.from(currentTime);
        debug(String.format(Locale.US, "World clock initialized: %d-%d-%d %02d:%02d",
                currentTime.getYear(), currentTime.getMonth(), currentTime.getDay(),
                currentTime.getHours(), currentTime.getMinutes()));
        registerEventListener(this);
        debug("Event listener registered");
        worldClockTimer = new Timer(1f, 0f, -1, this::updateWorldClockLabels);
        worldClockTimer.start();
        debug("World clock and payroll timer started; payroll runs at 00:00, 08:00, and 16:00");
        debug("Commands registered: /balance, /bal, /store, /claim, /unclaim, /chunk, /claims, /claimadmin");
        System.out.println("[RisingWorldStarter] Enabled on Rising World " + getGameVersion());
    }

    @Override
    public void onDisable() {
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
        System.out.println("[RisingWorldStarter] Disabled");
    }

    /** Returns this plugin's economy API for use by other plugins. */
    public EconomyApi getEconomyApi() {
        if (economy == null) {
            throw new IllegalStateException("Economy is not available before the plugin is enabled");
        }
        return economy;
    }

    @EventMethod
    public void onPlayerSpawn(PlayerSpawnEvent event) {
        economy.createAccount(event.getPlayer().getUID(), economySettings.defaultBalance());
        showBalance(event.getPlayer());
        showWorldClock(event.getPlayer());
    }

    @EventMethod
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        balanceLabels.remove(event.getPlayer().getUID());
        worldTimeLabels.remove(event.getPlayer().getUID());
        claimVisuals.remove(event.getPlayer().getUID());
        visualModes.remove(event.getPlayer().getUID());
        visualHeights.remove(event.getPlayer().getUID());
        storeViews.remove(event.getPlayer().getUID());
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
        if (command.equalsIgnoreCase("/balance") || command.equalsIgnoreCase("/bal")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            String formattedBalance = formatBalance(economy.getBalance(player.getUID()));
            player.sendTextMessage("<color=#E8C547>Cash:</color> " + formattedBalance);
            updateBalanceLabel(player);
        } else if (command.equalsIgnoreCase("/store")) {
            event.setCancelled(true);
            toggleStore(event.getPlayer());
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
        }
    }

    private void listOwnedChunks(Player player) {
        List<ClaimedChunk> ownedChunks = claims.getClaimsByOwner(player.getUID());
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
            String owner = existing.ownerUid().equals(player.getUID()) ? "you" : existing.ownerName();
            player.sendTextMessage("<color=#FF7777>Chunk " + chunk.x + ", " + chunk.z
                    + " is already claimed by " + owner + ".</color>");
            showCurrentChunk(player, false);
            return;
        }

        long claimCost = economySettings.claimCost();
        long balance = economy.getBalance(player.getUID());
        if (balance < claimCost) {
            player.sendTextMessage("<color=#FF7777>Claiming this chunk costs " + formatBalance(claimCost)
                    + ". You only have " + formatBalance(balance) + ".</color>");
            return;
        }

        if (!claims.claim(chunk.x, chunk.z, player.getUID(), player.getName())) {
            player.sendTextMessage("<color=#FF7777>That chunk was claimed before your request completed.</color>");
            return;
        }
        try {
            if (!economy.withdraw(player.getUID(), claimCost)) {
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
        } else if (!existing.ownerUid().equals(player.getUID()) && !isClaimAdmin(player)) {
            player.sendTextMessage("<color=#FF7777>This chunk is claimed by " + existing.ownerName() + ".</color>");
        } else {
            if (existing.ownerUid().equals(player.getUID())) {
                claims.unclaim(chunk.x, chunk.z, player.getUID());
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
        } else if (claim.ownerUid().equals(player.getUID())) {
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

    private void toggleStore(Player player) {
        if (storeViews.containsKey(player.getUID())) {
            closeStore(player);
        } else {
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
        itemList.setMouseWheelScrollSize(92f);
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
        if (!economy.withdraw(player.getUID(), total)) {
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
            economy.deposit(player.getUID(), refund);
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
            label.setText("Cash: " + formatBalance(economy.getBalance(player.getUID())));
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
            economy.createAccount(player.getUID(), economySettings.defaultBalance());
            long newBalance = economy.deposit(player.getUID(), salary);
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
}

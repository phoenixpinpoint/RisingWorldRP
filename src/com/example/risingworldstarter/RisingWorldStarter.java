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
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.Area;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.style.Pivot;
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
    private EconomyApi economy;
    private ClaimService claims;
    private ClaimAdminService claimAdmins;
    private EconomySettings economySettings;
    private Timer worldClockTimer;
    private PayPeriod lastSalaryPeriod;

    @Override
    public void onEnable() {
        economy = new FileEconomyService(Path.of(getPath(), "balances.properties"));
        claims = new ClaimService(Path.of(getPath(), "claims.properties"));
        claimAdmins = new ClaimAdminService(Path.of(getPath(), "claim-admins.properties"));
        economySettings = EconomySettings.load(Path.of(getPath(), "economy.properties"));
        net.risingworld.api.objects.Time currentTime = Server.getGameTime();
        lastSalaryPeriod = PayPeriod.from(currentTime);
        registerEventListener(this);
        worldClockTimer = new Timer(1f, 0f, -1, this::updateWorldClockLabels);
        worldClockTimer.start();
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
}

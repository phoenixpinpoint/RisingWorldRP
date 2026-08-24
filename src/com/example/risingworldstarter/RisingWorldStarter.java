package com.example.risingworldstarter;

import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
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

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal entry point for a Rising World (Unity version) plugin.
 */
public final class RisingWorldStarter extends Plugin implements Listener {
    private final Map<String, UILabel> balanceLabels = new ConcurrentHashMap<>();
    private final Map<String, Area3D> chunkVisuals = new ConcurrentHashMap<>();
    private final Map<String, String> viewedChunks = new ConcurrentHashMap<>();
    private EconomyApi economy;
    private ClaimService claims;
    private ClaimAdminService claimAdmins;

    @Override
    public void onEnable() {
        economy = new FileEconomyService(Path.of(getPath(), "balances.properties"));
        claims = new ClaimService(Path.of(getPath(), "claims.properties"));
        claimAdmins = new ClaimAdminService(Path.of(getPath(), "claim-admins.properties"));
        registerEventListener(this);
        System.out.println("[RisingWorldStarter] Enabled on Rising World " + getGameVersion());
    }

    @Override
    public void onDisable() {
        balanceLabels.clear();
        chunkVisuals.clear();
        viewedChunks.clear();
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
        showBalance(event.getPlayer());
    }

    @EventMethod
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        balanceLabels.remove(event.getPlayer().getUID());
        chunkVisuals.remove(event.getPlayer().getUID());
        viewedChunks.remove(event.getPlayer().getUID());
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
        } else if (command.equalsIgnoreCase("/claimadmin")) {
            event.setCancelled(true);
            handleClaimAdminCommand(event.getPlayer(), parts);
        }
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

        claims.claim(chunk.x, chunk.z, player.getUID(), player.getName());
        player.sendTextMessage("<color=#77FF99>Claimed chunk " + chunk.x + ", " + chunk.z + ".</color>");
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
        Area3D oldVisual = chunkVisuals.remove(player.getUID());
        String oldChunkKey = viewedChunks.remove(player.getUID());
        if (oldVisual != null) {
            player.removeGameObject(oldVisual);
        }
        if (toggle && chunkKey.equals(oldChunkKey)) {
            player.sendTextMessage("<color=#AAAAAA>Chunk highlight hidden.</color>");
            return;
        }

        Claim claim = claims.getClaim(chunk.x, chunk.z).orElse(null);
        String ownerText = claim == null ? "Unclaimed" : "Claimed by " + claim.ownerName();
        player.sendTextMessage("<color=#E8C547>Chunk:</color> " + chunk.x + ", " + chunk.z
                + " - " + ownerText);

        Vector3f start = Utils.ChunkUtils.getGlobalPosition(new Vector3i(chunk.x, 0, chunk.z), Vector3i.ZERO);
        Vector3f end = Utils.ChunkUtils.getGlobalPosition(new Vector3i(chunk.x + 1, 0, chunk.z + 1), Vector3i.ZERO);
        float groundY = player.getPosition().y - 0.15f;
        Area area = new Area(start.x, groundY, start.z, end.x, groundY + 0.3f, end.z);
        Area3D visual = new Area3D(area);
        visual.setAlwaysVisible(true);
        visual.setFrameVisible(true);

        if (claim == null) {
            visual.setColor(0.15f, 0.85f, 0.30f, 0.12f);
            visual.setFrameColor(0.25f, 1.0f, 0.40f, 0.95f);
        } else if (claim.ownerUid().equals(player.getUID())) {
            visual.setColor(0.15f, 0.45f, 1.0f, 0.12f);
            visual.setFrameColor(0.30f, 0.65f, 1.0f, 0.95f);
        } else {
            visual.setColor(1.0f, 0.15f, 0.15f, 0.12f);
            visual.setFrameColor(1.0f, 0.30f, 0.30f, 0.95f);
        }

        chunkVisuals.put(player.getUID(), visual);
        viewedChunks.put(player.getUID(), chunkKey);
        player.addGameObject(visual);
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

    private static String formatBalance(long minorUnits) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        return currency.format(minorUnits / 100.0);
    }
}

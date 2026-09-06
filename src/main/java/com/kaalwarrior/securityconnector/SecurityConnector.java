package com.kaalwarrior.securityconnector;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SecurityConnector extends JavaPlugin {

private LuckPerms luckPerms;

private File securityFolder;
private File playersFile;
private FileConfiguration playersConfig;

private static final String VULCAN_PERMISSION = "vulcan.bypass.*";
private static final String GRIM_PERMISSION = "grim.exempt";
private static final String THEMIS_PERMISSION = "themis.bypass";

@Override
public void onEnable() {

    saveDefaultConfig();

    if (!setupLuckPerms()) {
        getLogger().warning("LuckPerms was not found. Player permissions cannot be applied.");
    }

    createSecurityConnector();

    sendConnectionMessage();

    Bukkit.getPluginManager().registerEvents(
            new PlayerListener(this),
            this
    );

    // Apply permissions to players who are already online.
    for (Player player : Bukkit.getOnlinePlayers()) {
        applyPermissions(player);
    }
}

@Override
public void onDisable() {
    getLogger().info("SecurityConnector disabled.");
}

private boolean setupLuckPerms() {

    RegisteredServiceProvider<LuckPerms> provider =
            Bukkit.getServicesManager().getRegistration(LuckPerms.class);

    if (provider == null) {
        return false;
    }

    luckPerms = provider.getProvider();
    return luckPerms != null;
}

private void createSecurityConnector() {

    boolean vulcan = getConfig().getBoolean("VulcanAC", false);
    boolean grim = getConfig().getBoolean("GrimAC", false);
    boolean themis = getConfig().getBoolean("ThemisAC", false);

    int enabled = 0;

    if (vulcan) enabled++;
    if (grim) enabled++;
    if (themis) enabled++;

    /*
     * The Security Connector folder is only required
     * when two or more options are enabled.
     */
    if (enabled < 2) {
        return;
    }

    securityFolder = new File(
            Bukkit.getPluginsFolder(),
            "Security Connector"
    );

    if (!securityFolder.exists() && !securityFolder.mkdirs()) {
        getLogger().warning("Could not create Security Connector folder.");
        return;
    }

    playersFile = new File(securityFolder, "players.yml");

    if (!playersFile.exists()) {
        try {
            if (!playersFile.createNewFile()) {
                getLogger().warning("Could not create players.yml.");
                return;
            }
        } catch (IOException e) {
            getLogger().warning("Could not create players.yml: " + e.getMessage());
            return;
        }

        playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        playersConfig.set(
                "players",
                new ArrayList<String>()
        );

        playersConfig.options().setHeader(List.of(
                "Security Connector player list.",
                "Enter Minecraft usernames or UUIDs under 'players'.",
                "Example:",
                "players:",
                "  - \"PlayerName\"",
                "  - \"00000000-0000-0000-0000-000000000000\""
        ));

        savePlayersFile();

    } else {
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
    }
}

private void sendConnectionMessage() {

    boolean vulcan = getConfig().getBoolean("VulcanAC", false);
    boolean grim = getConfig().getBoolean("GrimAC", false);
    boolean themis = getConfig().getBoolean("ThemisAC", false);

    List<String> enabled = new ArrayList<>();

    if (vulcan) enabled.add("VulcanAC");
    if (grim) enabled.add("GrimAC");
    if (themis) enabled.add("ThemisAC");

    if (enabled.isEmpty()) {

        getServer().getConsoleSender().sendMessage(
                ChatColor.RED +
                "Failed to Connect plugins because none of the options in (config.yml) is set to true"
        );

    } else if (enabled.size() == 1) {

        getServer().getConsoleSender().sendMessage(
                ChatColor.YELLOW +
                "Unable to connect " +
                enabled.get(0) +
                " because only 1 option is selected"
        );

    } else {

        String message;

        if (enabled.size() == 2) {

            message =
                    "Successfully Connected " +
                    enabled.get(0) +
                    " and " +
                    enabled.get(1);

        } else {

            message =
                    "Successfully Connected " +
                    enabled.get(0) +
                    " and " +
                    enabled.get(1) +
                    " and " +
                    enabled.get(2);
        }

        getServer().getConsoleSender().sendMessage(
                ChatColor.GREEN + message
        );
    }
}

public void applyPermissions(Player player) {

    if (luckPerms == null) {
        return;
    }

    if (playersConfig == null) {
        return;
    }

    List<String> entries =
            playersConfig.getStringList("players");

    if (entries.isEmpty()) {
        return;
    }

    String playerName = player.getName();
    UUID playerUuid = player.getUniqueId();

    boolean listed = false;

    for (String entry : entries) {

        if (entry == null || entry.trim().isEmpty()) {
            continue;
        }

        String value = entry.trim();

        if (value.equalsIgnoreCase(playerName)
                || value.equalsIgnoreCase(playerUuid.toString())) {

            listed = true;
            break;
        }
    }

    if (!listed) {
        return;
    }

    User user = luckPerms.getUserManager().getUser(playerUuid);

    if (user == null) {
        luckPerms.getUserManager().loadUser(playerUuid)
                .thenAccept(thisUser -> {

                    if (thisUser != null) {
                        applyConfiguredPermissions(thisUser);
                    }

                });

        return;
    }

    applyConfiguredPermissions(user);
}

private void applyConfiguredPermissions(User user) {

    boolean vulcan = getConfig().getBoolean("VulcanAC", false);
    boolean grim = getConfig().getBoolean("GrimAC", false);
    boolean themis = getConfig().getBoolean("ThemisAC", false);

    if (vulcan) {
        user.data().add(
                PermissionNode.builder(VULCAN_PERMISSION)
                        .build()
        );
    }

    if (grim) {
        user.data().add(
                PermissionNode.builder(GRIM_PERMISSION)
                        .build()
        );
    }

    if (themis) {
        user.data().add(
                PermissionNode.builder(THEMIS_PERMISSION)
                        .build()
        );
    }

    luckPerms.getUserManager().saveUser(user);
}

private void savePlayersFile() {

    if (playersConfig == null || playersFile == null) {
        return;
    }

    try {
        playersConfig.save(playersFile);
    } catch (IOException e) {
        getLogger().warning(
                "Could not save players.yml: " +
                e.getMessage()
        );
    }
}

public static class PlayerListener
        implements org.bukkit.event.Listener {

    private final SecurityConnector plugin;

    public PlayerListener(SecurityConnector plugin) {
        this.plugin = plugin;
    }

    @org.bukkit.event.EventHandler
    public void onPlayerJoin(
            org.bukkit.event.player.PlayerJoinEvent event) {

        plugin.applyPermissions(event.getPlayer());
    }
}

}

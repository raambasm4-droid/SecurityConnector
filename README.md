# Security Connector

Paper 1.21.11 plugin (Java 21).

## Behavior

On first startup it creates `plugins/SecurityConnector/config.yml` with:

```yaml
VulcanAC: false
GrimAC: false
ThemisAC: false
```

- 0 selected: red console message.
- 1 selected: yellow console message.
- 2 or 3 selected: green console message.
- When 2 or 3 are selected, it creates `plugins/Security Connector/players.yml`.
- Listed players receive the matching LuckPerms nodes when they join (and for already-online players on enable).

Permissions:
- VulcanAC -> `vulcan.bypass.*`
- GrimAC -> `grim.exempt`
- ThemisAC -> `themis.bypass`

Use UUIDs where possible. Player names are also accepted and are checked when the player joins. This is compatible with Geyser at the Bukkit player-name/UUID level; UUIDs are preferred for Bedrock accounts.

The plugin does not use ViaVersion/ViaBackwards/ViaRewind APIs because this feature does not need protocol-specific behavior.

## Build

Requires JDK 21 and Maven. Run:

```text
mvn clean package
```

The jar is `target/SecurityConnector-1.0.0.jar`.

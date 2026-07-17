# CelestCombat

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/celestcombat-pro?logo=modrinth&logoColor=white&label=downloads&labelColor=%23139549&color=%2318c25f)](https://modrinth.com/plugin/celestcombat-pro)

[![Folia](https://img.shields.io/badge/Folia-Supported-brightgreen.svg?logo=papermc&logoColor=white&labelColor=%23139549&color=%2318c25f)](https://github.com/PaperMC/Folia)



A comprehensive combat management plugin for Minecraft servers specializing in PvP environments.

## Features

- Configurable combat tags, command restrictions, flight control, and logout punishment
- Per-world enchanted golden apple, ender pearl, trident, and item restrictions
- Newbie protection from PvP, end crystals, TNT, and optionally mobs
- Optional keep-inventory while newbie protection is active
- Semi keep-inventory for natural deaths while PvP, bosses, dragons, custom mobs, and unknown causes still drop loot
- Per-world semi keep-inventory overrides, including excluded worlds such as `desert`
- Respawn messages explaining why inventory was kept or dropped
- WorldGuard, GriefPrevention, UXM Claims, StrikePractice, and PlaceholderAPI integrations

## Newbie Protection and Semi Keep-Inventory

Newbie protection can independently block player, end-crystal, TNT, and mob
damage. Set `newbie_protection.keep_inventory` to `true` if protected newcomers
should keep their inventory when they die.

Semi keep-inventory classifies each death:

- Configured natural damage such as falling, fire, lava, drowning, and the void keeps inventory.
- Configured vanilla mobs such as zombies, spiders, and skeletons keep inventory.
- Player combat, dragons, bosses, recognized custom mobs, and unknown causes drop inventory.
- After respawning, the player is told why their inventory was kept or dropped.

Enable the feature globally and exclude individual worlds with `false`:

```yaml
semi_keep_inventory:
  enabled: true

  worlds:
    world: true
    world_nether: true
    desert: false

  unknown_causes_keep_inventory: false
```

World names are matched case-insensitively. Worlds missing from `worlds` use
the global `enabled` value. A world can also be enabled explicitly when the
global value is `false`.

Custom mobs are checked before the vanilla-mob allowlist. Add the metadata
keys or scoreboard-tag prefixes used by your custom-mob plugin:

```yaml
semi_keep_inventory:
  custom_mob_detection:
    metadata_keys:
      - MythicMob
    scoreboard_tag_prefixes:
      - "mythicmob:"
      - "custommob:"
```

The complete damage-cause and natural-mob allowlists are documented in
`src/main/resources/config.yml`.

## Requirements

- **Minecraft Version:** 1.21 - 1.21.4
- **Server Software:** Paper, Purpur, Folia
- **Java Version:** 21+

### Optional Dependencies
- **WorldGuard** - For safe zone integration and region protection
- **GriefPrevention** - For claim-based protection systems

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/celestcombat-pro)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin in `plugins/CelestCombat/config.yml`
5. Reload with `/cc reload`

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/cc help` | `celestcombat.command.use` | Display command help |
| `/cc reload` | `celestcombat.command.use` | Reload plugin configuration |
| `/cc tag <player1> [player2]` | `celestcombat.command.use` | Manually tag players in combat |
| `/cc removetag <player/world/all>` | `celestcombat.command.use` | Remove combat tags |

**Aliases:** `/cc`, `/combat`, `/celestcombat`

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `celestcombat.command.use` | OP | Access to all plugin commands |
| `celestcombat.update.notify` | OP | Receive update notifications |
| `celestcombat.bypass.tag` | false | Bypass combat tagging |

## Building

```bash
git clone https://github.com/ptthanh02/CelestCombat.git
cd CelestCombat
./gradlew build
```

The compiled JAR will be available in `build/libs/`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Support

- **Issues & Bug Reports:** [GitHub Issues](https://github.com/ptthanh02/CelestCombat/issues)
- **Discord Community:** [Join our Discord](https://discord.com/invite/FJN7hJKPyb)

## Statistics

[![bStats](https://bstats.org/signatures/bukkit/CelestCombat-Pro/27299.svg)](https://bstats.org/plugin/bukkit/CelestCombat-Pro/27299)

## License

This project is licensed under the CC BY-NC-SA 4.0 License - see the [LICENSE](LICENSE) file for details.

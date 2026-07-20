# MBedwarsArenaPack

An [MBedwars](https://mbedwars.com/) addon that exports arenas — world files, spawner
coordinates, dealer NPC locations, bed locations, team spawns and all arena settings —
to portable `.zip` **arena packs**, and imports them on any server. Packs can also be
hosted in a GitHub repository and installed straight from it.

Requires **MBedwars 5.5.8+**.

## Commands

All commands live under `/bw arenapack`:

| Command | Permission | Description |
|---|---|---|
| `/bw arenapack export <arena> [packVersion]` | `mbedwars.arenapack.export` | Export an arena to `plugins/MBedwarsArenaPack/exports/<arena>.zip` |
| `/bw arenapack import <zipFile> [newArenaName]` | `mbedwars.arenapack.import` | Import a local pack (searched in `imports/`, `exports/` and `cache/downloads/`) |
| `/bw arenapack install <packName> [newArenaName]` | `mbedwars.arenapack.install` | Download a pack from the configured GitHub repo and import it |
| `/bw arenapack list [local]` | `mbedwars.arenapack.list` | List packs available in the repo (or local zips) |

Notes:
- Exporting requires the arena to be **stopped**, and ships the arena's **entire world folder**
  — one world per map is assumed.
- The **lobby location is not exported** (it usually points into a shared lobby world).
  Set one after importing before enabling the arena.
- Imported worlds get a fresh folder name (default `arenapack_<arena>`, see `config.yml`).
- Worlds cannot be loaded on servers **older** than the Minecraft version they were exported on.

## Hosting packs on GitHub

The plugin fetches packs from a plain GitHub repository via `raw.githubusercontent.com`
(no API tokens needed for public repos). Point `config.yml` at your repo:

```yaml
repo:
  slug: "YourName/your-arena-packs"
  branch: "main"
  index-path: "index.json"
```

The repository layout:

```
index.json
packs/
  amazonia.zip
  glacier.zip
```

`index.json` schema (`name`, `version` and `file` are required):

```json
{
  "format": 1,
  "packs": [
    {
      "name": "Amazonia",
      "version": 3,
      "file": "packs/amazonia.zip",
      "description": "4-team jungle map, 2 per team",
      "authors": ["MetallicGoat"],
      "minecraft-version": "1.21.4",
      "min-mbedwars-api": 208
    }
  ]
}
```

### Publishing workflow

1. Build/update the arena on your build server.
2. `/bw arenapack export <arena> [packVersion]`
3. Take the zip from `plugins/MBedwarsArenaPack/exports/` and commit it to the repo under `packs/`.
4. Add or bump the pack's entry in `index.json`.
5. On any server: `/bw arenapack install <name>`.

## Pack format

A pack zip contains:

```
arena.yml    # format-versioned metadata (settings, teams, beds, spawners, dealers, ...)
world/       # the world folder, minus uid.dat, session.lock, playerdata, stats, advancements
```

Locations in `arena.yml` use compact strings: `x;y;z`, `x;y;z;yaw;pitch` (spawns) and
`x;y;z;DIRECTION` (beds). Dealer/upgrade-dealer NPCs are stored by controller type and
recreated through the MBedwars API on import, so they work regardless of world name.

## Building

```
mvn package
```

The jar lands in `target/MBedwarsArenaPack-<version>.jar`. Java 8+, no external
runtime dependencies (Gson ships with the server).

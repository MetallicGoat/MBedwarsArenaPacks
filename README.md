# MBedwarsArenaPacks

An [MBedwars](https://mbedwars.com/) addon that exports arenas — world files, spawner
coordinates, dealer NPC locations, bed locations, team spawns and all arena settings —
to portable **arena packs**, and imports them on any server. Packs can also be hosted in
a GitHub repository and installed straight from it.

A pack is a folder holding an immutable `world.zip` next to a human-editable `arena.json`,
so map data and arena configuration live side by side in git: coordinates and settings show
up as reviewable diffs, while the world blob is written once and never churns.

Requires **MBedwars 5.5.8+**.

## Repo layout

```
Plugin/           the addon's Maven project (pom.xml, src/)
Packs/            the packs themselves, grouped by team count
  index.json      lists the pack directories
  datafix.py      snaps freshly exported coordinates onto a sane grid
  2-Teams/
    Picnic/
      arena.json
      world.zip
  4-Teams/
    Aquarium/
    ...
```

Category folders are named with a hyphen (`4-Teams`, not `4 Teams`) because pack paths go
into a `raw.githubusercontent.com` URL verbatim. You still install a pack by its own name —
`/bw arenapacks install aquarium` — so the category never has to be typed.

The shipped `config.yml` already points at this repo's `Packs/` folder, so a fresh install
can `/bw arenapacks install aquarium` with no configuration.

## Commands

All commands live under `/bw arenapacks`:

| Command | Permission | Description |
|---|---|---|
| `/bw arenapacks export <arena> [packVersion]` | `mbedwars.arenapacks.export` | Export an arena to `plugins/MBedwarsArenaPacks/exports/<N>-Teams/<arena>/` |
| `/bw arenapacks import <packFolder> [newArenaName]` | `mbedwars.arenapacks.import` | Import a local pack by name or path (searched recursively in `imports/`, `exports/` and `cache/downloads/`) |
| `/bw arenapacks install <packName> [newArenaName]` | `mbedwars.arenapacks.install` | Download a pack from the configured GitHub repo and import it |
| `/bw arenapacks list [local]` | `mbedwars.arenapacks.list` | List packs available in the repo (or local pack folders) |

Notes:
- Exporting requires the arena to be **stopped**, and ships the arena's **entire world folder**
  — one world per map is assumed.
- The **lobby location and arena icon are not exported** (the lobby usually points into a
  shared lobby world). Set them after importing before enabling the arena.
- **Team potion effects and addon data** (MBedwars' per-arena persistent storage) are not part
  of a pack either — configure those per server.
- Imported worlds get a fresh folder name (default `arenapacks_<arena>`, see `config.yml`).
- Worlds cannot be loaded on servers **older** than the Minecraft version they were exported on.
- Re-exporting an unchanged world produces a byte-identical `world.zip`, so git records no
  change — only the `arena.json` edits show up.

## Pack format

```
Amazonia/
  arena.json   # format-versioned metadata (settings, teams, beds, spawners, dealers, ...)
  world.zip    # the world folder's contents, minus the entries listed below
```

`world.zip` skips `uid.dat`, `session.lock`, `level.dat_old`, `playerdata`, `stats` and
`advancements`, plus any `.zip` or `.txt` sitting in the world folder root — so world backup
archives and build notes kept next to `level.dat` stay out of the pack. Only the root level is
filtered, so a zipped datapack under `datapacks/` still ships.

`arena.json` is meant to be edited by hand — every location is a plain object:

```json
{
  "format": 1,
  "pack": {
    "name": "Amazonia",
    "version": 3,
    "exported-by": "MBedwarsArenaPacks 1.0.0",
    "exported-at": "2026-07-20T18:32:11Z",
    "minecraft-version": "1.21.4",
    "mbedwars-api-version": 208,
    "original-world-name": "bw_amazonia"
  },
  "arena": {
    "name": "Amazonia",
    "min-players": 4,
    "players-per-team": 2,
    "region": {
      "min": { "x": 10.0, "y": 40.0, "z": 10.0 },
      "max": { "x": 210.0, "y": 120.0, "z": 210.0 }
    },
    "teams": {
      "RED": {
        "spawn": { "x": 20.5, "y": 78.0, "z": 100.5, "yaw": 90.0, "pitch": 0.0 },
        "bed": { "x": 25.0, "y": 78.0, "z": 100.0, "direction": "WEST" }
      }
    },
    "spawners": [{ "type": "iron", "location": { "x": 30.5, "y": 78.0, "z": 100.5 } }],
    "holograms": [
      { "controller": "DEALER", "location": { "x": 22.5, "y": 78.0, "z": 98.5, "yaw": 180.0, "pitch": 0.0 } }
    ]
  }
}
```

The whole file is written by the exporter and is safe to edit afterwards — moving a spawner
is a one-line change. `mbedwars-api-version` records the API the pack was exported on;
`install` refuses packs from a newer MBedwars than the server runs. Dealer/upgrade-dealer NPCs
are stored by controller type and recreated through the MBedwars API on import, so they work
regardless of world name.

## Hosting packs on GitHub

The plugin fetches packs from a plain GitHub repository via `raw.githubusercontent.com`
(no API tokens needed for public repos). Point `config.yml` at your repo:

```yaml
repo:
  slug: "YourName/your-arena-packs"
  branch: "main"
  index-path: "Packs/index.json"
```

The repository layout — each pack is a directory, exactly as exported, optionally grouped
into category folders:

```
Packs/
  index.json
  2-Teams/
    duel/
      arena.json
      world.zip
  4-Teams/
    amazonia/
    glacier/
```

`index.json` lists the pack directories **relative to its own folder**, so a pack collection
can sit anywhere in the repo and grouping is just part of the path. Every detail is read from
the pack's own `arena.json`, so nothing is duplicated and version bumps happen in one place:

```json
{
  "format": 1,
  "packs": [
    "2-Teams/duel",
    "4-Teams/amazonia",
    "4-Teams/glacier"
  ]
}
```

A pack is installed by its directory name (`/bw arenapacks install amazonia`); the category is
only needed if two packs share a name, and the plugin will say so and list the candidates.
Grouping is optional — a flat list of pack directories still works.

### Publishing workflow

1. Build/update the arena on your build server.
2. `/bw arenapacks export <arena> [packVersion]` — lands in
   `exports/<N>-Teams/<arena>/`, already in the layout the repo uses.
3. Copy that `<N>-Teams/<arena>` folder into the repo's `Packs/` and commit it.
4. Run `Packs/datafix.py` to tidy the exported coordinates (see below).
5. Add the path (e.g. `4-Teams/amazonia`) to `Packs/index.json` if it is new.
6. On any server: `/bw arenapacks install <name>`.

Tweaking a coordinate later means editing `arena.json` and committing that one file — the
`world.zip` stays untouched.

### Tidying exported coordinates

An export records exactly where the builder was standing, so spawns arrive as `x: 20.474162`
with `yaw: 271.9496` and `pitch: 8.85`. `Packs/datafix.py` snaps those onto the grid a
hand-written pack would use:

| Field | Rule |
|---|---|
| `x`, `z` | nearest `0.5` — block centre, or the boundary between two blocks |
| `y` | nearest `1.0` — a whole block level (`.5` would sit inside a block) |
| `yaw` | nearest `5`, normalised into `[0, 360)` — in practice `0` / `90` / `180` / `270` |
| `pitch` | `0`; anything more than 10° off level is assumed deliberate and only reported |

It rewrites team spawns, the spectator spawn and hologram (dealer) locations. Bed and spawner
locations and region corners are left alone — MBedwars derives those from block positions, so
they are already exact. `world.zip` is never touched, and the output is byte-identical to what
the exporter itself writes, so re-exporting an unchanged arena still produces no diff.

Needs python3 and nothing else.

```
./Packs/datafix.py                 # fix every pack
./Packs/datafix.py --dry-run       # report only
./Packs/datafix.py Picnic Lectus   # limit to named packs
```

## Building

```
cd Plugin
mvn package
```

The jar lands in `Plugin/target/MBedwarsArenaPacks-<version>.jar`. Java 8+, no external
runtime dependencies (Gson ships with the server).

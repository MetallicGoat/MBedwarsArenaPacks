#!/usr/bin/env python3
"""Normalise the location values in every pack's arena.json.

Coordinates captured from a live server carry the jitter of wherever the builder
happened to be standing: a team spawn at x=20.4749 instead of 20.5, a yaw of
271.9496 instead of 270, a pitch of 8.85 instead of 0. The values are
functionally fine but they read badly in a diff and hide real edits, so this
snaps them to the grid a hand-written pack would use:

    x, z    -> nearest 0.5   (block centre, or the boundary between two blocks)
    y       -> nearest 1.0   (a whole block level; .5 would sit inside a block)
    yaw     -> nearest 5, normalised into [0, 360)  -> 0 / 90 / 180 / 270
    pitch   -> 0             (level gaze; see PITCH_SNAP_LIMIT)

Applied to team spawns, the spectator spawn, the lobby and hologram (dealer NPC)
locations. Bed locations, spawner locations and region corners are left alone:
MBedwars derives those from block positions, so they are already exact.

world.zip files are never touched. Output formatting matches
PackMetaCodec.write exactly, so tidying a pack does not create a spurious diff
the next time the arena is re-exported.
"""

import argparse
import json
import math
import sys
from pathlib import Path

META_NAME = "arena.json"

# A pitch further off level than this is assumed to be deliberate: it is left
# as-is and reported, rather than silently flattened.
PITCH_SNAP_LIMIT = 10.0

PACKS_DIR = Path(__file__).resolve().parent


def snap(value, step):
    """Round half-up to the nearest multiple of step, avoiding -0.0."""
    snapped = math.floor(value / step + 0.5) * step

    return snapped if snapped != 0 else 0.0


def fix_position(loc, changes, where):
    for axis, step in (("x", 0.5), ("y", 1.0), ("z", 0.5)):
        if axis not in loc:
            continue

        before = float(loc[axis])
        after = snap(before, step)

        if before != after:
            loc[axis] = after
            changes.append(f"{where} {axis}: {before:g} -> {after:g}")


def fix_facing(loc, changes, warnings, where):
    if "yaw" in loc:
        before = float(loc["yaw"])
        after = snap(before, 5.0) % 360.0
        after = after if after != 0 else 0.0

        if before != after:
            loc["yaw"] = after
            changes.append(f"{where} yaw: {before:g} -> {after:g}")

    if "pitch" in loc:
        before = float(loc["pitch"])

        if abs(before) > PITCH_SNAP_LIMIT:
            warnings.append(
                f"{where} pitch {before:g} is more than {PITCH_SNAP_LIMIT:g} off level - left as-is")
        elif before != 0.0:
            loc["pitch"] = 0.0
            changes.append(f"{where} pitch: {before:g} -> 0")


def fix_pack(meta_file, dry_run):
    """Returns (changes, warnings), or (None, warnings) if the file is unusable."""
    with meta_file.open(encoding="utf-8") as handle:
        data = json.load(handle)

    arena = data.get("arena")

    if not isinstance(arena, dict):
        return None, [f"{meta_file} has no 'arena' section - skipped"]

    changes = []
    warnings = []

    for team, team_data in (arena.get("teams") or {}).items():
        spawn = (team_data or {}).get("spawn")

        if isinstance(spawn, dict):
            fix_position(spawn, changes, f"team {team} spawn")
            fix_facing(spawn, changes, warnings, f"team {team} spawn")

    for key, label in (("spectator-spawn", "spectator spawn"), ("lobby", "lobby")):
        location = arena.get(key)

        if isinstance(location, dict):
            fix_position(location, changes, label)
            fix_facing(location, changes, warnings, label)

    for index, hologram in enumerate(arena.get("holograms") or []):
        location = (hologram or {}).get("location")

        if isinstance(location, dict):
            label = f"hologram #{index + 1} ({hologram.get('controller', '?')})"
            fix_position(location, changes, label)
            fix_facing(location, changes, warnings, label)

    if changes and not dry_run:
        # Matches PackMetaCodec.write: 2-space indent, literal non-ASCII,
        # trailing newline. Key order survives because json preserves it.
        with meta_file.open("w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=2, ensure_ascii=False)
            handle.write("\n")

    return changes, warnings


def find_packs(requested):
    """Pack paths relative to PACKS_DIR, e.g. '4-Teams/Aquarium'.

    Packs are grouped into category folders, so this recurses rather than
    listing PACKS_DIR's immediate children.
    """
    names = sorted(
        meta.parent.relative_to(PACKS_DIR).as_posix()
        for meta in PACKS_DIR.rglob(META_NAME)
    )

    if requested:
        # A pack may be named by its full path or just its folder name
        selected = []
        missing = []

        for want in requested:
            hits = [name for name in names
                    if name == want or name.rsplit("/", 1)[-1] == want]

            if hits:
                selected.extend(hits)
            else:
                missing.append(want)

        if missing:
            sys.exit(f"No such pack(s): {', '.join(missing)}")

        names = [name for name in names if name in selected]

    if not names:
        sys.exit(f"No packs found in {PACKS_DIR}")

    return names


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("packs", nargs="*", metavar="PACK",
                        help="limit to the named packs (default: all of them)")
    parser.add_argument("-n", "--dry-run", action="store_true",
                        help="report what would change, write nothing")
    args = parser.parse_args()

    total_changes = 0
    total_warnings = 0
    touched = 0

    for name in find_packs(args.packs):
        changes, warnings = fix_pack(PACKS_DIR / name / META_NAME, args.dry_run)

        if changes is None:
            for warning in warnings:
                print(f"  ! {warning}")

            total_warnings += len(warnings)
            continue

        if not changes and not warnings:
            print(f"{name}: already normalised")
            continue

        print(f"{name}: {len(changes)} value(s) {'to fix' if args.dry_run else 'fixed'}")

        for change in changes:
            print(f"    {change}")
        for warning in warnings:
            print(f"  ! {warning}")

        total_changes += len(changes)
        total_warnings += len(warnings)

        if changes:
            touched += 1

    print()
    print(f"{total_changes} value(s) across {touched} pack(s)"
          + (" would be changed (dry run)" if args.dry_run else " changed")
          + (f", {total_warnings} warning(s)" if total_warnings else ""))


if __name__ == "__main__":
    main()

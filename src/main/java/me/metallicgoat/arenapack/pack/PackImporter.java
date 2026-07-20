package me.metallicgoat.arenapack.pack;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.ArenaStatus;
import de.marcely.bedwars.api.arena.ArenaTimeType;
import de.marcely.bedwars.api.arena.ArenaWeatherType;
import de.marcely.bedwars.api.arena.RegenerationType;
import de.marcely.bedwars.api.arena.Team;
import de.marcely.bedwars.api.exception.ArenaBuildException;
import de.marcely.bedwars.api.game.spawner.DropType;
import de.marcely.bedwars.api.world.WorldStorage;
import de.marcely.bedwars.api.world.hologram.HologramControllerType;
import de.marcely.bedwars.api.world.hologram.HologramEntity;
import java.io.File;
import java.io.StringReader;
import java.util.Map;
import java.util.UUID;
import me.metallicgoat.arenapack.ArenaPackPlugin;
import me.metallicgoat.arenapack.config.MainConfig;
import me.metallicgoat.arenapack.util.Console;
import me.metallicgoat.arenapack.util.WorldFiles;
import me.metallicgoat.arenapack.util.ZipUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

public class PackImporter {

  /**
   * Imports a pack zip: unpacks it, installs the world under a new name,
   * creates the arena and applies all metadata. Call from the main thread;
   * heavy IO runs async and the outcome is reported to {@code sender}.
   */
  public static void importPack(CommandSender sender, File zipFile, @Nullable String overrideName) {
    if (!OperationLock.tryAcquire()) {
      sender.sendMessage("§cAnother arena pack operation is already running. Try again in a moment.");
      return;
    }

    if (!zipFile.isFile()) {
      OperationLock.release();
      sender.sendMessage("§cFile not found: " + zipFile.getPath());
      return;
    }

    final ArenaPackPlugin plugin = ArenaPackPlugin.getInstance();
    final File tmpDir = new File(plugin.getTmpImportFolder(), UUID.randomUUID().toString());

    sender.sendMessage("§7Unpacking " + zipFile.getName() + "...");

    // Step 1 (async): unzip + read metadata
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      final PackMeta meta;

      try {
        ZipUtil.unzip(zipFile, tmpDir);

        meta = PackMetaCodec.read(new File(tmpDir, PackMetaCodec.META_FILE_NAME));

        if (!new File(tmpDir, PackMetaCodec.WORLD_DIR_NAME).isDirectory())
          throw new IllegalStateException("Pack contains no '" + PackMetaCodec.WORLD_DIR_NAME + "' folder");
      } catch (Exception e) {
        fail(sender, tmpDir, "Invalid pack: " + e.getMessage(), e);
        return;
      }

      // Step 2 (sync): validate names against live server state
      Bukkit.getScheduler().runTask(plugin, () -> {
        final String arenaName = overrideName != null ? overrideName : meta.arenaName;

        if (!GameAPI.get().isArenaNameValid(arenaName)) {
          fail(sender, tmpDir, "'" + arenaName + "' is not a valid arena name.", null);
          return;
        }

        if (GameAPI.get().getArenaByExactName(arenaName) != null) {
          fail(sender, tmpDir, "An arena named '" + arenaName + "' already exists."
              + " Import under a different name: /bw arenapack import " + zipFile.getName() + " <newName>", null);
          return;
        }

        final String worldName = buildWorldName(arenaName);
        final File worldTarget = new File(Bukkit.getWorldContainer(), worldName);

        if (Bukkit.getWorld(worldName) != null || worldTarget.exists()) {
          fail(sender, tmpDir, "World '" + worldName + "' already exists. Remove it or import under a different arena name.", null);
          return;
        }

        sender.sendMessage("§7Installing world '" + worldName + "'...");

        // Step 3 (async): move the world folder into the server's world container
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
          try {
            WorldFiles.moveDirectory(new File(tmpDir, PackMetaCodec.WORLD_DIR_NAME), worldTarget);

            // Defensive: ensure a fresh world identity on this server
            new File(worldTarget, "uid.dat").delete();
            new File(worldTarget, "session.lock").delete();
          } catch (Exception e) {
            WorldFiles.deleteDirectory(worldTarget);
            fail(sender, tmpDir, "Failed to install the world folder: " + e.getMessage(), e);
            return;
          }

          // Step 4 (sync): load world, build arena, apply metadata
          Bukkit.getScheduler().runTask(plugin, () ->
              buildArena(sender, meta, arenaName, worldName, worldTarget, tmpDir));
        });
      });
    });
  }

  private static void buildArena(CommandSender sender, PackMeta meta, String arenaName,
                                 String worldName, File worldTarget, File tmpDir) {
    final ArenaPackPlugin plugin = ArenaPackPlugin.getInstance();
    World world = null;

    try {
      world = new WorldCreator(worldName).createWorld();

      if (world == null)
        throw new IllegalStateException("Bukkit failed to load world '" + worldName + "'");

      world.setAutoSave(true);

      final RegenerationType regenType = resolveRegenType(sender, meta.regenTypeId);
      final Arena arena;

      try {
        arena = GameAPI.get().createArena()
            .setName(arenaName)
            .setWorld(world)
            .setLocation1(meta.regionMin)
            .setLocation2(meta.regionMax)
            .setRegenerationType(regenType)
            .finish();
      } catch (ArenaBuildException e) {
        throw new IllegalStateException("Failed to create the arena: " + e.getMessage(), e);
      }

      applyMeta(sender, arena, meta);
      spawnHolograms(sender, world, meta);

      arena.setStatus(ArenaStatus.STOPPED);

      if (regenType == RegenerationType.REGION || regenType == RegenerationType.VOTING) {
        sender.sendMessage("§7Saving regeneration snapshot...");

        arena.runRegenerationBlocksSavingProcess(success -> {
          if (Boolean.TRUE.equals(success))
            sender.sendMessage("§aRegeneration snapshot saved.");
          else
            sender.sendMessage("§cSaving the regeneration snapshot failed! Run '/bw arena regenblocks " + arenaName + "' manually.");
        });
      }

      arena.saveNow();

      OperationLock.release();
      WorldFiles.deleteDirectoryAsync(tmpDir);

      sender.sendMessage("§aImported arena '" + arenaName + "' (world: " + worldName + ").");
      sender.sendMessage("§eThe lobby location is not part of packs - set one with the MBedwars setup GUI or '/bw arena set lobby " + arenaName + "' before enabling the arena.");
    } catch (Exception e) {
      // Fatal failure: undo the world install so the import leaves no traces
      if (world != null)
        Bukkit.unloadWorld(world, false);

      WorldFiles.deleteDirectoryAsync(worldTarget);

      final Arena halfBuilt = GameAPI.get().getArenaByExactName(arenaName);

      if (halfBuilt != null)
        halfBuilt.remove();

      fail(sender, tmpDir, "Import failed: " + e.getMessage(), e);
    }
  }

  /** Applies all optional metadata. Per-item problems warn and continue. */
  private static void applyMeta(CommandSender sender, Arena arena, PackMeta meta) {
    for (String author : meta.authors)
      arena.addAuthor(author);

    if (meta.minPlayers > 0)
      arena.setMinPlayers(meta.minPlayers);
    if (meta.playersPerTeam > 0)
      arena.setPlayersPerTeam(meta.playersPerTeam);

    if (meta.customName != null && !meta.customName.isEmpty()) {
      arena.setCustomName(meta.customName);
      arena.setCustomNameEnabled(meta.customNameEnabled);
    }

    if (meta.icon != null) {
      try {
        arena.setIcon(meta.icon);
      } catch (Exception e) {
        warn(sender, "Failed to apply the arena icon (incompatible item?): " + e.getMessage());
      }
    }

    if (meta.weatherType != null)
      arena.setWeatherType(parseEnum(ArenaWeatherType.class, meta.weatherType, ArenaWeatherType.UNTOUCHED, sender, "weather type"));
    if (meta.timeType != null)
      arena.setTimeType(parseEnum(ArenaTimeType.class, meta.timeType, ArenaTimeType.UNTOUCHED, sender, "time type"));

    if (meta.spectatorSpawn != null)
      arena.setSpectatorSpawn(meta.spectatorSpawn);

    for (Map.Entry<String, PackMeta.TeamData> entry : meta.teams.entrySet()) {
      final Team team;

      try {
        team = Team.valueOf(entry.getKey());
      } catch (IllegalArgumentException e) {
        warn(sender, "Skipping unknown team '" + entry.getKey() + "'");
        continue;
      }

      final PackMeta.TeamData data = entry.getValue();

      arena.setTeamEnabled(team, true);

      if (data.spawn != null)
        arena.setTeamSpawn(team, data.spawn);
      if (data.bed != null)
        arena.setBedLocation(team, data.bed);

      applyEffects(sender, arena, team, data.baseOnlyEffects, true);
      applyEffects(sender, arena, team, data.permanentEffects, false);
    }

    for (PackMeta.SpawnerData spawner : meta.spawners) {
      final DropType dropType = GameAPI.get().getDropTypeById(spawner.dropTypeId);

      if (dropType == null) {
        warn(sender, "Skipping spawner at " + spawner.location + ": drop type '" + spawner.dropTypeId
            + "' does not exist on this server");
        continue;
      }

      arena.addSpawner(spawner.location, dropType);
    }

    if (meta.persistentStorageDump != null && !meta.persistentStorageDump.isEmpty()) {
      try {
        arena.getPersistentStorage().deserialize(new StringReader(meta.persistentStorageDump));
      } catch (Exception e) {
        warn(sender, "Failed to restore the arena's addon data (persistent storage): " + e.getMessage());
      }
    }
  }

  private static void applyEffects(CommandSender sender, Arena arena, Team team,
                                   Iterable<PackMeta.EffectData> effects, boolean baseOnly) {
    for (PackMeta.EffectData effect : effects) {
      final PotionEffectType type = PotionEffectType.getByName(effect.type);

      if (type == null) {
        warn(sender, "Skipping unknown potion effect '" + effect.type + "' for team " + team.name());
        continue;
      }

      arena.addTeamEffect(team, baseOnly, type, effect.amplifier);
    }
  }

  private static void spawnHolograms(CommandSender sender, World world, PackMeta meta) {
    final WorldStorage storage = BedwarsAPI.getWorldStorage(world);

    for (PackMeta.HologramData data : meta.holograms) {
      final HologramControllerType type;

      try {
        type = HologramControllerType.valueOf(data.controllerType);
      } catch (IllegalArgumentException e) {
        warn(sender, "Skipping unknown hologram type '" + data.controllerType + "'");
        continue;
      }

      final HologramEntity hologram = storage.spawnHologram(type, data.location.toLocation(world));

      hologram.setPersistent(true);
    }
  }

  private static RegenerationType resolveRegenType(CommandSender sender, @Nullable String id) {
    final RegenerationType type = id != null ? RegenerationType.fromId(id) : null;

    if (type == null) {
      warn(sender, "Unknown regeneration type '" + id + "', falling back to REGION");
      return RegenerationType.REGION;
    }

    return type;
  }

  private static <T extends Enum<T>> T parseEnum(Class<T> clazz, String name, T fallback,
                                                 CommandSender sender, String what) {
    try {
      return Enum.valueOf(clazz, name);
    } catch (IllegalArgumentException e) {
      warn(sender, "Unknown " + what + " '" + name + "', falling back to " + fallback.name());
      return fallback;
    }
  }

  private static String buildWorldName(String arenaName) {
    final String cleaned = arenaName.toLowerCase().replaceAll("[^a-z0-9-_]", "_");

    return MainConfig.world_name_format.replace("{arena}", cleaned);
  }

  private static void warn(CommandSender sender, String message) {
    sender.sendMessage("§e" + message);
    Console.printWarn(message);
  }

  private static void fail(CommandSender sender, File tmpDir, String message, @Nullable Throwable cause) {
    OperationLock.release();
    WorldFiles.deleteDirectoryAsync(tmpDir);

    if (cause != null) {
      Console.printError(message);
      cause.printStackTrace();
    }

    if (Bukkit.isPrimaryThread()) {
      sender.sendMessage("§c" + message);
    } else {
      Bukkit.getScheduler().runTask(ArenaPackPlugin.getInstance(),
          () -> sender.sendMessage("§c" + message));
    }
  }
}

package me.metallicgoat.arenapacks.pack;

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
import java.util.Map;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.config.MainConfig;
import me.metallicgoat.arenapacks.util.Console;
import me.metallicgoat.arenapacks.util.WorldFiles;
import me.metallicgoat.arenapacks.util.ZipUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

public class PackImporter {

  /**
   * Imports a pack folder (arena.json + world.zip): unpacks the world under a
   * new name, creates the arena and applies all metadata. Call from the main
   * thread; heavy IO runs async and the outcome is reported to {@code sender}.
   * <p>
   * The pack folder itself is only ever read, never modified.
   */
  public static void importPack(CommandSender sender, File packDir, @Nullable String overrideName) {
    if (!OperationLock.tryAcquire()) {
      sender.sendMessage("§cAnother arena pack operation is already running. Try again in a moment.");
      return;
    }

    if (!packDir.isDirectory()) {
      OperationLock.release();
      sender.sendMessage("§cPack folder not found: " + packDir.getPath());
      return;
    }

    final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();
    final File worldZip = new File(packDir, PackMetaCodec.WORLD_ZIP_NAME);

    sender.sendMessage("§7Reading " + packDir.getName() + "/" + PackMetaCodec.META_FILE_NAME + "...");

    // Step 1 (async): read the metadata
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      final PackMeta meta;

      try {
        meta = PackMetaCodec.read(new File(packDir, PackMetaCodec.META_FILE_NAME));

        if (!worldZip.isFile())
          throw new InvalidPackException("Pack is missing its " + PackMetaCodec.WORLD_ZIP_NAME);
      } catch (Exception e) {
        fail(sender, "Invalid pack: " + e.getMessage(), e);
        return;
      }

      // Step 2 (sync): validate names against live server state
      Bukkit.getScheduler().runTask(plugin, () -> {
        final String arenaName = overrideName != null ? overrideName : meta.arenaName;

        if (!GameAPI.get().isArenaNameValid(arenaName)) {
          fail(sender, "'" + arenaName + "' is not a valid arena name.", null);
          return;
        }

        if (GameAPI.get().getArenaByExactName(arenaName) != null) {
          fail(sender, "An arena named '" + arenaName + "' already exists."
              + " Import under a different name: /bw arenapacks import " + packDir.getName() + " <newName>", null);
          return;
        }

        final String worldName = buildWorldName(arenaName);
        final File worldTarget = new File(Bukkit.getWorldContainer(), worldName);

        if (Bukkit.getWorld(worldName) != null || worldTarget.exists()) {
          fail(sender, "World '" + worldName + "' already exists. Remove it or import under a different arena name.", null);
          return;
        }

        sender.sendMessage("§7Installing world '" + worldName + "'...");

        // Step 3 (async): unpack the world straight into the server's world container
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
          try {
            ZipUtil.unzip(worldZip, worldTarget);

            // Defensive: ensure a fresh world identity on this server
            new File(worldTarget, "uid.dat").delete();
            new File(worldTarget, "session.lock").delete();
          } catch (Exception e) {
            WorldFiles.deleteDirectory(worldTarget);
            fail(sender, "Failed to install the world folder: " + e.getMessage(), e);
            return;
          }

          // Step 4 (sync): load world, build arena, apply metadata
          Bukkit.getScheduler().runTask(plugin, () ->
              buildArena(sender, meta, arenaName, worldName, worldTarget));
        });
      });
    });
  }

  private static void buildArena(CommandSender sender, PackMeta meta, String arenaName,
                                 String worldName, File worldTarget) {
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

      applyMeta(sender, arena, world, meta);
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

      sender.sendMessage("§aImported arena '" + arenaName + "' (world: " + worldName + ").");

      if (meta.lobby == null)
        sender.sendMessage("§eThis pack has no lobby location - set one with '/bw arena set lobby " + arenaName + "' before enabling the arena.");

      sender.sendMessage("§eThe arena icon is not part of packs - set one with the MBedwars setup GUI.");
    } catch (Exception e) {
      // Fatal failure: undo the world install so the import leaves no traces
      if (world != null)
        Bukkit.unloadWorld(world, false);

      WorldFiles.deleteDirectoryAsync(worldTarget);

      final Arena halfBuilt = GameAPI.get().getArenaByExactName(arenaName);

      if (halfBuilt != null)
        halfBuilt.remove();

      fail(sender, "Import failed: " + e.getMessage(), e);
    }
  }

  /** Applies all optional metadata. Per-item problems warn and continue. */
  private static void applyMeta(CommandSender sender, Arena arena, World world, PackMeta meta) {
    for (String author : meta.authors)
      arena.addAuthor(author);

    if (meta.minPlayers > 0)
      arena.setMinPlayers(meta.minPlayers);
    if (meta.playersPerTeam > 0)
      arena.setPlayersPerTeam(meta.playersPerTeam);

    // Always set one: a fresh arena would otherwise keep MBedwars' "Nameless Arena".
    // Falls back to the arena's own name, which is the override when imported under one.
    arena.setCustomName(PackMeta.customNameOr(meta.customName, arena.getName()));
    arena.setCustomNameEnabled(meta.customNameEnabled);

    if (meta.weatherType != null)
      arena.setWeatherType(parseEnum(ArenaWeatherType.class, meta.weatherType, ArenaWeatherType.UNTOUCHED, sender, "weather type"));
    if (meta.timeType != null)
      arena.setTimeType(parseEnum(ArenaTimeType.class, meta.timeType, ArenaTimeType.UNTOUCHED, sender, "time type"));

    if (meta.spectatorSpawn != null)
      arena.setSpectatorSpawn(meta.spectatorSpawn);

    // Stored world-less, so it always lands in the freshly imported world
    if (meta.lobby != null)
      arena.setLobbyLocation(meta.lobby.toLocation(world));

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

  private static void fail(CommandSender sender, String message, @Nullable Throwable cause) {
    OperationLock.release();

    if (cause != null) {
      Console.printError(message);
      cause.printStackTrace();
    }

    if (Bukkit.isPrimaryThread()) {
      sender.sendMessage("§c" + message);
    } else {
      Bukkit.getScheduler().runTask(ArenaPacksPlugin.getInstance(),
          () -> sender.sendMessage("§c" + message));
    }
  }
}

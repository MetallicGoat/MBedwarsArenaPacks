package me.metallicgoat.arenapacks.pack;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.ArenaBuilder;
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
import java.util.function.Consumer;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.config.MainConfig;
import me.metallicgoat.arenapacks.util.Console;
import me.metallicgoat.arenapacks.util.MinecraftVersions;
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
    importPack(sender, packDir, overrideName, null, null);
  }

  /**
   * Same as {@link #importPack(CommandSender, File, String)}, but reports the
   * outcome to {@code onDone} on the main thread once the import has finished
   * or failed - used to chain several imports behind the {@link OperationLock}.
   * <p>
   * {@code regenTypeOverride} replaces the pack's own regeneration type. As a
   * {@link RegenerationType#WORLD} arena spans the whole world, the pack's
   * region corners are ignored for it.
   */
  public static void importPack(CommandSender sender, File packDir, @Nullable String overrideName,
                                @Nullable RegenerationType regenTypeOverride, @Nullable Consumer<Boolean> onDone) {
    final Consumer<Boolean> done = onDone != null ? onDone : success -> { };

    if (!OperationLock.tryAcquire()) {
      Console.send(sender, "§cAnother arena pack operation is already running. Try again in a moment.");
      done.accept(false);
      return;
    }

    if (!packDir.isDirectory()) {
      OperationLock.release();
      Console.send(sender, "§cPack folder not found: " + packDir.getPath());
      done.accept(false);
      return;
    }

    final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();
    final File worldZip = new File(packDir, PackMetaCodec.WORLD_ZIP_NAME);

    Console.send(sender, "§7Reading " + packDir.getName() + "/" + PackMetaCodec.META_FILE_NAME + "...");

    // Step 1 (async): read the metadata
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      final PackMeta meta;

      try {
        meta = PackMetaCodec.read(new File(packDir, PackMetaCodec.META_FILE_NAME));

        if (!worldZip.isFile())
          throw new InvalidPackException("Pack is missing its " + PackMetaCodec.WORLD_ZIP_NAME);
      } catch (Exception e) {
        fail(sender, "Invalid pack: " + e.getMessage(), e, done);
        return;
      }

      // Step 2 (sync): validate names against live server state
      Bukkit.getScheduler().runTask(plugin, () -> {
        // local packs skip PackInstaller, so they need the same checks before the world is touched
        if (meta.mbedwarsApiVersion > BedwarsAPI.getAPIVersion()) {
          fail(sender, "This pack was exported on a newer MBedwars version (API " + meta.mbedwarsApiVersion
              + ", installed: " + BedwarsAPI.getAPIVersion() + ").", null, done);
          return;
        }

        if (MinecraftVersions.isNewerThan(meta.minecraftVersion, MinecraftVersions.server())) {
          fail(sender, "This pack's world was saved on Minecraft " + meta.minecraftVersion + ", but this server runs "
              + MinecraftVersions.server() + ". Worlds can't be loaded on older versions.", null, done);
          return;
        }

        final String arenaName = overrideName != null ? overrideName : meta.arenaName;

        if (!GameAPI.get().isArenaNameValid(arenaName)) {
          fail(sender, "'" + arenaName + "' is not a valid arena name.", null, done);
          return;
        }

        if (GameAPI.get().getArenaByExactName(arenaName) != null) {
          fail(sender, "An arena named '" + arenaName + "' already exists."
              + " Import under a different name: /bw arenapacks import " + packDir.getName() + " <newName>", null, done);
          return;
        }

        final String worldName = buildWorldName(arenaName);
        final File worldTarget = new File(Bukkit.getWorldContainer(), worldName);

        if (Bukkit.getWorld(worldName) != null || worldTarget.exists()) {
          fail(sender, "World '" + worldName + "' already exists. Remove it or import under a different arena name.", null, done);
          return;
        }

        Console.send(sender, "§7Installing world '" + worldName + "'...");

        // Step 3 (async): unpack the world straight into the server's world container
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
          try {
            ZipUtil.unzip(worldZip, worldTarget);

            // Defensive: ensure a fresh world identity on this server
            new File(worldTarget, "uid.dat").delete();
            new File(worldTarget, "session.lock").delete();
          } catch (Exception e) {
            WorldFiles.deleteDirectory(worldTarget);
            fail(sender, "Failed to install the world folder: " + e.getMessage(), e, done);
            return;
          }

          // Step 4 (sync): load world, build arena, apply metadata
          Bukkit.getScheduler().runTask(plugin, () ->
              buildArena(sender, meta, arenaName, worldName, worldTarget, regenTypeOverride, done));
        });
      });
    });
  }

  private static void buildArena(CommandSender sender, PackMeta meta, String arenaName,
                                 String worldName, File worldTarget, @Nullable RegenerationType regenTypeOverride,
                                 Consumer<Boolean> done) {
    World world = null;
    final Arena arena;
    final RegenerationType regenType;

    try {
      world = new WorldCreator(worldName).createWorld();

      if (world == null)
        throw new IllegalStateException("Bukkit failed to load world '" + worldName + "'");

      world.setAutoSave(true);

      regenType = regenTypeOverride != null
          ? regenTypeOverride
          : resolveRegenType(sender, meta.regenTypeId);

      try {
        final ArenaBuilder builder = GameAPI.get().createArena()
            .setName(arenaName)
            .setWorld(world)
            .setRegenerationType(regenType);

        // a world arena covers the whole world, so the pack's region corners don't apply
        if (regenType != RegenerationType.WORLD) {
          builder.setLocation1(meta.regionMin)
              .setLocation2(meta.regionMax);
        }

        arena = builder.finish();
      } catch (ArenaBuildException e) {
        throw new IllegalStateException("Failed to create the arena: " + e.getMessage(), e);
      }

      applyMeta(sender, arena, world, meta);
      spawnHolograms(sender, world, meta);

      arena.setStatus(ArenaStatus.STOPPED);
      arena.saveNow();
    } catch (Exception e) {
      // Fatal failure: undo the world install so the import leaves no traces
      if (world != null)
        Bukkit.unloadWorld(world, false);

      WorldFiles.deleteDirectoryAsync(worldTarget);

      final Arena halfBuilt = GameAPI.get().getArenaByExactName(arenaName);

      if (halfBuilt != null)
        halfBuilt.remove();

      fail(sender, "Import failed: " + e.getMessage(), e, done);
      return;
    }

    // Outside the try: nothing past this point may roll back the created arena
    final Runnable finish = () -> {
      OperationLock.release();

      Console.send(sender, "§aImported arena '" + arenaName + "' (world: " + worldName + ").");

      if (meta.lobby == null)
        Console.send(sender, "§eThis pack has no lobby location - set one with '/bw arena set lobby " + arenaName + "' before enabling the arena.");

      done.accept(true);
    };

    if (regenType != RegenerationType.REGION && regenType != RegenerationType.VOTING) {
      finish.run();
      return;
    }

    // The import only counts as done once the snapshot is saved: this keeps the lock held
    // (so batch installs don't start the next map meanwhile) and the "Imported" message honest
    Console.send(sender, "§7Saving regeneration snapshot for '" + arenaName + "', this may take a moment...");

    arena.runRegenerationBlocksSavingProcess(success -> {
      final Runnable onSaved = () -> {
        if (!Boolean.TRUE.equals(success))
          Console.send(sender, "§cSaving the regeneration snapshot failed! Run '/bw arena regenblocks " + arenaName + "' manually.");

        finish.run();
      };

      // the callback may be called from another thread
      if (Bukkit.isPrimaryThread())
        onSaved.run();
      else
        Bukkit.getScheduler().runTask(ArenaPacksPlugin.getInstance(), onSaved);
    });
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
    Console.send(sender, "§e" + message);

    if (!Console.isConsole(sender))
      Console.printWarn(message);
  }

  private static void fail(CommandSender sender, String message, @Nullable Throwable cause, Consumer<Boolean> done) {
    OperationLock.release();

    if (cause != null) {
      Console.printError(message);
      cause.printStackTrace();
    }

    // the console already got the message as an error above
    final Runnable report = () -> {
      if (cause == null || !Console.isConsole(sender))
        Console.send(sender, "§c" + message);

      done.accept(false);
    };

    if (Bukkit.isPrimaryThread())
      report.run();
    else
      Bukkit.getScheduler().runTask(ArenaPacksPlugin.getInstance(), report);
  }
}

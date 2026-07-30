package me.metallicgoat.arenapacks.pack;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.ArenaStatus;
import de.marcely.bedwars.api.arena.Team;
import de.marcely.bedwars.api.game.spawner.Spawner;
import de.marcely.bedwars.api.world.hologram.HologramControllerType;
import de.marcely.bedwars.api.world.hologram.HologramEntity;
import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.config.MainConfig;
import me.metallicgoat.arenapacks.util.Console;
import me.metallicgoat.arenapacks.util.WorldFiles;
import me.metallicgoat.arenapacks.util.ZipUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

public class PackExporter {

  /**
   * Exports an arena to exports/&lt;name&gt;/, holding world.zip next to a
   * hand-editable arena.json. Must be called on the main thread; file IO runs
   * async and the outcome is reported to {@code sender}.
   */
  public static void export(CommandSender sender, Arena arena, int packVersion) {
    if (!OperationLock.tryAcquire()) {
      sender.sendMessage("§cAnother arena pack operation is already running. Try again in a moment.");
      return;
    }

    boolean started = false;

    try {
      if (arena.getStatus() != ArenaStatus.STOPPED) {
        sender.sendMessage("§cThe arena must be stopped before it can be exported (current status: " + arena.getStatus() + ").");
        return;
      }

      final World world = arena.getGameWorld();

      if (world == null) {
        sender.sendMessage("§cThe arena's world '" + arena.getGameWorldName() + "' is not loaded.");
        return;
      }

      if (arena.getMinRegionCorner() == null || arena.getMaxRegionCorner() == null) {
        sender.sendMessage("§cThe arena has no region corners set. Set them first with the MBedwars setup tools.");
        return;
      }

      if (!world.getPlayers().isEmpty())
        sender.sendMessage("§eWarning: there are players inside the arena world. Their changes may end up in the pack.");

      for (Arena other : GameAPI.get().getArenas()) {
        if (other != arena && arena.getGameWorldName().equals(other.getGameWorldName())) {
          sender.sendMessage("§eWarning: arena '" + other.getName() + "' shares this world. Its map will be included in the pack.");
          break;
        }
      }

      final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();
      final PackMeta meta = capture(arena, world, packVersion);
      final File worldFolder = world.getWorldFolder();
      final File packDir = new File(categoryFolder(plugin.getExportsFolder(), meta), sanitizeFileName(meta.arenaName));
      final File zipFile = new File(packDir, PackMetaCodec.WORLD_ZIP_NAME);
      final File metaFile = new File(packDir, PackMetaCodec.META_FILE_NAME);

      sender.sendMessage("§7Exporting arena '" + meta.arenaName + "'...");

      // Flush the world and keep it stable while the folder is zipped
      world.save();
      world.setAutoSave(false);
      started = true;

      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        Throwable failure = null;

        try {
          if (!packDir.isDirectory() && !packDir.mkdirs())
            throw new IOException("Failed to create directory: " + packDir);

          // Staged next to the final file so a crash cannot leave a half-written world.zip
          final File partFile = new File(packDir, PackMetaCodec.WORLD_ZIP_NAME + ".part");

          try {
            ZipUtil.zip(worldFolder, partFile, WorldFiles::isExcludedFromPack);

            zipFile.delete();

            if (!partFile.renameTo(zipFile))
              throw new IOException("Failed to move " + partFile.getName() + " into place");
          } finally {
            partFile.delete();
          }

          PackMetaCodec.write(meta, metaFile);
        } catch (Throwable t) {
          failure = t;
        }

        final Throwable finalFailure = failure;

        Bukkit.getScheduler().runTask(plugin, () -> {
          final World loadedWorld = Bukkit.getWorld(world.getName());

          if (loadedWorld != null)
            loadedWorld.setAutoSave(true);

          OperationLock.release();

          if (finalFailure != null) {
            Console.printError("Failed to export arena '" + meta.arenaName + "'", finalFailure.toString());
            finalFailure.printStackTrace();
            sender.sendMessage("§cExport failed: " + finalFailure.getMessage() + " (see console)");
          } else {
            sender.sendMessage("§aExported arena '" + meta.arenaName + "' to " + packDir.getPath()
                + " (world.zip: " + WorldFiles.formatSize(zipFile.length()) + ")");
          }
        });
      });
    } finally {
      if (!started)
        OperationLock.release();
    }
  }

  /** Snapshots everything into a PackMeta. Main thread only. */
  private static PackMeta capture(Arena arena, World world, int packVersion) {
    final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();
    final PackMeta meta = new PackMeta();

    meta.packName = arena.getName();
    meta.packVersion = packVersion;
    meta.exporterVersion = "MBedwarsArenaPacks " + plugin.getDescription().getVersion();
    meta.exportedAt = utcTimestamp();
    meta.minecraftVersion = Bukkit.getBukkitVersion().split("-")[0];
    meta.mbedwarsApiVersion = BedwarsAPI.getAPIVersion();

    meta.originalWorldName = world.getName();

    meta.arenaName = arena.getName();
    meta.customNameEnabled = arena.isCustomNameEnabled();
    meta.customName = arena.isCustomNameEnabled()
        ? PackMeta.customNameOr(arena.getCustomName(), arena.getName())
        : arena.getName();
    meta.minPlayers = arena.getMinPlayers();
    meta.playersPerTeam = arena.getPlayersPerTeam();
    meta.regenTypeId = arena.getRegenerationType().getId();
    meta.weatherType = arena.getWeatherType().name();
    meta.timeType = arena.getTimeType().name();

    for (String author : arena.getAuthors())
      meta.authors.add(author);

    meta.regionMin = new XYZ(arena.getMinRegionCorner());
    meta.regionMax = new XYZ(arena.getMaxRegionCorner());

    final XYZYP spectatorSpawn = arena.getSpectatorSpawn();

    meta.spectatorSpawn = spectatorSpawn != null ? new XYZYP(spectatorSpawn) : null;
    meta.lobby = captureLobby(arena, world);

    for (Team team : arena.getEnabledTeams()) {
      final PackMeta.TeamData data = new PackMeta.TeamData();
      final XYZYP spawn = arena.getTeamSpawn(team);
      final XYZD bed = arena.getBedLocation(team);

      data.spawn = spawn != null ? new XYZYP(spawn) : null;
      data.bed = bed != null ? new XYZD(bed) : null;

      meta.teams.put(team.name(), data);
    }

    for (Spawner spawner : arena.getSpawners())
      meta.spawners.add(new PackMeta.SpawnerData(spawner.getDropType().getId(), new XYZ(spawner.getLocation())));

    for (HologramEntity hologram : BedwarsAPI.getWorldStorage(world).getHolograms()) {
      if (!hologram.isPersistent() || hologram.getControllerType() == HologramControllerType.DEAD)
        continue;

      final Location location = hologram.getSavingLocation();

      if (MainConfig.only_holograms_inside_region && !isInsideRegion(location, meta.regionMin, meta.regionMax))
        continue;

      meta.holograms.add(new PackMeta.HologramData(hologram.getControllerType().name(), new XYZYP(location)));
    }

    return meta;
  }

  /**
   * The lobby is the one location MBedwars stores as a full {@link Location}, so
   * it may point at a completely different world. Only coordinates inside the
   * arena's own world can travel with the pack; a lobby in a shared hub world is
   * skipped, since applying those coordinates to the imported world would drop
   * players somewhere arbitrary.
   */
  private static @Nullable XYZYP captureLobby(Arena arena, World world) {
    if (!arena.hasLobbyLocation())
      return null;

    final Location lobby = arena.getLobbyLocation();

    if (lobby == null)
      return null;

    if (!world.equals(lobby.getWorld())) {
      Console.printWarn("Arena '" + arena.getName() + "' has its lobby in world '"
          + (lobby.getWorld() != null ? lobby.getWorld().getName() : "?")
          + "' rather than the arena's own world, so it is not part of the pack."
          + " Set a lobby after importing.");

      return null;
    }

    return new XYZYP(lobby);
  }

  private static boolean isInsideRegion(Location location, XYZ corner1, XYZ corner2) {
    final double minX = Math.min(corner1.getX(), corner2.getX());
    final double maxX = Math.max(corner1.getX(), corner2.getX());
    final double minY = Math.min(corner1.getY(), corner2.getY());
    final double maxY = Math.max(corner1.getY(), corner2.getY());
    final double minZ = Math.min(corner1.getZ(), corner2.getZ());
    final double maxZ = Math.max(corner1.getZ(), corner2.getZ());

    // +1 as corners are block coordinates and locations sit inside blocks
    return location.getX() >= minX && location.getX() <= maxX + 1
        && location.getY() >= minY && location.getY() <= maxY + 1
        && location.getZ() >= minZ && location.getZ() <= maxZ + 1;
  }

  /**
   * Groups exports by team count ({@code exports/4-Teams/Amazonia}) so an
   * exported folder can be dropped into a pack repo as-is. Hyphenated to keep
   * the path URL-safe once it is published.
   */
  private static File categoryFolder(File exportsFolder, PackMeta meta) {
    if (meta.teams.isEmpty())
      return exportsFolder;

    return new File(exportsFolder, meta.teams.size() + "-Teams");
  }

  public static String sanitizeFileName(String name) {
    return name.replaceAll("[^a-zA-Z0-9-_.]", "_");
  }

  private static String utcTimestamp() {
    final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);

    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    return format.format(new Date());
  }
}

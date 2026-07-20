package me.metallicgoat.arenapack.pack;

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
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import me.metallicgoat.arenapack.ArenaPackPlugin;
import me.metallicgoat.arenapack.config.MainConfig;
import me.metallicgoat.arenapack.util.Console;
import me.metallicgoat.arenapack.util.WorldFiles;
import me.metallicgoat.arenapack.util.ZipUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffect;

public class PackExporter {

  /**
   * Exports an arena to exports/&lt;name&gt;.zip. Must be called on the main
   * thread; file IO runs async and the outcome is reported to {@code sender}.
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

      final ArenaPackPlugin plugin = ArenaPackPlugin.getInstance();
      final PackMeta meta = capture(arena, world, packVersion);
      final File worldFolder = world.getWorldFolder();
      final File tmpDir = new File(plugin.getTmpExportFolder(), meta.arenaName);
      final File zipFile = new File(plugin.getExportsFolder(), sanitizeFileName(meta.arenaName) + ".zip");

      sender.sendMessage("§7Exporting arena '" + meta.arenaName + "'...");

      // Flush the world and keep it stable while the folder is copied
      world.save();
      world.setAutoSave(false);
      started = true;

      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        Throwable failure = null;

        try {
          WorldFiles.deleteDirectory(tmpDir);
          WorldFiles.copyDirectory(worldFolder, new File(tmpDir, PackMetaCodec.WORLD_DIR_NAME), WorldFiles.WORLD_EXCLUDES);
          PackMetaCodec.write(meta, new File(tmpDir, PackMetaCodec.META_FILE_NAME));

          zipFile.getParentFile().mkdirs();
          zipFile.delete();
          ZipUtil.zip(tmpDir, zipFile);
        } catch (Throwable t) {
          failure = t;
        } finally {
          WorldFiles.deleteDirectory(tmpDir);
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
            sender.sendMessage("§aExported arena '" + meta.arenaName + "' to " + zipFile.getPath()
                + " (" + WorldFiles.formatSize(zipFile.length()) + ")");
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
    final ArenaPackPlugin plugin = ArenaPackPlugin.getInstance();
    final PackMeta meta = new PackMeta();

    meta.packName = arena.getName();
    meta.packVersion = packVersion;
    meta.exporterVersion = "MBedwarsArenaPack " + plugin.getDescription().getVersion();
    meta.mbedwarsApiVersion = BedwarsAPI.getAPIVersion();
    meta.minecraftVersion = Bukkit.getBukkitVersion().split("-")[0];
    meta.exportedAt = utcTimestamp();

    meta.originalWorldName = world.getName();

    meta.arenaName = arena.getName();
    meta.customNameEnabled = arena.isCustomNameEnabled();
    meta.customName = arena.getCustomName();
    meta.minPlayers = arena.getMinPlayers();
    meta.playersPerTeam = arena.getPlayersPerTeam();
    meta.regenTypeId = arena.getRegenerationType().getId();
    meta.weatherType = arena.getWeatherType().name();
    meta.timeType = arena.getTimeType().name();

    for (String author : arena.getAuthors())
      meta.authors.add(author);

    final org.bukkit.inventory.ItemStack icon = arena.getIcon();

    meta.icon = icon != null ? icon.clone() : null;
    meta.regionMin = new XYZ(arena.getMinRegionCorner());
    meta.regionMax = new XYZ(arena.getMaxRegionCorner());

    final XYZYP spectatorSpawn = arena.getSpectatorSpawn();

    meta.spectatorSpawn = spectatorSpawn != null ? new XYZYP(spectatorSpawn) : null;

    for (Team team : arena.getEnabledTeams()) {
      final PackMeta.TeamData data = new PackMeta.TeamData();
      final XYZYP spawn = arena.getTeamSpawn(team);
      final XYZD bed = arena.getBedLocation(team);

      data.spawn = spawn != null ? new XYZYP(spawn) : null;
      data.bed = bed != null ? new XYZD(bed) : null;

      for (PotionEffect effect : arena.getTeamBaseOnlyEffects(team))
        data.baseOnlyEffects.add(new PackMeta.EffectData(effect.getType().getName(), effect.getAmplifier()));

      for (PotionEffect effect : arena.getTeamPermanentEffects(team))
        data.permanentEffects.add(new PackMeta.EffectData(effect.getType().getName(), effect.getAmplifier()));

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

    try {
      final StringWriter writer = new StringWriter();

      arena.getPersistentStorage().serialize(writer);
      meta.persistentStorageDump = writer.toString();
    } catch (Exception e) {
      Console.printWarn("Failed to serialize the arena's persistent storage; the pack will not include it: " + e);
    }

    return meta;
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

  public static String sanitizeFileName(String name) {
    return name.replaceAll("[^a-zA-Z0-9-_.]", "_");
  }

  private static String utcTimestamp() {
    final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);

    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    return format.format(new Date());
  }
}

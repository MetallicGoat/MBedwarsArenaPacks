package me.metallicgoat.arenapacks.util;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WorldFiles {

  private static final Set<String> EXCLUDED_NAMES = new HashSet<>(Arrays.asList(
      "uid.dat",
      "session.lock",
      "level.dat_old",
      "level.dat_old.gz",
      "playerdata",
      "stats",
      "advancements",
      "dim-1",
      "dim1",
      "forcedchunks.dat"
  ));

  /**
   * Nothing the server needs to load a world ends in these. Builders tend to
   * park world backups and notes next to level.dat, and shipping a backup
   * archive inside the pack would double its size for nothing.
   */
  private static final List<String> EXCLUDED_EXTENSIONS = Arrays.asList(
      ".zip",
      ".txt"
  );

  /**
   * Whether a world folder entry must not travel with a pack. Applied to the
   * world folder's top level only, so a zipped datapack under datapacks/ is
   * still shipped.
   */
  public static boolean isExcludedFromPack(String name) {
    final String lowerCase = name.toLowerCase(Locale.ROOT);

    if (EXCLUDED_NAMES.contains(lowerCase))
      return true;

    for (String extension : EXCLUDED_EXTENSIONS) {
      if (lowerCase.endsWith(extension))
        return true;
    }

    return false;
  }

  public static void deleteDirectoryAsync(File directory) {
    org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(
        me.metallicgoat.arenapacks.ArenaPacksPlugin.getInstance(),
        () -> deleteDirectory(directory));
  }

  public static boolean deleteDirectory(File directory) {
    if (!directory.exists())
      return true;

    final File[] entries = directory.listFiles();

    if (entries != null) {
      for (File entry : entries) {
        if (entry.isDirectory())
          deleteDirectory(entry);
        else
          entry.delete();
      }
    }

    return directory.delete();
  }

  public static String formatSize(long bytes) {
    if (bytes < 1024)
      return bytes + " B";
    if (bytes < 1024 * 1024)
      return String.format("%.1f KB", bytes / 1024D);

    return String.format("%.1f MB", bytes / (1024D * 1024D));
  }
}

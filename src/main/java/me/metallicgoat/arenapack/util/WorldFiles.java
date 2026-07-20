package me.metallicgoat.arenapack.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class WorldFiles {

  /**
   * Files/folders (relative to the world folder root) that must not travel
   * with a pack: locks, the world UUID and player-specific data.
   */
  public static final Set<String> WORLD_EXCLUDES = new HashSet<>(Arrays.asList(
      "uid.dat",
      "session.lock",
      "level.dat_old",
      "playerdata",
      "stats",
      "advancements"
  ));

  /**
   * Recursively copies a directory. Entries whose path relative to
   * {@code source} matches one in {@code excludedRootEntries} (top level only)
   * are skipped.
   */
  public static void copyDirectory(File source, File target, Collection<String> excludedRootEntries) throws IOException {
    if (!source.isDirectory())
      throw new IOException("Not a directory: " + source);

    final File[] entries = source.listFiles();

    if (entries == null)
      throw new IOException("Failed to list directory: " + source);

    if (!target.isDirectory() && !target.mkdirs())
      throw new IOException("Failed to create directory: " + target);

    for (File entry : entries) {
      if (excludedRootEntries != null && excludedRootEntries.contains(entry.getName()))
        continue;

      final File targetEntry = new File(target, entry.getName());

      if (entry.isDirectory())
        copyDirectory(entry, targetEntry, null);
      else
        Files.copy(entry.toPath(), targetEntry.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public static void deleteDirectoryAsync(File directory) {
    org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(
        me.metallicgoat.arenapack.ArenaPackPlugin.getInstance(),
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

  /**
   * Moves a directory, falling back to copy+delete when a simple rename is
   * not possible (e.g. across file systems).
   */
  public static void moveDirectory(File source, File target) throws IOException {
    if (source.renameTo(target))
      return;

    copyDirectory(source, target, null);
    deleteDirectory(source);
  }

  public static long directorySize(File file) {
    if (file.isFile())
      return file.length();

    long size = 0;
    final File[] entries = file.listFiles();

    if (entries != null) {
      for (File entry : entries)
        size += directorySize(entry);
    }

    return size;
  }

  public static String formatSize(long bytes) {
    if (bytes < 1024)
      return bytes + " B";
    if (bytes < 1024 * 1024)
      return String.format("%.1f KB", bytes / 1024D);

    return String.format("%.1f MB", bytes / (1024D * 1024D));
  }
}

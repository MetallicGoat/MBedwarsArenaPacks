package me.metallicgoat.arenapacks.util;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Compares Minecraft versions such as {@code 1.8.8}, {@code 1.21.4} or {@code 26.3}.
 * Worlds can't be loaded by a server older than the one that saved them.
 */
public class MinecraftVersions {

  /** The running server's version in the same form the exporter writes, e.g. {@code 1.21.4}. */
  public static String server() {
    return Bukkit.getBukkitVersion().split("-")[0];
  }

  /**
   * Whether a world saved on {@code packVersion} is too new for {@code serverVersion}.
   * Unknown or unparseable versions are never treated as too new, so a malformed field
   * doesn't block an install.
   */
  public static boolean isNewerThan(@Nullable String packVersion, String serverVersion) {
    final int[] pack = parse(packVersion);
    final int[] server = parse(serverVersion);

    if (pack == null || server == null)
      return false;

    return compare(pack, server) > 0;
  }

  static int compare(int[] a, int[] b) {
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      final int partA = i < a.length ? a[i] : 0;
      final int partB = i < b.length ? b[i] : 0;

      if (partA != partB)
        return Integer.compare(partA, partB);
    }

    return 0;
  }

  static @Nullable int[] parse(@Nullable String version) {
    if (version == null || !version.trim().matches("\\d+(\\.\\d+)*"))
      return null;

    final String[] parts = version.trim().split("\\.");
    final int[] numbers = new int[parts.length];

    try {
      for (int i = 0; i < parts.length; i++)
        numbers[i] = Integer.parseInt(parts[i]);
    } catch (NumberFormatException e) {
      return null;
    }

    return numbers;
  }
}

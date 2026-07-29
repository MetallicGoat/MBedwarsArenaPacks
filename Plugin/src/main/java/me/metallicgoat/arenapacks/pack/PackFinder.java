package me.metallicgoat.arenapacks.pack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Locates pack folders inside the plugin's search folders. Packs may sit at the
 * root of a search folder or be grouped into category folders
 * ({@code 4-Teams/Aquarium}), so lookups recurse.
 * <p>
 * Takes its roots as a parameter rather than reaching for the plugin instance,
 * which keeps it testable without a running server.
 */
public class PackFinder {

  /**
   * How deep below a search folder a pack may sit. Categories are one level, so
   * this is only a stop against walking into something huge by accident.
   */
  private static final int MAX_DEPTH = 4;

  /**
   * Resolves a user-typed name to pack folders. Matches either the folder's own
   * name ({@code aquarium}) or its path below a search folder
   * ({@code 4-Teams/Aquarium}), case-insensitively.
   * <p>
   * Returns every match: the same pack name may appear under two categories, and
   * the caller should say so rather than silently guess. Empty if nothing matched.
   */
  public static List<File> find(File[] roots, String name) {
    final String wanted = normalize(name);
    final List<File> matches = new ArrayList<>();

    if (wanted.isEmpty())
      return matches;

    for (File root : roots) {
      for (File pack : findAll(root)) {
        final String relative = normalize(relativeName(root, pack));

        if (relative.equals(wanted) || normalize(pack.getName()).equals(wanted))
          matches.add(pack);
      }
    }

    return matches;
  }

  /** Every pack folder below any of {@code roots}, in stable order. */
  public static List<File> findAll(File[] roots) {
    final List<File> packs = new ArrayList<>();

    for (File root : roots)
      packs.addAll(findAll(root));

    return packs;
  }

  public static List<File> findAll(File root) {
    final List<File> packs = new ArrayList<>();

    collect(root, root, 0, packs);

    return packs;
  }

  /** Path of {@code pack} below {@code root}, e.g. {@code 4-Teams/Aquarium}. */
  public static String relativeName(File root, File pack) {
    final String rootPath = root.getPath();
    final String packPath = pack.getPath();

    if (!packPath.startsWith(rootPath))
      return pack.getName();

    return packPath.substring(rootPath.length())
        .replace(File.separatorChar, '/')
        .replaceAll("^/+", "");
  }

  /** A folder holding an arena.json is a pack, not a category. */
  public static boolean isPack(File file) {
    return file.isDirectory() && new File(file, PackMetaCodec.META_FILE_NAME).isFile();
  }

  private static void collect(File root, File dir, int depth, List<File> packs) {
    if (depth > MAX_DEPTH || !dir.isDirectory())
      return;

    final File[] entries = dir.listFiles(File::isDirectory);

    if (entries == null)
      return;

    Arrays.sort(entries, Comparator.comparing(File::getName));

    for (File entry : entries) {
      if (!isInside(root, entry))
        continue;

      // A pack is a leaf: never walk into a world's own folders
      if (isPack(entry))
        packs.add(entry);
      else
        collect(root, entry, depth + 1, packs);
    }
  }

  /**
   * Confirms {@code file} really sits below {@code root} once symlinks and
   * {@code ..} are resolved. The pack argument comes from a command, so it may
   * contain {@code ../}; this is what keeps lookups inside the search folders.
   */
  private static boolean isInside(File root, File file) {
    try {
      final String rootPath = root.getCanonicalPath() + File.separator;

      return file.getCanonicalPath().startsWith(rootPath);
    } catch (IOException e) {
      return false;
    }
  }

  private static String normalize(String name) {
    return name.trim()
        .replace(File.separatorChar, '/')
        .replaceAll("^/+", "")
        .replaceAll("/+$", "")
        .toLowerCase();
  }
}

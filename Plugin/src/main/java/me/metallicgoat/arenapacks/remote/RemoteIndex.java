package me.metallicgoat.arenapacks.remote;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The repo's index.json. Deserialized by Gson.
 * <p>
 * It only lists the pack directories; everything else about a pack lives in
 * that directory's own arena.json, so nothing is duplicated here. Paths are
 * relative to the index file's own folder and may name a category folder
 * ({@code 4-Teams/Aquarium}).
 */
public class RemoteIndex {

  public int format = 1;
  public List<String> packs = new ArrayList<>();

  /**
   * Resolves a user-typed name to a pack path. Matches the directory name
   * (the last path segment) or the full path, both case-insensitively.
   */
  public @Nullable String findPack(String name) {
    final List<String> matches = findPacks(name);

    return matches.isEmpty() ? null : matches.get(0);
  }

  /**
   * Every path matching {@code name}. The same pack name can appear under two
   * categories, in which case the caller should ask for a full path rather than
   * guess which one was meant.
   */
  public List<String> findPacks(String name) {
    final List<String> matches = new ArrayList<>();
    final String wanted = name.trim();

    for (String path : this.packs) {
      if (path == null || path.isEmpty())
        continue;

      if (path.equalsIgnoreCase(wanted) || directoryName(path).equalsIgnoreCase(wanted))
        matches.add(path);
    }

    return matches;
  }

  public static String directoryName(String path) {
    final String trimmed = trimTrailingSlash(path);
    final int slash = trimmed.lastIndexOf('/');

    return slash == -1 ? trimmed : trimmed.substring(slash + 1);
  }

  /**
   * The folder a pack is grouped under, e.g. {@code 4-Teams}. Empty for a pack
   * sitting directly beside index.json.
   */
  public static String categoryOf(String path) {
    final String trimmed = trimTrailingSlash(path);
    final int slash = trimmed.lastIndexOf('/');

    return slash == -1 ? "" : trimmed.substring(0, slash);
  }

  private static String trimTrailingSlash(String path) {
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }
}

package me.metallicgoat.arenapacks.remote;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.config.MainConfig;
import me.metallicgoat.arenapacks.pack.PackExporter;
import me.metallicgoat.arenapacks.pack.PackMeta;
import me.metallicgoat.arenapacks.pack.PackMetaCodec;
import me.metallicgoat.arenapacks.util.Console;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Fetches index.json, per-pack arena.json files and world.zip files from the
 * configured GitHub repository via raw.githubusercontent.com. All network IO
 * runs async; callbacks are delivered on the main thread.
 */
public class RemoteIndexService {

  private static @Nullable RemoteIndex cachedIndex;
  private static long cachedAt = 0;

  /** Pack path -> its arena.json, cached under the same TTL as the index. */
  private static final Map<String, CachedMeta> CACHED_METAS = new HashMap<>();

  public static void fetchIndex(boolean allowCache, Consumer<RemoteIndex> onSuccess, Consumer<String> onError) {
    final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();

    if (allowCache && cachedIndex != null && !isExpired(cachedAt)) {
      onSuccess.accept(cachedIndex);
      return;
    }

    final String url = rawUrl(MainConfig.repo_index_path);

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        final RemoteIndex index = readIndex(url);

        Bukkit.getScheduler().runTask(plugin, () -> {
          cachedIndex = index;
          cachedAt = System.currentTimeMillis();
          onSuccess.accept(index);
        });
      } catch (Exception e) {
        Bukkit.getScheduler().runTask(plugin, () ->
            onError.accept("Failed to fetch the pack index from " + url + ": " + e.getMessage()));
      }
    });
  }

  /** Fetches a single pack's arena.json - the authoritative source for its details. */
  public static void fetchPackMeta(String path, Consumer<PackMeta> onSuccess, Consumer<String> onError) {
    final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();
    final PackMeta cached = getCachedMeta(path);

    if (cached != null) {
      onSuccess.accept(cached);
      return;
    }

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        final PackMeta meta = readPackMeta(path);

        Bukkit.getScheduler().runTask(plugin, () -> {
          cacheMeta(path, meta);
          onSuccess.accept(meta);
        });
      } catch (Exception e) {
        Bukkit.getScheduler().runTask(plugin, () ->
            onError.accept("Failed to read '" + path + "/" + PackMetaCodec.META_FILE_NAME + "': " + e.getMessage()));
      }
    });
  }

  /**
   * Fetches every listed pack's arena.json in one async pass. Packs that fail
   * to load map to {@code null} so the caller can still list them.
   */
  public static void fetchPackMetas(List<String> paths, Consumer<Map<String, PackMeta>> onDone) {
    final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      final Map<String, PackMeta> metas = new LinkedHashMap<>();

      for (String path : paths) {
        if (path == null || path.isEmpty())
          continue;

        PackMeta meta = getCachedMeta(path);

        if (meta == null) {
          try {
            meta = readPackMeta(path);
          } catch (Exception e) {
            meta = null;
          }
        }

        metas.put(path, meta);
      }

      Bukkit.getScheduler().runTask(plugin, () -> {
        for (Map.Entry<String, PackMeta> entry : metas.entrySet()) {
          if (entry.getValue() != null)
            cacheMeta(entry.getKey(), entry.getValue());
        }

        onDone.accept(metas);
      });
    });
  }

  /**
   * Downloads a pack's world.zip into cache/downloads/&lt;name&gt;-v&lt;version&gt;/
   * and writes {@code meta} beside it, so the result is a complete local pack
   * folder that the importer consumes like any other.
   */
  public static void downloadPack(String path, PackMeta meta, Consumer<File> onSuccess, Consumer<String> onError) {
    final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();
    final File packDir = new File(plugin.getDownloadsFolder(),
        PackExporter.sanitizeFileName(meta.packName) + "-v" + meta.packVersion);
    final File worldZip = new File(packDir, PackMetaCodec.WORLD_ZIP_NAME);
    final String url = packFileUrl(path, PackMetaCodec.WORLD_ZIP_NAME);

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      final File partFile = new File(packDir, PackMetaCodec.WORLD_ZIP_NAME + ".part");

      try {
        if (!packDir.isDirectory() && !packDir.mkdirs())
          throw new IOException("Failed to create directory: " + packDir);

        // The world never changes for a given pack version, so reuse it
        if (!worldZip.isFile() || worldZip.length() == 0) {
          final HttpURLConnection connection = openConnection(url);

          try (InputStream in = connection.getInputStream();
               OutputStream out = new FileOutputStream(partFile)) {

            final byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1)
              out.write(buffer, 0, read);
          } finally {
            connection.disconnect();
          }

          worldZip.delete();

          if (!partFile.renameTo(worldZip))
            throw new IOException("Failed to move the downloaded file into place");
        }

        // Always refreshed: the metadata is the half that gets edited upstream
        PackMetaCodec.write(meta, new File(packDir, PackMetaCodec.META_FILE_NAME));

        Bukkit.getScheduler().runTask(plugin, () -> onSuccess.accept(packDir));
      } catch (Exception e) {
        partFile.delete();
        Bukkit.getScheduler().runTask(plugin, () ->
            onError.accept("Failed to download '" + meta.packName + "' from " + url + ": " + e.getMessage()));
      }
    });
  }

  public static @Nullable RemoteIndex getCachedIndex() {
    return cachedIndex;
  }

  private static RemoteIndex readIndex(String url) throws IOException {
    final HttpURLConnection connection = openConnection(url);

    try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
      final RemoteIndex index = new Gson().fromJson(reader, RemoteIndex.class);

      if (index == null || index.packs == null)
        throw new IOException("index.json is empty or malformed");

      warnAboutUnsafePaths(index);

      return index;
    } finally {
      connection.disconnect();
    }
  }

  private static PackMeta readPackMeta(String path) throws IOException {
    final HttpURLConnection connection = openConnection(packFileUrl(path, PackMetaCodec.META_FILE_NAME));

    try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
      return PackMetaCodec.read(reader);
    } finally {
      connection.disconnect();
    }
  }

  /**
   * Pack paths go into a URL verbatim - nothing percent-encodes them - so a
   * space or other unsafe character would fail as an opaque HTTP error. Warn
   * loudly at parse time instead, naming the entry at fault.
   */
  private static void warnAboutUnsafePaths(RemoteIndex index) {
    for (String path : index.packs) {
      if (path != null && !path.isEmpty() && !path.matches("[A-Za-z0-9-_./]+")) {
        Console.printWarn("Pack path '" + path + "' in index.json contains characters that are not URL-safe"
            + " and will fail to download. Rename the folder using letters, digits, '-', '_' or '.'"
            + " (this repo's convention is e.g. '4-Teams/Aquarium').");
      }
    }
  }

  /**
   * Pack paths in the index are relative to the index file's own folder, so a
   * pack collection stays self-contained no matter where it sits in the repo.
   */
  private static String packFileUrl(String path, String fileName) {
    final String indexPath = MainConfig.repo_index_path;
    final int slash = indexPath.lastIndexOf('/');
    final String base = slash == -1 ? "" : indexPath.substring(0, slash + 1);

    return rawUrl(base + path + "/" + fileName);
  }

  private static @Nullable PackMeta getCachedMeta(String path) {
    synchronized (CACHED_METAS) {
      final CachedMeta cached = CACHED_METAS.get(path);

      return cached != null && !isExpired(cached.time) ? cached.meta : null;
    }
  }

  private static void cacheMeta(String path, PackMeta meta) {
    synchronized (CACHED_METAS) {
      CACHED_METAS.put(path, new CachedMeta(meta, System.currentTimeMillis()));
    }
  }

  private static boolean isExpired(long time) {
    return System.currentTimeMillis() - time >= MainConfig.index_cache_seconds * 1000L;
  }

  private static String rawUrl(String path) {
    final String cleanedPath = path.startsWith("/") ? path.substring(1) : path;

    return "https://raw.githubusercontent.com/" + MainConfig.repo_slug + "/" + MainConfig.repo_branch + "/" + cleanedPath;
  }

  private static HttpURLConnection openConnection(String url) throws IOException {
    final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

    connection.setConnectTimeout(MainConfig.connect_timeout_ms);
    connection.setReadTimeout(MainConfig.read_timeout_ms);
    connection.setRequestProperty("User-Agent",
        "MBedwarsArenaPacks/" + ArenaPacksPlugin.getInstance().getDescription().getVersion());
    connection.setInstanceFollowRedirects(true);

    final int status = connection.getResponseCode();

    if (status != HttpURLConnection.HTTP_OK) {
      connection.disconnect();
      throw new IOException("HTTP " + status
          + (status == HttpURLConnection.HTTP_NOT_FOUND ? " (does the file exist in the repo?)" : ""));
    }

    return connection;
  }

  private static class CachedMeta {

    private final PackMeta meta;
    private final long time;

    private CachedMeta(PackMeta meta, long time) {
      this.meta = meta;
      this.time = time;
    }
  }
}

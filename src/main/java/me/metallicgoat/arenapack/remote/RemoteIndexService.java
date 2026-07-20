package me.metallicgoat.arenapack.remote;

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
import java.util.function.Consumer;
import me.metallicgoat.arenapack.ArenaPackPlugin;
import me.metallicgoat.arenapack.config.MainConfig;
import me.metallicgoat.arenapack.pack.PackExporter;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Fetches index.json and pack zips from the configured GitHub repository via
 * raw.githubusercontent.com. All network IO runs async; callbacks are
 * delivered on the main thread.
 */
public class RemoteIndexService {

  private static @Nullable RemoteIndex cachedIndex;
  private static long cachedAt = 0;

  public static void fetchIndex(boolean allowCache, Consumer<RemoteIndex> onSuccess, Consumer<String> onError) {
    final ArenaPackPlugin plugin = ArenaPackPlugin.getInstance();

    if (allowCache && cachedIndex != null
        && System.currentTimeMillis() - cachedAt < MainConfig.index_cache_seconds * 1000L) {
      onSuccess.accept(cachedIndex);
      return;
    }

    final String url = rawUrl(MainConfig.repo_index_path);

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        final HttpURLConnection connection = openConnection(url);

        try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
          final RemoteIndex index = new Gson().fromJson(reader, RemoteIndex.class);

          if (index == null || index.packs == null)
            throw new IOException("index.json is empty or malformed");

          Bukkit.getScheduler().runTask(plugin, () -> {
            cachedIndex = index;
            cachedAt = System.currentTimeMillis();
            onSuccess.accept(index);
          });
        } finally {
          connection.disconnect();
        }
      } catch (Exception e) {
        Bukkit.getScheduler().runTask(plugin, () ->
            onError.accept("Failed to fetch the pack index from " + url + ": " + e.getMessage()));
      }
    });
  }

  public static void downloadPack(RemotePackInfo pack, Consumer<File> onSuccess, Consumer<String> onError) {
    final ArenaPackPlugin plugin = ArenaPackPlugin.getInstance();
    final File target = new File(plugin.getDownloadsFolder(),
        PackExporter.sanitizeFileName(pack.name) + "-v" + pack.version + ".zip");
    final String url = rawUrl(pack.file);

    if (target.isFile() && target.length() > 0) {
      onSuccess.accept(target);
      return;
    }

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      final File partFile = new File(target.getPath() + ".part");

      try {
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

        target.getParentFile().mkdirs();
        target.delete();

        if (!partFile.renameTo(target))
          throw new IOException("Failed to move the downloaded file into place");

        Bukkit.getScheduler().runTask(plugin, () -> onSuccess.accept(target));
      } catch (Exception e) {
        partFile.delete();
        Bukkit.getScheduler().runTask(plugin, () ->
            onError.accept("Failed to download '" + pack.name + "' from " + url + ": " + e.getMessage()));
      }
    });
  }

  public static @Nullable RemoteIndex getCachedIndex() {
    return cachedIndex;
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
        "MBedwarsArenaPack/" + ArenaPackPlugin.getInstance().getDescription().getVersion());
    connection.setInstanceFollowRedirects(true);

    final int status = connection.getResponseCode();

    if (status != HttpURLConnection.HTTP_OK) {
      connection.disconnect();
      throw new IOException("HTTP " + status
          + (status == HttpURLConnection.HTTP_NOT_FOUND ? " (does the file exist in the repo?)" : ""));
    }

    return connection;
  }
}

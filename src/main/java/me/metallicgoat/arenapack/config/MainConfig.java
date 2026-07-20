package me.metallicgoat.arenapack.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class MainConfig {

  public static String repo_slug = "MetallicGoat/mbedwars-arena-packs";
  public static String repo_branch = "main";
  public static String repo_index_path = "index.json";

  public static int connect_timeout_ms = 10000;
  public static int read_timeout_ms = 30000;
  public static int index_cache_seconds = 300;

  public static String world_name_format = "arenapack_{arena}";
  public static boolean only_holograms_inside_region = true;

  public static void load(Plugin plugin) {
    plugin.reloadConfig();

    final FileConfiguration config = plugin.getConfig();

    repo_slug = config.getString("repo.slug", repo_slug);
    repo_branch = config.getString("repo.branch", repo_branch);
    repo_index_path = config.getString("repo.index-path", repo_index_path);

    connect_timeout_ms = config.getInt("network.connect-timeout-ms", connect_timeout_ms);
    read_timeout_ms = config.getInt("network.read-timeout-ms", read_timeout_ms);
    index_cache_seconds = config.getInt("network.index-cache-seconds", index_cache_seconds);

    world_name_format = config.getString("import.world-name-format", world_name_format);
    only_holograms_inside_region = config.getBoolean("export.only-holograms-inside-region", only_holograms_inside_region);
  }
}

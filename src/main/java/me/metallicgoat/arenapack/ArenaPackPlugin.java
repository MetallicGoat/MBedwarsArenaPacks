package me.metallicgoat.arenapack;

import de.marcely.bedwars.api.BedwarsAPI;
import java.io.File;
import me.metallicgoat.arenapack.config.MainConfig;
import me.metallicgoat.arenapack.util.Console;
import me.metallicgoat.arenapack.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ArenaPackPlugin extends JavaPlugin {

  public static final int MIN_MBEDWARS_API_VER = 208;
  public static final String MIN_MBEDWARS_VER_NAME = "5.5.8";

  private static ArenaPackPlugin instance;
  private static ArenaPackAddon addon;

  public static ArenaPackPlugin getInstance() {
    return instance;
  }

  public static ArenaPackAddon getAddon() {
    return addon;
  }

  @Override
  public void onEnable() {
    instance = this;

    if (!checkMBedwars())
      return;
    if (!registerAddon())
      return;

    saveDefaultConfig();
    MainConfig.load(this);

    setupDataFolders();

    // MBedwars builds its command tree while enabling; register ours once it is ready
    BedwarsAPI.onReady(() -> addon.registerCommands());

    Console.printInfo("MBedwarsArenaPack v" + getDescription().getVersion() + " enabled");
  }

  private void setupDataFolders() {
    getExportsFolder().mkdirs();
    getImportsFolder().mkdirs();
    getDownloadsFolder().mkdirs();

    // Leftovers from operations interrupted by a shutdown
    WorldFiles.deleteDirectory(getTmpExportFolder());
    WorldFiles.deleteDirectory(getTmpImportFolder());
  }

  public File getExportsFolder() {
    return new File(getDataFolder(), "exports");
  }

  public File getImportsFolder() {
    return new File(getDataFolder(), "imports");
  }

  public File getDownloadsFolder() {
    return new File(getDataFolder(), "cache/downloads");
  }

  public File getTmpExportFolder() {
    return new File(getDataFolder(), "cache/tmp-export");
  }

  public File getTmpImportFolder() {
    return new File(getDataFolder(), "cache/tmp-import");
  }

  private boolean checkMBedwars() {
    try {
      final Class<?> apiClass = Class.forName("de.marcely.bedwars.api.BedwarsAPI");
      final int apiVersion = (int) apiClass.getMethod("getAPIVersion").invoke(null);

      if (apiVersion < MIN_MBEDWARS_API_VER)
        throw new IllegalStateException();
    } catch (Exception e) {
      getLogger().warning("Sorry, your installed version of MBedwars is not supported. Please install at least v" + MIN_MBEDWARS_VER_NAME);
      Bukkit.getPluginManager().disablePlugin(this);

      return false;
    }

    return true;
  }

  private boolean registerAddon() {
    addon = new ArenaPackAddon(this);

    if (!addon.register()) {
      getLogger().warning("It seems like this addon has already been loaded. Please delete duplicates and try again.");
      Bukkit.getPluginManager().disablePlugin(this);

      return false;
    }

    return true;
  }
}

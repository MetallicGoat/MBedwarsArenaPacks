package me.metallicgoat.arenapacks.pack;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.RegenerationType;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import me.metallicgoat.arenapacks.remote.RemoteIndexService;
import me.metallicgoat.arenapacks.util.Console;
import me.metallicgoat.arenapacks.util.MinecraftVersions;
import me.metallicgoat.arenapacks.util.WorldFiles;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

/**
 * Installs a pack from the configured GitHub repo: resolves the name against
 * the index, checks the pack's API version, downloads it and imports it.
 */
public class PackInstaller {

  public enum Result {
    INSTALLED,
    /** {@code skipExisting} was set and an arena with the pack's name already exists */
    SKIPPED,
    /** the pack needs a newer Minecraft or MBedwars version than this server runs */
    INCOMPATIBLE,
    FAILED
  }

  /**
   * The regeneration types a pack can be installed as, parsed from a command argument.
   * Returns null for anything but {@code world} or {@code region}.
   */
  public static @Nullable RegenerationType parseInstallType(String arg) {
    final RegenerationType type = RegenerationType.fromId(arg);

    return type != null && type.isNormal() ? type : null;
  }

  /**
   * Call from the main thread. {@code onDone} is always called exactly once, on
   * the main thread. With {@code skipExisting}, a pack whose arena already
   * exists is skipped before anything is downloaded, which makes batch installs
   * safe to re-run.
   */
  public static void install(CommandSender sender, String packName, @Nullable String overrideName,
                             RegenerationType regenType, boolean skipExisting, Consumer<Result> onDone) {
    final Consumer<String> onError = error -> {
      Console.send(sender, "§c" + error);
      onDone.accept(Result.FAILED);
    };

    Console.send(sender, "§7Looking up '" + packName + "'...");

    RemoteIndexService.fetchIndex(true, index -> {
      final List<String> matches = index.findPacks(packName);

      if (matches.isEmpty()) {
        onError.accept("No pack named '" + packName + "' exists in the repository. Use '/bw arenapacks list' to see what is available.");
        return;
      }

      if (matches.size() > 1) {
        Console.send(sender, "§c'" + packName + "' matches more than one pack. Install it by its full path:");

        for (String match : matches)
          Console.send(sender, "§7- §f" + match);

        onDone.accept(Result.FAILED);
        return;
      }

      final String path = matches.get(0);

      RemoteIndexService.fetchPackMeta(path, meta -> {
        final String arenaName = overrideName != null ? overrideName : meta.arenaName;

        if (skipExisting && GameAPI.get().getArenaByExactName(arenaName) != null) {
          Console.send(sender, "§7Arena '" + arenaName + "' already exists, skipping '" + path + "'.");
          onDone.accept(Result.SKIPPED);
          return;
        }

        // checked before downloading anything
        if (meta.mbedwarsApiVersion > BedwarsAPI.getAPIVersion()) {
          Console.send(sender, "§cSkipping '" + path + "': it was exported on a newer MBedwars version (API "
              + meta.mbedwarsApiVersion + ", installed: " + BedwarsAPI.getAPIVersion() + ").");
          onDone.accept(Result.INCOMPATIBLE);
          return;
        }

        if (MinecraftVersions.isNewerThan(meta.minecraftVersion, MinecraftVersions.server())) {
          Console.send(sender, "§cSkipping '" + path + "': its world was saved on Minecraft " + meta.minecraftVersion
              + ", but this server runs " + MinecraftVersions.server() + ". Worlds can't be loaded on older versions.");
          onDone.accept(Result.INCOMPATIBLE);
          return;
        }

        Console.send(sender, "§7Downloading '" + meta.packName + "' v" + meta.packVersion + "...");

        RemoteIndexService.downloadPack(path, meta, packDir -> {
          final File worldZip = new File(packDir, PackMetaCodec.WORLD_ZIP_NAME);

          Console.send(sender, "§7Got " + WorldFiles.formatSize(worldZip.length()) + ", importing...");
          PackImporter.importPack(sender, packDir, overrideName, regenType,
              success -> onDone.accept(success ? Result.INSTALLED : Result.FAILED));
        }, onError);
      }, onError);
    }, onError);
  }
}

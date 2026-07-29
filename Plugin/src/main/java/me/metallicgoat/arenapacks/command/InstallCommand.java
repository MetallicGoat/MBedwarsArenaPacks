package me.metallicgoat.arenapacks.command;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.SubCommand;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.pack.PackImporter;
import me.metallicgoat.arenapacks.pack.PackMetaCodec;
import me.metallicgoat.arenapacks.remote.RemoteIndex;
import me.metallicgoat.arenapacks.remote.RemoteIndexService;
import me.metallicgoat.arenapacks.util.WorldFiles;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class InstallCommand implements CommandHandler {

  @Override
  public Plugin getPlugin() {
    return ArenaPacksPlugin.getInstance();
  }

  @Override
  public void onRegister(SubCommand command) {
    command.setOnlyForPlayers(false);
    command.setUsage("<packName> [newArenaName]");
    command.setPermission("mbedwars.arenapacks.install");
  }

  @Override
  public void onFire(CommandSender sender, String label, String[] args) {
    if (args.length < 1 || args.length > 2) {
      sender.sendMessage("§cUsage: /" + label + " <packName> [newArenaName]");
      return;
    }

    final String packName = args[0];
    final String overrideName = args.length == 2 ? args[1] : null;

    sender.sendMessage("§7Looking up '" + packName + "'...");

    RemoteIndexService.fetchIndex(true, index -> {
      final List<String> matches = index.findPacks(packName);

      if (matches.isEmpty()) {
        sender.sendMessage("§cNo pack named '" + packName + "' exists in the repository. Use '/bw arenapacks list' to see what is available.");
        return;
      }

      if (matches.size() > 1) {
        sender.sendMessage("§c'" + packName + "' matches more than one pack. Install it by its full path:");

        for (String match : matches)
          sender.sendMessage("§7- §f" + match);

        return;
      }

      final String path = matches.get(0);

      RemoteIndexService.fetchPackMeta(path, meta -> {
        if (meta.mbedwarsApiVersion > BedwarsAPI.getAPIVersion()) {
          sender.sendMessage("§cThis pack was exported on a newer MBedwars version (API " + meta.mbedwarsApiVersion
              + ", installed: " + BedwarsAPI.getAPIVersion() + ").");
          return;
        }

        if (meta.minecraftVersion != null)
          sender.sendMessage("§7Pack was built on Minecraft " + meta.minecraftVersion
              + ". Worlds cannot be loaded on older server versions.");

        sender.sendMessage("§7Downloading '" + meta.packName + "' v" + meta.packVersion + "...");

        RemoteIndexService.downloadPack(path, meta, packDir -> {
          final File worldZip = new File(packDir, PackMetaCodec.WORLD_ZIP_NAME);

          sender.sendMessage("§7Got " + WorldFiles.formatSize(worldZip.length()) + ", importing...");
          PackImporter.importPack(sender, packDir, overrideName);
        }, error -> sender.sendMessage("§c" + error));
      }, error -> sender.sendMessage("§c" + error));
    }, error -> sender.sendMessage("§c" + error));
  }

  @Override
  public @Nullable List<String> onAutocomplete(CommandSender sender, String[] args) {
    if (args.length != 1)
      return null;

    final RemoteIndex index = RemoteIndexService.getCachedIndex();

    if (index == null)
      return null;

    final List<String> names = new ArrayList<>();

    for (String path : index.packs) {
      if (path == null || path.isEmpty())
        continue;

      final String name = RemoteIndex.directoryName(path);

      if (name.toLowerCase().startsWith(args[0].toLowerCase()))
        names.add(name);
    }

    return names;
  }
}

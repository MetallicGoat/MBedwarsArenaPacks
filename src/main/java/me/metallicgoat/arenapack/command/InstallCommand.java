package me.metallicgoat.arenapack.command;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.SubCommand;
import java.util.ArrayList;
import java.util.List;
import me.metallicgoat.arenapack.ArenaPackPlugin;
import me.metallicgoat.arenapack.pack.PackImporter;
import me.metallicgoat.arenapack.remote.RemoteIndex;
import me.metallicgoat.arenapack.remote.RemoteIndexService;
import me.metallicgoat.arenapack.remote.RemotePackInfo;
import me.metallicgoat.arenapack.util.WorldFiles;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class InstallCommand implements CommandHandler {

  @Override
  public Plugin getPlugin() {
    return ArenaPackPlugin.getInstance();
  }

  @Override
  public void onRegister(SubCommand command) {
    command.setOnlyForPlayers(false);
    command.setUsage("<packName> [newArenaName]");
    command.setPermission("mbedwars.arenapack.install");
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
      final RemotePackInfo pack = index.findPack(packName);

      if (pack == null) {
        sender.sendMessage("§cNo pack named '" + packName + "' exists in the repository. Use '/bw arenapack list' to see what is available.");
        return;
      }

      if (pack.minMBedwarsApi > BedwarsAPI.getAPIVersion()) {
        sender.sendMessage("§cThis pack requires a newer MBedwars version (API " + pack.minMBedwarsApi
            + ", installed: " + BedwarsAPI.getAPIVersion() + ").");
        return;
      }

      if (pack.minecraftVersion != null)
        sender.sendMessage("§7Pack was built on Minecraft " + pack.minecraftVersion
            + ". Worlds cannot be loaded on older server versions.");

      sender.sendMessage("§7Downloading '" + pack.name + "' v" + pack.version + "...");

      RemoteIndexService.downloadPack(pack, file -> {
        sender.sendMessage("§7Downloaded " + WorldFiles.formatSize(file.length()) + ", importing...");
        PackImporter.importPack(sender, file, overrideName);
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

    for (RemotePackInfo pack : index.packs) {
      if (pack.isValid() && pack.name.toLowerCase().startsWith(args[0].toLowerCase()))
        names.add(pack.name);
    }

    return names;
  }
}

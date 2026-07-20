package me.metallicgoat.arenapack.command;

import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.SubCommand;
import java.io.File;
import java.util.Collections;
import java.util.List;
import me.metallicgoat.arenapack.ArenaPackPlugin;
import me.metallicgoat.arenapack.config.MainConfig;
import me.metallicgoat.arenapack.remote.RemoteIndexService;
import me.metallicgoat.arenapack.remote.RemotePackInfo;
import me.metallicgoat.arenapack.util.WorldFiles;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class ListCommand implements CommandHandler {

  @Override
  public Plugin getPlugin() {
    return ArenaPackPlugin.getInstance();
  }

  @Override
  public void onRegister(SubCommand command) {
    command.setOnlyForPlayers(false);
    command.setUsage("[local]");
    command.setPermission("mbedwars.arenapack.list");
  }

  @Override
  public void onFire(CommandSender sender, String label, String[] args) {
    if (args.length >= 1 && args[0].equalsIgnoreCase("local")) {
      listLocal(sender);
      return;
    }

    sender.sendMessage("§7Fetching pack index from " + MainConfig.repo_slug + "...");

    RemoteIndexService.fetchIndex(true, index -> {
      if (index.packs.isEmpty()) {
        sender.sendMessage("§eThe repository contains no packs.");
        return;
      }

      sender.sendMessage("§6Available arena packs:");

      for (RemotePackInfo pack : index.packs) {
        if (!pack.isValid())
          continue;

        final boolean installed = GameAPI.get().getArenaByExactName(pack.name) != null;
        final StringBuilder line = new StringBuilder("§7- §f")
            .append(pack.name)
            .append(" §7v").append(pack.version);

        if (pack.description != null)
          line.append(" §8- §7").append(pack.description);
        if (installed)
          line.append(" §a(installed)");

        sender.sendMessage(line.toString());
      }

      sender.sendMessage("§7Install one with: /bw arenapack install <name>");
    }, error -> sender.sendMessage("§c" + error));
  }

  private void listLocal(CommandSender sender) {
    final ArenaPackPlugin plugin = ArenaPackPlugin.getInstance();
    boolean foundAny = false;

    sender.sendMessage("§6Local arena packs:");

    for (File folder : new File[]{plugin.getImportsFolder(), plugin.getExportsFolder(), plugin.getDownloadsFolder()}) {
      final File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));

      if (files == null || files.length == 0)
        continue;

      for (File file : files) {
        sender.sendMessage("§7- §f" + file.getName() + " §7(" + WorldFiles.formatSize(file.length())
            + ", " + folder.getName() + ")");
        foundAny = true;
      }
    }

    if (!foundAny)
      sender.sendMessage("§7(none - export an arena or drop zips into plugins/MBedwarsArenaPack/imports/)");
  }

  @Override
  public @Nullable List<String> onAutocomplete(CommandSender sender, String[] args) {
    if (args.length == 1 && "local".startsWith(args[0].toLowerCase()))
      return Collections.singletonList("local");

    return null;
  }
}

package me.metallicgoat.arenapacks.command;

import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.SubCommand;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.config.MainConfig;
import me.metallicgoat.arenapacks.pack.PackFinder;
import me.metallicgoat.arenapacks.pack.PackMeta;
import me.metallicgoat.arenapacks.pack.PackMetaCodec;
import me.metallicgoat.arenapacks.remote.RemoteIndex;
import me.metallicgoat.arenapacks.remote.RemoteIndexService;
import me.metallicgoat.arenapacks.util.WorldFiles;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class ListCommand implements CommandHandler {

  @Override
  public Plugin getPlugin() {
    return ArenaPacksPlugin.getInstance();
  }

  @Override
  public void onRegister(SubCommand command) {
    command.setOnlyForPlayers(false);
    command.setUsage("[local]");
    command.setPermission("mbedwars.arenapacks.list");
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

      // Details live in each pack's own arena.json, so they are fetched per pack
      RemoteIndexService.fetchPackMetas(index.packs, metas -> {
        sender.sendMessage("§6Available arena packs:");

        String shownCategory = null;

        for (Map.Entry<String, PackMeta> entry : metas.entrySet()) {
          final String path = entry.getKey();
          final PackMeta meta = entry.getValue();
          final String category = RemoteIndex.categoryOf(path);

          // Packs arrive in index order, so a header per run of a category groups them
          if (!category.isEmpty() && !category.equals(shownCategory))
            sender.sendMessage("§6" + category + ":");

          shownCategory = category;

          if (meta == null) {
            sender.sendMessage("§7- §f" + RemoteIndex.directoryName(path) + " §c(details unavailable)");
            continue;
          }

          final boolean installed = GameAPI.get().getArenaByExactName(meta.arenaName) != null;
          final StringBuilder line = new StringBuilder("§7- §f")
              .append(RemoteIndex.directoryName(path))
              .append(" §7v").append(meta.packVersion);

          if (!meta.authors.isEmpty())
            line.append(" §8by §7").append(String.join(", ", meta.authors));
          if (installed)
            line.append(" §a(installed)");

          sender.sendMessage(line.toString());
        }

        sender.sendMessage("§7Install one with: /bw arenapacks install <name>");
      });
    }, error -> sender.sendMessage("§c" + error));
  }

  private void listLocal(CommandSender sender) {
    boolean foundAny = false;

    sender.sendMessage("§6Local arena packs:");

    for (File folder : ImportCommand.searchFolders()) {
      for (File pack : PackFinder.findAll(folder)) {
        final File worldZip = new File(pack, PackMetaCodec.WORLD_ZIP_NAME);

        sender.sendMessage("§7- §f" + PackFinder.relativeName(folder, pack)
            + " §7(" + (worldZip.isFile() ? WorldFiles.formatSize(worldZip.length()) : "§cno " + PackMetaCodec.WORLD_ZIP_NAME + "§7")
            + ", " + folder.getName() + ")");
        foundAny = true;
      }
    }

    if (!foundAny)
      sender.sendMessage("§7(none - export an arena or drop pack folders into plugins/MBedwarsArenaPacks/imports/)");
  }

  @Override
  public @Nullable List<String> onAutocomplete(CommandSender sender, String[] args) {
    if (args.length == 1 && "local".startsWith(args[0].toLowerCase()))
      return Collections.singletonList("local");

    return null;
  }
}

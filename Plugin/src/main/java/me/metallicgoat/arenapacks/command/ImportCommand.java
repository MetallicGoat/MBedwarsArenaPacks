package me.metallicgoat.arenapacks.command;

import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.SubCommand;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.pack.PackFinder;
import me.metallicgoat.arenapacks.pack.PackImporter;
import me.metallicgoat.arenapacks.pack.PackMetaCodec;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class ImportCommand implements CommandHandler {

  @Override
  public Plugin getPlugin() {
    return ArenaPacksPlugin.getInstance();
  }

  @Override
  public void onRegister(SubCommand command) {
    command.setOnlyForPlayers(false);
    command.setUsage("<packFolder> [newArenaName]");
    command.setPermission("mbedwars.arenapacks.import");
  }

  @Override
  public void onFire(CommandSender sender, String label, String[] args) {
    if (args.length < 1 || args.length > 2) {
      sender.sendMessage("§cUsage: /" + label + " <packFolder> [newArenaName]");
      return;
    }

    final List<File> matches = PackFinder.find(searchFolders(), args[0]);

    if (matches.isEmpty()) {
      sender.sendMessage("§cCould not find a pack named '" + args[0] + "' in the imports, exports or downloads folder."
          + " A pack is a folder holding " + PackMetaCodec.META_FILE_NAME + " and " + PackMetaCodec.WORLD_ZIP_NAME
          + "; drop one into plugins/MBedwarsArenaPacks/imports/");
      return;
    }

    if (matches.size() > 1) {
      sender.sendMessage("§c'" + args[0] + "' matches more than one pack. Import it by its full path:");

      for (File match : matches)
        sender.sendMessage("§7- §f" + describe(match));

      return;
    }

    PackImporter.importPack(sender, matches.get(0), args.length == 2 ? args[1] : null);
  }

  @Override
  public @Nullable List<String> onAutocomplete(CommandSender sender, String[] args) {
    if (args.length != 1)
      return null;

    final List<String> names = new ArrayList<>();

    for (File pack : PackFinder.findAll(searchFolders())) {
      if (pack.getName().toLowerCase().startsWith(args[0].toLowerCase()) && !names.contains(pack.getName()))
        names.add(pack.getName());
    }

    return names;
  }

  /** Path of a pack relative to whichever search folder holds it. */
  static String describe(File pack) {
    for (File root : searchFolders()) {
      if (pack.getPath().startsWith(root.getPath()))
        return PackFinder.relativeName(root, pack);
    }

    return pack.getName();
  }

  static File[] searchFolders() {
    final ArenaPacksPlugin plugin = ArenaPacksPlugin.getInstance();

    return new File[]{
        plugin.getImportsFolder(),
        plugin.getExportsFolder(),
        plugin.getDownloadsFolder()
    };
  }
}

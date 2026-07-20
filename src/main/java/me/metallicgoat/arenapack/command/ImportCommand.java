package me.metallicgoat.arenapack.command;

import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.SubCommand;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import me.metallicgoat.arenapack.ArenaPackPlugin;
import me.metallicgoat.arenapack.pack.PackImporter;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class ImportCommand implements CommandHandler {

  @Override
  public Plugin getPlugin() {
    return ArenaPackPlugin.getInstance();
  }

  @Override
  public void onRegister(SubCommand command) {
    command.setOnlyForPlayers(false);
    command.setUsage("<zipFile> [newArenaName]");
    command.setPermission("mbedwars.arenapack.import");
  }

  @Override
  public void onFire(CommandSender sender, String label, String[] args) {
    if (args.length < 1 || args.length > 2) {
      sender.sendMessage("§cUsage: /" + label + " <zipFile> [newArenaName]");
      return;
    }

    final File zipFile = resolveZip(args[0]);

    if (zipFile == null) {
      sender.sendMessage("§cCould not find '" + args[0] + "' in the imports, exports or downloads folder."
          + " Drop the zip into plugins/MBedwarsArenaPack/imports/");
      return;
    }

    PackImporter.importPack(sender, zipFile, args.length == 2 ? args[1] : null);
  }

  @Override
  public @Nullable List<String> onAutocomplete(CommandSender sender, String[] args) {
    if (args.length != 1)
      return null;

    final List<String> names = new ArrayList<>();

    for (File folder : searchFolders()) {
      final File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));

      if (files == null)
        continue;

      for (File file : files) {
        if (file.getName().toLowerCase().startsWith(args[0].toLowerCase()) && !names.contains(file.getName()))
          names.add(file.getName());
      }
    }

    return names;
  }

  private static @Nullable File resolveZip(String name) {
    final String zipName = name.toLowerCase().endsWith(".zip") ? name : name + ".zip";

    for (File folder : searchFolders()) {
      final File file = new File(folder, zipName);

      // Keep lookups inside the search folders (the name may contain ../)
      if (file.isFile() && file.getParentFile().equals(folder))
        return file;
    }

    return null;
  }

  private static File[] searchFolders() {
    final ArenaPackPlugin plugin = ArenaPackPlugin.getInstance();

    return new File[]{
        plugin.getImportsFolder(),
        plugin.getExportsFolder(),
        plugin.getDownloadsFolder()
    };
  }
}

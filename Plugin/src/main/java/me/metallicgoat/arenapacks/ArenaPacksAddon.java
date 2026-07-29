package me.metallicgoat.arenapacks;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.BedwarsAddon;
import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.CommandsCollection;
import me.metallicgoat.arenapacks.command.ExportCommand;
import me.metallicgoat.arenapacks.command.ImportCommand;
import me.metallicgoat.arenapacks.command.InstallCommand;
import me.metallicgoat.arenapacks.command.ListCommand;
import me.metallicgoat.arenapacks.util.Console;

public class ArenaPacksAddon extends BedwarsAddon {

  private final ArenaPacksPlugin plugin;

  public ArenaPacksAddon(ArenaPacksPlugin plugin) {
    super(plugin);

    this.plugin = plugin;
  }

  @Override
  public String getName() {
    return this.plugin.getName();
  }

  public void registerCommands() {
    final CommandsCollection root = BedwarsAPI.getRootCommandsCollection();
    final CommandsCollection collection = root.addCommandsCollection("arenapacks");

    if (collection == null) {
      Console.printWarn("Failed to register '/bw arenapacks' commands. Does another plugin already use that name?");
      return;
    }

    collection.setPermission("mbedwars.arenapacks");

    registerCommand("export", collection, new ExportCommand());
    registerCommand("import", collection, new ImportCommand());
    registerCommand("install", collection, new InstallCommand());
    registerCommand("list", collection, new ListCommand());
  }

  private void registerCommand(String name, CommandsCollection collection, CommandHandler handler) {
    collection.addCommand(name).setHandler(handler);
  }
}

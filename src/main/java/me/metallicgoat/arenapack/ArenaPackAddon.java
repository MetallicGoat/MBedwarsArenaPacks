package me.metallicgoat.arenapack;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.BedwarsAddon;
import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.CommandsCollection;
import me.metallicgoat.arenapack.command.ExportCommand;
import me.metallicgoat.arenapack.command.ImportCommand;
import me.metallicgoat.arenapack.command.InstallCommand;
import me.metallicgoat.arenapack.command.ListCommand;
import me.metallicgoat.arenapack.util.Console;

public class ArenaPackAddon extends BedwarsAddon {

  private final ArenaPackPlugin plugin;

  public ArenaPackAddon(ArenaPackPlugin plugin) {
    super(plugin);

    this.plugin = plugin;
  }

  @Override
  public String getName() {
    return this.plugin.getName();
  }

  public void registerCommands() {
    final CommandsCollection root = BedwarsAPI.getRootCommandsCollection();
    final CommandsCollection collection = root.addCommandsCollection("arenapack");

    if (collection == null) {
      Console.printWarn("Failed to register '/bw arenapack' commands. Does another plugin already use that name?");
      return;
    }

    collection.setPermission("mbedwars.arenapack");

    registerCommand("export", collection, new ExportCommand());
    registerCommand("import", collection, new ImportCommand());
    registerCommand("install", collection, new InstallCommand());
    registerCommand("list", collection, new ListCommand());
  }

  private void registerCommand(String name, CommandsCollection collection, CommandHandler handler) {
    collection.addCommand(name).setHandler(handler);
  }
}

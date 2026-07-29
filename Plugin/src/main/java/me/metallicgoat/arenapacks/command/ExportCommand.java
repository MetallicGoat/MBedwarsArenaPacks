package me.metallicgoat.arenapacks.command;

import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.SubCommand;
import java.util.ArrayList;
import java.util.List;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.pack.PackExporter;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class ExportCommand implements CommandHandler {

  @Override
  public Plugin getPlugin() {
    return ArenaPacksPlugin.getInstance();
  }

  @Override
  public void onRegister(SubCommand command) {
    command.setOnlyForPlayers(false);
    command.setUsage("<arena> [packVersion]");
    command.setPermission("mbedwars.arenapacks.export");
  }

  @Override
  public void onFire(CommandSender sender, String label, String[] args) {
    if (args.length < 1 || args.length > 2) {
      sender.sendMessage("§cUsage: /" + label + " <arena> [packVersion]");
      return;
    }

    final Arena arena = GameAPI.get().getArenaByName(args[0]);

    if (arena == null) {
      sender.sendMessage("§cUnknown arena: " + args[0]);
      return;
    }

    int packVersion = 1;

    if (args.length == 2) {
      try {
        packVersion = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        sender.sendMessage("§cThe pack version must be a number, got: " + args[1]);
        return;
      }
    }

    PackExporter.export(sender, arena, packVersion);
  }

  @Override
  public @Nullable List<String> onAutocomplete(CommandSender sender, String[] args) {
    if (args.length != 1)
      return null;

    final List<String> names = new ArrayList<>();

    for (Arena arena : GameAPI.get().getArenas()) {
      if (arena.getName().toLowerCase().startsWith(args[0].toLowerCase()))
        names.add(arena.getName());
    }

    return names;
  }
}

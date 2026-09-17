package me.metallicgoat.arenapacks.command;

import de.marcely.bedwars.api.arena.RegenerationType;
import de.marcely.bedwars.api.command.CommandHandler;
import de.marcely.bedwars.api.command.SubCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import me.metallicgoat.arenapacks.pack.PackInstaller;
import me.metallicgoat.arenapacks.remote.RemoteIndex;
import me.metallicgoat.arenapacks.remote.RemoteIndexService;
import me.metallicgoat.arenapacks.util.Console;
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
    command.setUsage("<world|region> <packName> [newArenaName]");
    command.setPermission("mbedwars.arenapacks.install");
  }

  @Override
  public void onFire(CommandSender sender, String label, String[] args) {
    if (args.length < 2 || args.length > 3) {
      Console.send(sender, "§cUsage: /" + label + " <world|region> <packName> [newArenaName]");
      return;
    }

    final RegenerationType regenType = PackInstaller.parseInstallType(args[0]);

    if (regenType == null) {
      Console.send(sender, "§c'" + args[0] + "' is not a valid arena type. Use 'world' or 'region'.");
      return;
    }

    final String packName = args[1];
    final String overrideName = args.length == 3 ? args[2] : null;

    PackInstaller.install(sender, packName, overrideName, regenType, false, result -> { });
  }

  @Override
  public @Nullable List<String> onAutocomplete(CommandSender sender, String[] args) {
    if (args.length == 1)
      return filterPrefix(Arrays.asList("world", "region"), args[0]);
    if (args.length != 2)
      return null;

    final RemoteIndex index = RemoteIndexService.getCachedIndex();

    if (index == null)
      return null;

    final List<String> names = new ArrayList<>();

    for (String path : index.packs) {
      if (path == null || path.isEmpty())
        continue;

      final String name = RemoteIndex.directoryName(path);

      if (name.toLowerCase().startsWith(args[1].toLowerCase()))
        names.add(name);
    }

    return names;
  }

  static List<String> filterPrefix(List<String> options, String typed) {
    final List<String> result = new ArrayList<>();

    for (String option : options) {
      if (option.startsWith(typed.toLowerCase()))
        result.add(option);
    }

    return result;
  }
}

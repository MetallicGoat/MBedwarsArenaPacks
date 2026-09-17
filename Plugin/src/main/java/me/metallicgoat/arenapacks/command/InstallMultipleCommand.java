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

/**
 * Installs several packs one after another. Also what MBedwars' web setup
 * dispatches for the maps picked in the browser, so it must stay usable from
 * the console and safe to re-run (existing arenas are skipped).
 */
public class InstallMultipleCommand implements CommandHandler {

  @Override
  public Plugin getPlugin() {
    return ArenaPacksPlugin.getInstance();
  }

  @Override
  public void onRegister(SubCommand command) {
    command.setOnlyForPlayers(false);
    command.setUsage("<world|region> <packName> [packName...]");
    command.setPermission("mbedwars.arenapacks.install");
  }

  @Override
  public void onFire(CommandSender sender, String label, String[] args) {
    if (args.length < 2) {
      Console.send(sender, "§cUsage: /" + label + " <world|region> <packName> [packName...]");
      return;
    }

    final RegenerationType regenType = PackInstaller.parseInstallType(args[0]);

    if (regenType == null) {
      Console.send(sender, "§c'" + args[0] + "' is not a valid arena type. Use 'world' or 'region'.");
      return;
    }

    final List<String> packs = Arrays.asList(args).subList(1, args.length);

    Console.send(sender, "§7Installing " + packs.size() + " arena pack(s) as " + regenType.getId() + " arenas...");

    installNext(sender, packs, regenType, 0, new int[PackInstaller.Result.values().length]);
  }

  /** Imports hold the server-wide OperationLock, so each pack starts only once the previous one is done. */
  private static void installNext(CommandSender sender, List<String> packs, RegenerationType regenType,
                                  int index, int[] counts) {
    if (index >= packs.size()) {
      Console.send(sender, "§aFinished installing arena packs: " + counts[0] + " installed, "
          + counts[1] + " skipped (already exist), " + counts[2] + " skipped (incompatible), " + counts[3] + " failed.");
      return;
    }

    final String pack = packs.get(index);

    Console.send(sender, "§7[" + (index + 1) + "/" + packs.size() + "] " + pack);

    PackInstaller.install(sender, pack, null, regenType, true, result -> {
      counts[result.ordinal()]++;
      installNext(sender, packs, regenType, index + 1, counts);
    });
  }

  @Override
  public @Nullable List<String> onAutocomplete(CommandSender sender, String[] args) {
    if (args.length == 0)
      return null;
    if (args.length == 1)
      return InstallCommand.filterPrefix(Arrays.asList("world", "region"), args[0]);

    final RemoteIndex index = RemoteIndexService.getCachedIndex();

    if (index == null)
      return null;

    final String typed = args[args.length - 1].toLowerCase();
    final List<String> names = new ArrayList<>();

    for (String path : index.packs) {
      if (path == null || path.isEmpty())
        continue;

      final String name = RemoteIndex.directoryName(path);

      if (name.toLowerCase().startsWith(typed))
        names.add(name);
    }

    return names;
  }
}

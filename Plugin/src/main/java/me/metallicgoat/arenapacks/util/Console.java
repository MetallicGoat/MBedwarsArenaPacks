package me.metallicgoat.arenapacks.util;

import java.util.logging.Level;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

public class Console {

  public static void printInfo(String... messages) {
    for (String message : messages)
      ArenaPacksPlugin.getInstance().getLogger().log(Level.INFO, message);
  }

  public static void printWarn(String... messages) {
    for (String message : messages)
      ArenaPacksPlugin.getInstance().getLogger().log(Level.WARNING, message);
  }

  public static void printError(String... messages) {
    for (String message : messages)
      ArenaPacksPlugin.getInstance().getLogger().log(Level.SEVERE, message);
  }

  public static void send(CommandSender sender, String message) {
    if (sender instanceof ConsoleCommandSender) {
      final String plain = ChatColor.stripColor(message);

      if (message.startsWith("§c") || message.startsWith("§e"))
        printWarn(plain);
      else
        printInfo(plain);

      return;
    }

    sender.sendMessage("§8[§b" + ArenaPacksPlugin.getInstance().getName() + "§8] " + message);
  }

  public static boolean isConsole(CommandSender sender) {
    return sender instanceof ConsoleCommandSender;
  }
}

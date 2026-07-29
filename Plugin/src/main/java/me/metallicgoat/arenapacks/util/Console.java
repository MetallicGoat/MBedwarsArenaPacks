package me.metallicgoat.arenapacks.util;

import java.util.logging.Level;
import me.metallicgoat.arenapacks.ArenaPacksPlugin;

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
}

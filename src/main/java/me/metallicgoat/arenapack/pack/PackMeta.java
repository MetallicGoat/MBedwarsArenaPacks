package me.metallicgoat.arenapack.pack;

import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything stored in a pack's arena.yml. Plain data carrier — no Bukkit or
 * MBedwars lookups happen here, so instances may travel between threads.
 */
public class PackMeta {

  public static final int CURRENT_FORMAT_VERSION = 1;

  public int formatVersion = CURRENT_FORMAT_VERSION;

  // pack
  public String packName;
  public int packVersion = 1;
  public String exporterVersion;
  public int mbedwarsApiVersion;
  public String minecraftVersion;
  public String exportedAt;

  // world
  public String originalWorldName;

  // arena
  public String arenaName;
  public boolean customNameEnabled;
  public @Nullable String customName;
  public int minPlayers;
  public int playersPerTeam;
  public String regenTypeId;
  public String weatherType;
  public String timeType;
  public List<String> authors = new ArrayList<>();
  public @Nullable ItemStack icon;
  public XYZ regionMin;
  public XYZ regionMax;
  public @Nullable XYZYP spectatorSpawn;
  // key = Team enum name
  public Map<String, TeamData> teams = new LinkedHashMap<>();
  public List<SpawnerData> spawners = new ArrayList<>();
  public List<HologramData> holograms = new ArrayList<>();
  public @Nullable String persistentStorageDump;

  public static class TeamData {
    public @Nullable XYZYP spawn;
    public @Nullable XYZD bed;
    public List<EffectData> baseOnlyEffects = new ArrayList<>();
    public List<EffectData> permanentEffects = new ArrayList<>();
  }

  public static class EffectData {
    public String type;
    public int amplifier;

    public EffectData() {
    }

    public EffectData(String type, int amplifier) {
      this.type = type;
      this.amplifier = amplifier;
    }
  }

  public static class SpawnerData {
    public String dropTypeId;
    public XYZ location;

    public SpawnerData() {
    }

    public SpawnerData(String dropTypeId, XYZ location) {
      this.dropTypeId = dropTypeId;
      this.location = location;
    }
  }

  public static class HologramData {
    public String controllerType;
    public XYZYP location;

    public HologramData() {
    }

    public HologramData(String controllerType, XYZYP location) {
      this.controllerType = controllerType;
      this.location = location;
    }
  }
}

package me.metallicgoat.arenapack.pack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.metallicgoat.arenapack.util.LocCodec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Reads/writes a pack's arena.yml. Locations are stored as compact strings
 * (see {@link LocCodec}); the icon uses Bukkit's native ItemStack
 * serialization since it round-trips on any server.
 */
public class PackMetaCodec {

  public static final String META_FILE_NAME = "arena.yml";
  public static final String WORLD_DIR_NAME = "world";

  public static void write(PackMeta meta, File file) throws IOException {
    final YamlConfiguration config = new YamlConfiguration();

    config.set("format-version", meta.formatVersion);

    config.set("pack.name", meta.packName);
    config.set("pack.pack-version", meta.packVersion);
    config.set("pack.exported-by", meta.exporterVersion);
    config.set("pack.mbedwars-api-version", meta.mbedwarsApiVersion);
    config.set("pack.minecraft-version", meta.minecraftVersion);
    config.set("pack.exported-at", meta.exportedAt);

    config.set("world.original-name", meta.originalWorldName);

    config.set("arena.name", meta.arenaName);
    config.set("arena.custom-name-enabled", meta.customNameEnabled);
    config.set("arena.custom-name", meta.customName);
    config.set("arena.min-players", meta.minPlayers);
    config.set("arena.players-per-team", meta.playersPerTeam);
    config.set("arena.regeneration-type", meta.regenTypeId);
    config.set("arena.weather-type", meta.weatherType);
    config.set("arena.time-type", meta.timeType);
    config.set("arena.authors", meta.authors);

    if (meta.icon != null)
      config.set("arena.icon", meta.icon);

    config.set("arena.region.min", LocCodec.format(meta.regionMin));
    config.set("arena.region.max", LocCodec.format(meta.regionMax));

    if (meta.spectatorSpawn != null)
      config.set("arena.spectator-spawn", LocCodec.format(meta.spectatorSpawn));

    for (Map.Entry<String, PackMeta.TeamData> entry : meta.teams.entrySet()) {
      final String path = "arena.teams." + entry.getKey();
      final PackMeta.TeamData team = entry.getValue();

      if (team.spawn != null)
        config.set(path + ".spawn", LocCodec.format(team.spawn));
      if (team.bed != null)
        config.set(path + ".bed", LocCodec.format(team.bed));
      if (!team.baseOnlyEffects.isEmpty())
        config.set(path + ".effects.base-only", writeEffects(team.baseOnlyEffects));
      if (!team.permanentEffects.isEmpty())
        config.set(path + ".effects.permanent", writeEffects(team.permanentEffects));
    }

    final List<Map<String, Object>> spawners = new ArrayList<>();

    for (PackMeta.SpawnerData spawner : meta.spawners) {
      final Map<String, Object> map = new LinkedHashMap<>();

      map.put("type", spawner.dropTypeId);
      map.put("location", LocCodec.format(spawner.location));
      spawners.add(map);
    }

    config.set("arena.spawners", spawners);

    final List<Map<String, Object>> holograms = new ArrayList<>();

    for (PackMeta.HologramData hologram : meta.holograms) {
      final Map<String, Object> map = new LinkedHashMap<>();

      map.put("controller", hologram.controllerType);
      map.put("location", LocCodec.format(hologram.location));
      holograms.add(map);
    }

    config.set("arena.holograms", holograms);

    if (meta.persistentStorageDump != null && !meta.persistentStorageDump.isEmpty())
      config.set("arena.persistent-storage", meta.persistentStorageDump);

    config.save(file);
  }

  public static PackMeta read(File file) throws IOException, InvalidConfigurationException {
    final YamlConfiguration config = new YamlConfiguration();

    config.load(file);

    final PackMeta meta = new PackMeta();

    meta.formatVersion = config.getInt("format-version", -1);

    if (meta.formatVersion != PackMeta.CURRENT_FORMAT_VERSION)
      throw new InvalidConfigurationException(
          "Unsupported pack format version " + meta.formatVersion
              + " (this plugin supports version " + PackMeta.CURRENT_FORMAT_VERSION + ")");

    meta.packName = config.getString("pack.name");
    meta.packVersion = config.getInt("pack.pack-version", 1);
    meta.exporterVersion = config.getString("pack.exported-by");
    meta.mbedwarsApiVersion = config.getInt("pack.mbedwars-api-version", 0);
    meta.minecraftVersion = config.getString("pack.minecraft-version");
    meta.exportedAt = config.getString("pack.exported-at");

    meta.originalWorldName = config.getString("world.original-name");

    meta.arenaName = config.getString("arena.name");

    if (meta.arenaName == null || meta.arenaName.isEmpty())
      throw new InvalidConfigurationException("Pack is missing 'arena.name'");

    meta.customNameEnabled = config.getBoolean("arena.custom-name-enabled", false);
    meta.customName = config.getString("arena.custom-name");
    meta.minPlayers = config.getInt("arena.min-players", 0);
    meta.playersPerTeam = config.getInt("arena.players-per-team", 1);
    meta.regenTypeId = config.getString("arena.regeneration-type");
    meta.weatherType = config.getString("arena.weather-type");
    meta.timeType = config.getString("arena.time-type");
    meta.authors = config.getStringList("arena.authors");
    meta.icon = config.getItemStack("arena.icon");

    final String regionMin = config.getString("arena.region.min");
    final String regionMax = config.getString("arena.region.max");

    if (regionMin == null || regionMax == null)
      throw new InvalidConfigurationException("Pack is missing region corners");

    meta.regionMin = LocCodec.parseXYZ(regionMin);
    meta.regionMax = LocCodec.parseXYZ(regionMax);

    final String spectatorSpawn = config.getString("arena.spectator-spawn");

    if (spectatorSpawn != null)
      meta.spectatorSpawn = LocCodec.parseXYZYP(spectatorSpawn);

    final ConfigurationSection teamsSection = config.getConfigurationSection("arena.teams");

    if (teamsSection != null) {
      for (String teamName : teamsSection.getKeys(false)) {
        final ConfigurationSection teamSection = teamsSection.getConfigurationSection(teamName);

        if (teamSection == null)
          continue;

        final PackMeta.TeamData team = new PackMeta.TeamData();
        final String spawn = teamSection.getString("spawn");
        final String bed = teamSection.getString("bed");

        if (spawn != null)
          team.spawn = LocCodec.parseXYZYP(spawn);
        if (bed != null)
          team.bed = LocCodec.parseXYZD(bed);

        team.baseOnlyEffects = readEffects(teamSection.getMapList("effects.base-only"));
        team.permanentEffects = readEffects(teamSection.getMapList("effects.permanent"));

        meta.teams.put(teamName, team);
      }
    }

    for (Map<?, ?> map : config.getMapList("arena.spawners")) {
      final Object type = map.get("type");
      final Object location = map.get("location");

      if (type == null || location == null)
        throw new InvalidConfigurationException("Invalid spawner entry: " + map);

      meta.spawners.add(new PackMeta.SpawnerData(type.toString(), LocCodec.parseXYZ(location.toString())));
    }

    for (Map<?, ?> map : config.getMapList("arena.holograms")) {
      final Object controller = map.get("controller");
      final Object location = map.get("location");

      if (controller == null || location == null)
        throw new InvalidConfigurationException("Invalid hologram entry: " + map);

      meta.holograms.add(new PackMeta.HologramData(controller.toString(), LocCodec.parseXYZYP(location.toString())));
    }

    meta.persistentStorageDump = config.getString("arena.persistent-storage");

    return meta;
  }

  private static List<Map<String, Object>> writeEffects(List<PackMeta.EffectData> effects) {
    final List<Map<String, Object>> list = new ArrayList<>();

    for (PackMeta.EffectData effect : effects) {
      final Map<String, Object> map = new LinkedHashMap<>();

      map.put("type", effect.type);
      map.put("amplifier", effect.amplifier);
      list.add(map);
    }

    return list;
  }

  private static List<PackMeta.EffectData> readEffects(List<Map<?, ?>> maps) {
    final List<PackMeta.EffectData> effects = new ArrayList<>();

    for (Map<?, ?> map : maps) {
      final Object type = map.get("type");
      final Object amplifier = map.get("amplifier");

      if (type == null)
        continue;

      effects.add(new PackMeta.EffectData(
          type.toString(),
          amplifier instanceof Number ? ((Number) amplifier).intValue() : 0));
    }

    return effects;
  }
}

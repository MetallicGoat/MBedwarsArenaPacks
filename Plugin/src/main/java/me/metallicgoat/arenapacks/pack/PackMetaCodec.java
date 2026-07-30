package me.metallicgoat.arenapacks.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import me.metallicgoat.arenapacks.util.LocJson;
import org.jetbrains.annotations.Nullable;

/**
 * Reads/writes a pack's arena.json — the human-editable half of a pack, which
 * sits next to the (never-changing) world.zip so coordinates and settings can
 * be reviewed and tweaked in git.
 */
public class PackMetaCodec {

  public static final String META_FILE_NAME = "arena.json";
  public static final String WORLD_ZIP_NAME = "world.zip";

  private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .serializeNulls()
      // Colour codes are full of '&'; escaping them would make the file unreadable
      .disableHtmlEscaping()
      .create();

  public static void write(PackMeta meta, File file) throws IOException {
    final JsonObject root = new JsonObject();

    root.addProperty("format", meta.formatVersion);

    final JsonObject pack = new JsonObject();

    pack.addProperty("name", meta.packName);
    pack.addProperty("version", meta.packVersion);
    pack.addProperty("exported-by", meta.exporterVersion);
    pack.addProperty("exported-at", meta.exportedAt);
    pack.addProperty("minecraft-version", meta.minecraftVersion);
    pack.addProperty("mbedwars-api-version", meta.mbedwarsApiVersion);
    pack.addProperty("original-world-name", meta.originalWorldName);
    root.add("pack", pack);

    final JsonObject arena = new JsonObject();

    arena.addProperty("name", meta.arenaName);
    arena.addProperty("custom-name", meta.customName);
    arena.addProperty("custom-name-enabled", meta.customNameEnabled);
    arena.addProperty("min-players", meta.minPlayers);
    arena.addProperty("players-per-team", meta.playersPerTeam);
    arena.addProperty("regeneration-type", meta.regenTypeId);
    arena.addProperty("weather-type", meta.weatherType);
    arena.addProperty("time-type", meta.timeType);

    final JsonArray authors = new JsonArray();

    for (String author : meta.authors)
      authors.add(new JsonPrimitive(author));

    arena.add("authors", authors);

    final JsonObject region = new JsonObject();

    region.add("min", LocJson.write(meta.regionMin));
    region.add("max", LocJson.write(meta.regionMax));
    arena.add("region", region);

    arena.add("spectator-spawn", meta.spectatorSpawn != null ? LocJson.write(meta.spectatorSpawn) : null);
    arena.add("lobby", meta.lobby != null ? LocJson.write(meta.lobby) : null);

    final JsonObject teams = new JsonObject();

    for (Map.Entry<String, PackMeta.TeamData> entry : meta.teams.entrySet()) {
      final PackMeta.TeamData data = entry.getValue();
      final JsonObject team = new JsonObject();

      team.add("spawn", data.spawn != null ? LocJson.write(data.spawn) : null);
      team.add("bed", data.bed != null ? LocJson.write(data.bed) : null);

      teams.add(entry.getKey(), team);
    }

    arena.add("teams", teams);

    final JsonArray spawners = new JsonArray();

    for (PackMeta.SpawnerData data : meta.spawners) {
      final JsonObject spawner = new JsonObject();

      spawner.addProperty("type", data.dropTypeId);
      spawner.add("location", LocJson.write(data.location));
      spawners.add(spawner);
    }

    arena.add("spawners", spawners);

    final JsonArray holograms = new JsonArray();

    for (PackMeta.HologramData data : meta.holograms) {
      final JsonObject hologram = new JsonObject();

      hologram.addProperty("controller", data.controllerType);
      hologram.add("location", LocJson.write(data.location));
      holograms.add(hologram);
    }

    arena.add("holograms", holograms);
    root.add("arena", arena);

    try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
      GSON.toJson(root, writer);
      writer.write('\n');
    }
  }

  public static PackMeta read(File file) throws IOException {
    if (!file.isFile())
      throw new InvalidPackException("Pack is missing its " + META_FILE_NAME);

    try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
      return read(reader);
    }
  }

  public static PackMeta read(Reader reader) throws IOException {
    final JsonElement parsed;

    try {
      parsed = new JsonParser().parse(reader);
    } catch (RuntimeException e) {
      throw new InvalidPackException(META_FILE_NAME + " is not valid JSON: " + e.getMessage());
    }

    if (parsed == null || !parsed.isJsonObject())
      throw new InvalidPackException(META_FILE_NAME + " is empty or not a JSON object");

    try {
      return read(parsed.getAsJsonObject());
    } catch (RuntimeException e) {
      throw new InvalidPackException("Malformed " + META_FILE_NAME + ": " + e.getMessage());
    }
  }

  private static PackMeta read(JsonObject root) throws InvalidPackException {
    final PackMeta meta = new PackMeta();

    meta.formatVersion = getInt(root, "format", -1);

    if (meta.formatVersion != PackMeta.CURRENT_FORMAT_VERSION)
      throw new InvalidPackException(
          "Unsupported pack format version " + meta.formatVersion
              + " (this plugin supports version " + PackMeta.CURRENT_FORMAT_VERSION + ")");

    final JsonObject pack = getObject(root, "pack");

    if (pack != null) {
      meta.packName = getString(pack, "name");
      meta.packVersion = getInt(pack, "version", 1);
      meta.exporterVersion = getString(pack, "exported-by");
      meta.exportedAt = getString(pack, "exported-at");
      meta.minecraftVersion = getString(pack, "minecraft-version");
      meta.mbedwarsApiVersion = getInt(pack, "mbedwars-api-version", 0);
      meta.originalWorldName = getString(pack, "original-world-name");
    }

    final JsonObject arena = getObject(root, "arena");

    if (arena == null)
      throw new InvalidPackException("Pack is missing the 'arena' section");

    meta.arenaName = getString(arena, "name");

    if (meta.arenaName == null || meta.arenaName.isEmpty())
      throw new InvalidPackException("Pack is missing 'arena.name'");

    if (meta.packName == null)
      meta.packName = meta.arenaName;

    meta.customName = PackMeta.customNameOr(getString(arena, "custom-name"), meta.arenaName);
    meta.customNameEnabled = getBoolean(arena, "custom-name-enabled");
    meta.minPlayers = getInt(arena, "min-players", 0);
    meta.playersPerTeam = getInt(arena, "players-per-team", 1);
    meta.regenTypeId = getString(arena, "regeneration-type");
    meta.weatherType = getString(arena, "weather-type");
    meta.timeType = getString(arena, "time-type");

    for (JsonElement author : getArray(arena, "authors"))
      meta.authors.add(author.getAsString());

    final JsonObject region = getObject(arena, "region");
    final JsonObject regionMin = region != null ? getObject(region, "min") : null;
    final JsonObject regionMax = region != null ? getObject(region, "max") : null;

    if (regionMin == null || regionMax == null)
      throw new InvalidPackException("Pack is missing 'arena.region.min' or 'arena.region.max'");

    meta.regionMin = LocJson.readXYZ(regionMin);
    meta.regionMax = LocJson.readXYZ(regionMax);

    final JsonObject spectatorSpawn = getObject(arena, "spectator-spawn");

    if (spectatorSpawn != null)
      meta.spectatorSpawn = LocJson.readXYZYP(spectatorSpawn);

    final JsonObject lobby = getObject(arena, "lobby");

    if (lobby != null)
      meta.lobby = LocJson.readXYZYP(lobby);

    final JsonObject teams = getObject(arena, "teams");

    if (teams != null) {
      for (Map.Entry<String, JsonElement> entry : teams.entrySet()) {
        if (!entry.getValue().isJsonObject())
          continue;

        final JsonObject team = entry.getValue().getAsJsonObject();
        final PackMeta.TeamData data = new PackMeta.TeamData();
        final JsonObject spawn = getObject(team, "spawn");
        final JsonObject bed = getObject(team, "bed");

        if (spawn != null)
          data.spawn = LocJson.readXYZYP(spawn);
        if (bed != null)
          data.bed = LocJson.readXYZD(bed);

        meta.teams.put(entry.getKey(), data);
      }
    }

    for (JsonElement element : getArray(arena, "spawners")) {
      final JsonObject spawner = element.getAsJsonObject();
      final String type = getString(spawner, "type");
      final JsonObject location = getObject(spawner, "location");

      if (type == null || location == null)
        throw new InvalidPackException("Invalid spawner entry: " + spawner);

      meta.spawners.add(new PackMeta.SpawnerData(type, LocJson.readXYZ(location)));
    }

    for (JsonElement element : getArray(arena, "holograms")) {
      final JsonObject hologram = element.getAsJsonObject();
      final String controller = getString(hologram, "controller");
      final JsonObject location = getObject(hologram, "location");

      if (controller == null || location == null)
        throw new InvalidPackException("Invalid hologram entry: " + hologram);

      meta.holograms.add(new PackMeta.HologramData(controller, LocJson.readXYZYP(location)));
    }

    return meta;
  }

  private static @Nullable JsonElement get(JsonObject json, String key) {
    final JsonElement element = json.get(key);

    return element == null || element.isJsonNull() ? null : element;
  }

  private static @Nullable String getString(JsonObject json, String key) {
    final JsonElement element = get(json, key);

    return element != null ? element.getAsString() : null;
  }

  private static int getInt(JsonObject json, String key, int fallback) {
    final JsonElement element = get(json, key);

    return element != null ? element.getAsInt() : fallback;
  }

  private static boolean getBoolean(JsonObject json, String key) {
    final JsonElement element = get(json, key);

    return element != null && element.getAsBoolean();
  }

  private static @Nullable JsonObject getObject(JsonObject json, String key) {
    final JsonElement element = get(json, key);

    return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
  }

  private static JsonArray getArray(JsonObject json, String key) {
    final JsonElement element = get(json, key);

    return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
  }
}

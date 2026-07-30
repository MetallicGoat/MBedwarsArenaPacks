package me.metallicgoat.arenapacks;

import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import me.metallicgoat.arenapacks.pack.PackMeta;
import me.metallicgoat.arenapacks.pack.PackMetaCodec;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PackMetaCodecTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void roundTrip() throws Exception {
    final File file = new File(tmp.getRoot(), PackMetaCodec.META_FILE_NAME);

    PackMetaCodec.write(sampleMeta(), file);

    final PackMeta read = PackMetaCodec.read(file);

    assertEquals(PackMeta.CURRENT_FORMAT_VERSION, read.formatVersion);
    assertEquals("Amazonia", read.packName);
    assertEquals(3, read.packVersion);
    assertEquals("1.21.4", read.minecraftVersion);
    assertEquals(208, read.mbedwarsApiVersion);
    assertEquals("bw_amazonia", read.originalWorldName);

    assertEquals("Amazonia", read.arenaName);
    assertTrue(read.customNameEnabled);
    assertEquals("&aAmazonia", read.customName);
    assertEquals(4, read.minPlayers);
    assertEquals(2, read.playersPerTeam);
    assertEquals("region", read.regenTypeId);
    assertEquals("UNTOUCHED", read.weatherType);
    assertEquals("NOON", read.timeType);
    assertEquals(Arrays.asList("MetallicGoat", "SomeoneElse"), read.authors);

    assertEquals(10, read.regionMin.getX(), 0);
    assertEquals(210, read.regionMax.getX(), 0);
    assertNotNull(read.spectatorSpawn);
    assertEquals(90, read.spectatorSpawn.getY(), 0);
    assertNotNull(read.lobby);
    assertEquals(100.5, read.lobby.getX(), 0);
    assertEquals(91, read.lobby.getY(), 0);
    assertEquals(180f, read.lobby.getYaw(), 0);

    assertEquals(2, read.teams.size());

    final PackMeta.TeamData readRed = read.teams.get("RED");

    assertNotNull(readRed);
    assertNotNull(readRed.spawn);
    assertEquals(90f, readRed.spawn.getYaw(), 0);
    assertNotNull(readRed.bed);
    assertEquals(XYZD.Direction.WEST, readRed.bed.getDirection());

    final PackMeta.TeamData readBlue = read.teams.get("BLUE");

    assertNotNull(readBlue);
    assertNull(readBlue.bed);

    assertEquals(2, read.spawners.size());
    assertEquals("iron", read.spawners.get(0).dropTypeId);
    assertEquals(30.5, read.spawners.get(0).location.getX(), 0);

    assertEquals(1, read.holograms.size());
    assertEquals("DEALER", read.holograms.get(0).controllerType);
    assertEquals(180f, read.holograms.get(0).location.getYaw(), 0);
  }

  /** Team effects and addon persistent storage are deliberately not part of a pack. */
  @Test
  public void writesNoEffectsOrPersistentStorage() throws Exception {
    final File file = new File(tmp.getRoot(), PackMetaCodec.META_FILE_NAME);

    PackMetaCodec.write(sampleMeta(), file);

    final String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

    assertFalse(json.contains("effects"));
    assertFalse(json.contains("persistent-storage"));
  }

  /** Packs written by older builds carry fields the format dropped; ignore them. */
  @Test
  public void ignoresRetiredFields() throws Exception {
    final File file = new File(tmp.getRoot(), PackMetaCodec.META_FILE_NAME);

    PackMetaCodec.write(sampleMeta(), file);

    final String withRetired = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
        .replace("\"min-players\": 4,",
            "\"min-players\": 4,\n    \"persistent-storage\": \"some: junk\\n\",")
        .replace("\"version\": 3,",
            "\"version\": 3,\n    \"description\": \"stale\",\n    \"min-mbedwars-api\": 208,");

    Files.write(file.toPath(), withRetired.getBytes(StandardCharsets.UTF_8));

    final PackMeta read = PackMetaCodec.read(file);

    assertEquals("Amazonia", read.arenaName);
    assertEquals(4, read.minPlayers);
    assertEquals(2, read.teams.size());
  }

  /** The whole point of the format: coordinates are readable and editable. */
  @Test
  public void writesEditableJson() throws Exception {
    final File file = new File(tmp.getRoot(), PackMetaCodec.META_FILE_NAME);

    PackMetaCodec.write(sampleMeta(), file);

    final String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

    assertTrue(json.contains("\"x\": 20.5"));
    assertTrue(json.contains("\"direction\": \"WEST\""));
    // Colour codes must stay readable rather than being escaped to &
    assertTrue(json.contains("\"custom-name\": \"&aAmazonia\""));
  }

  @Test
  public void handEditedCoordinateIsRead() throws Exception {
    final File file = new File(tmp.getRoot(), PackMetaCodec.META_FILE_NAME);

    PackMetaCodec.write(sampleMeta(), file);

    final String edited = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
        .replace("\"x\": 20.5", "\"x\": 21.5");

    Files.write(file.toPath(), edited.getBytes(StandardCharsets.UTF_8));

    final PackMeta read = PackMetaCodec.read(file);

    assertEquals(21.5, read.teams.get("RED").spawn.getX(), 0);
  }

  @Test(expected = Exception.class)
  public void rejectsUnknownFormatVersion() throws Exception {
    read("{\"format\": 99, \"arena\": {\"name\": \"Foo\"}}");
  }

  @Test(expected = Exception.class)
  public void rejectsMissingArenaName() throws Exception {
    read("{\"format\": 1, \"arena\": {}}");
  }

  @Test(expected = Exception.class)
  public void rejectsMissingRegion() throws Exception {
    read("{\"format\": 1, \"arena\": {\"name\": \"Foo\"}}");
  }

  @Test(expected = Exception.class)
  public void rejectsBrokenJson() throws Exception {
    read("{\"format\": 1, ");
  }

  @Test(expected = Exception.class)
  public void rejectsMissingFile() throws Exception {
    PackMetaCodec.read(new File(tmp.getRoot(), "nope.json"));
  }

  private void read(String json) throws Exception {
    final File file = new File(tmp.getRoot(), "bad.json");

    Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));

    PackMetaCodec.read(file);
  }

  private static PackMeta sampleMeta() {
    final PackMeta meta = new PackMeta();

    meta.packName = "Amazonia";
    meta.packVersion = 3;
    meta.exporterVersion = "MBedwarsArenaPacks 1.0.0";
    meta.exportedAt = "2026-07-20T18:32:11Z";
    meta.minecraftVersion = "1.21.4";
    meta.mbedwarsApiVersion = 208;
    meta.originalWorldName = "bw_amazonia";

    meta.arenaName = "Amazonia";
    meta.customNameEnabled = true;
    meta.customName = "&aAmazonia";
    meta.minPlayers = 4;
    meta.playersPerTeam = 2;
    meta.regenTypeId = "region";
    meta.weatherType = "UNTOUCHED";
    meta.timeType = "NOON";
    meta.authors = Arrays.asList("MetallicGoat", "SomeoneElse");
    meta.regionMin = new XYZ(10, 40, 10);
    meta.regionMax = new XYZ(210, 120, 210);
    meta.spectatorSpawn = new XYZYP(100.5, 90, 100.5, 0f, 0f);
    meta.lobby = new XYZYP(100.5, 91, 100.5, 180f, 0f);

    final PackMeta.TeamData red = new PackMeta.TeamData();

    red.spawn = new XYZYP(20.5, 78, 100.5, 90f, 0f);
    red.bed = new XYZD(25, 78, 100, XYZD.Direction.WEST);
    meta.teams.put("RED", red);

    final PackMeta.TeamData blue = new PackMeta.TeamData();

    blue.spawn = new XYZYP(180.5, 78, 100.5, 270f, 0f);
    meta.teams.put("BLUE", blue);

    meta.spawners.add(new PackMeta.SpawnerData("iron", new XYZ(30.5, 78, 100.5)));
    meta.spawners.add(new PackMeta.SpawnerData("emerald", new XYZ(105.5, 80, 105.5)));

    meta.holograms.add(new PackMeta.HologramData("DEALER", new XYZYP(22.5, 78, 98.5, 180f, 0f)));

    return meta;
  }
}

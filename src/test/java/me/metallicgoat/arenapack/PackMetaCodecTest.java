package me.metallicgoat.arenapack;

import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;
import java.io.File;
import java.util.Arrays;
import me.metallicgoat.arenapack.pack.PackMeta;
import me.metallicgoat.arenapack.pack.PackMetaCodec;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PackMetaCodecTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  // Icon (ItemStack) is not covered here: its deserialization needs a running
  // server's ConfigurationSerialization registration.
  @Test
  public void roundTrip() throws Exception {
    final PackMeta meta = new PackMeta();

    meta.packName = "Amazonia";
    meta.packVersion = 3;
    meta.exporterVersion = "MBedwarsArenaPack 1.0.0";
    meta.mbedwarsApiVersion = 208;
    meta.minecraftVersion = "1.21.4";
    meta.exportedAt = "2026-07-20T18:32:11Z";
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

    final PackMeta.TeamData red = new PackMeta.TeamData();

    red.spawn = new XYZYP(20.5, 78, 100.5, 90f, 0f);
    red.bed = new XYZD(25, 78, 100, XYZD.Direction.WEST);
    red.baseOnlyEffects.add(new PackMeta.EffectData("SPEED", 1));
    meta.teams.put("RED", red);

    final PackMeta.TeamData blue = new PackMeta.TeamData();

    blue.spawn = new XYZYP(180.5, 78, 100.5, 270f, 0f);
    meta.teams.put("BLUE", blue);

    meta.spawners.add(new PackMeta.SpawnerData("iron", new XYZ(30.5, 78, 100.5)));
    meta.spawners.add(new PackMeta.SpawnerData("emerald", new XYZ(105.5, 80, 105.5)));

    meta.holograms.add(new PackMeta.HologramData("DEALER", new XYZYP(22.5, 78, 98.5, 180f, 0f)));

    meta.persistentStorageDump = "some: opaque\ncontent: here\n";

    final File file = new File(tmp.getRoot(), "arena.yml");

    PackMetaCodec.write(meta, file);

    final PackMeta read = PackMetaCodec.read(file);

    assertEquals(PackMeta.CURRENT_FORMAT_VERSION, read.formatVersion);
    assertEquals("Amazonia", read.packName);
    assertEquals(3, read.packVersion);
    assertEquals(208, read.mbedwarsApiVersion);
    assertEquals("bw_amazonia", read.originalWorldName);

    assertEquals("Amazonia", read.arenaName);
    assertEquals(true, read.customNameEnabled);
    assertEquals("&aAmazonia", read.customName);
    assertEquals(4, read.minPlayers);
    assertEquals(2, read.playersPerTeam);
    assertEquals("region", read.regenTypeId);
    assertEquals(Arrays.asList("MetallicGoat", "SomeoneElse"), read.authors);
    assertNull(read.icon);

    assertEquals(10, read.regionMin.getX(), 0);
    assertEquals(210, read.regionMax.getX(), 0);
    assertNotNull(read.spectatorSpawn);
    assertEquals(90, read.spectatorSpawn.getY(), 0);

    assertEquals(2, read.teams.size());

    final PackMeta.TeamData readRed = read.teams.get("RED");

    assertNotNull(readRed);
    assertNotNull(readRed.spawn);
    assertEquals(90f, readRed.spawn.getYaw(), 0);
    assertNotNull(readRed.bed);
    assertEquals(XYZD.Direction.WEST, readRed.bed.getDirection());
    assertEquals(1, readRed.baseOnlyEffects.size());
    assertEquals("SPEED", readRed.baseOnlyEffects.get(0).type);
    assertEquals(1, readRed.baseOnlyEffects.get(0).amplifier);

    final PackMeta.TeamData readBlue = read.teams.get("BLUE");

    assertNotNull(readBlue);
    assertNull(readBlue.bed);

    assertEquals(2, read.spawners.size());
    assertEquals("iron", read.spawners.get(0).dropTypeId);
    assertEquals(30.5, read.spawners.get(0).location.getX(), 0);

    assertEquals(1, read.holograms.size());
    assertEquals("DEALER", read.holograms.get(0).controllerType);
    assertEquals(180f, read.holograms.get(0).location.getYaw(), 0);

    assertEquals("some: opaque\ncontent: here\n", read.persistentStorageDump);
  }

  @Test(expected = Exception.class)
  public void rejectsUnknownFormatVersion() throws Exception {
    final File file = new File(tmp.getRoot(), "bad.yml");

    java.nio.file.Files.write(file.toPath(),
        "format-version: 99\narena:\n  name: Foo\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    PackMetaCodec.read(file);
  }
}

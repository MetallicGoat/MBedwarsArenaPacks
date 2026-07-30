package me.metallicgoat.arenapacks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import me.metallicgoat.arenapacks.util.WorldFiles;
import me.metallicgoat.arenapacks.util.ZipUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZipUtilTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void roundTrip() throws IOException {
    final File source = tmp.newFolder("world");

    write(new File(source, "level.dat"), "level");
    write(new File(source, "region/r.0.0.mca"), "fake region data");

    final File zip = new File(tmp.getRoot(), "world.zip");

    ZipUtil.zip(source, zip);

    final File out = tmp.newFolder("out");

    ZipUtil.unzip(zip, out);

    // The world folder's contents land at the zip root, not under world/
    assertEquals("level", read(new File(out, "level.dat")));
    assertEquals("fake region data", read(new File(out, "region/r.0.0.mca")));
  }

  @Test
  public void zipHonorsExcludes() throws IOException {
    final File source = tmp.newFolder("world");

    write(new File(source, "level.dat"), "level");
    write(new File(source, "uid.dat"), "uuid");
    write(new File(source, "playerdata/someone.dat"), "player");
    write(new File(source, "region/r.0.0.mca"), "region");

    final File zip = new File(tmp.getRoot(), "world.zip");

    ZipUtil.zip(source, zip, WorldFiles::isExcludedFromPack);

    final File out = tmp.newFolder("out");

    ZipUtil.unzip(zip, out);

    assertTrue(new File(out, "level.dat").isFile());
    assertTrue(new File(out, "region/r.0.0.mca").isFile());
    assertFalse(new File(out, "uid.dat").exists());
    assertFalse(new File(out, "playerdata").exists());
  }

  /**
   * A single-player world keeps its nether and end inline as DIM-1/DIM1. Spigot
   * splits dimensions into separate world folders, so shipping these makes the
   * server log "Failed to delete directory ... not empty" on every import.
   */
  @Test
  public void zipSkipsForeignDimensionsAndForgeLeftovers() throws IOException {
    final File source = tmp.newFolder("world");

    write(new File(source, "level.dat"), "level");
    write(new File(source, "level.dat_old.gz"), "gzipped level.dat backup");
    write(new File(source, "region/r.0.0.mca"), "region");
    write(new File(source, "forcedchunks.dat"), "forge chunk loaders");
    write(new File(source, "DIM-1/forcedchunks.dat"), "nether");
    write(new File(source, "DIM-1/data/villages_nether.dat"), "nether villages");
    write(new File(source, "DIM1/forcedchunks.dat"), "end");
    write(new File(source, "DIM1/data/villages_end.dat"), "end villages");
    write(new File(source, "data/villages.dat"), "overworld villages");

    final File zip = new File(tmp.getRoot(), "world.zip");

    ZipUtil.zip(source, zip, WorldFiles::isExcludedFromPack);

    final File out = tmp.newFolder("out");

    ZipUtil.unzip(zip, out);

    assertTrue(new File(out, "level.dat").isFile());
    assertTrue(new File(out, "region/r.0.0.mca").isFile());
    // data/ stays: it can hold map_*.dat for maps placed in item frames
    assertTrue(new File(out, "data/villages.dat").isFile());

    assertFalse(new File(out, "DIM-1").exists());
    assertFalse(new File(out, "DIM1").exists());
    assertFalse(new File(out, "forcedchunks.dat").exists());
    assertFalse(new File(out, "level.dat_old.gz").exists());
  }

  /** Builders park world backups and notes next to level.dat. */
  @Test
  public void zipSkipsBackupsAndNotes() throws IOException {
    final File source = tmp.newFolder("world");

    write(new File(source, "level.dat"), "level");
    write(new File(source, "world-backup-2026-07-01.zip"), "old backup");
    write(new File(source, "BACKUP.ZIP"), "shouty backup");
    write(new File(source, "notes.txt"), "todo: move the diamond gen");
    // Zipped datapacks are functional, so nesting is left alone
    write(new File(source, "datapacks/mypack.zip"), "datapack");

    final File zip = new File(tmp.getRoot(), "world.zip");

    ZipUtil.zip(source, zip, WorldFiles::isExcludedFromPack);

    final File out = tmp.newFolder("out");

    ZipUtil.unzip(zip, out);

    assertTrue(new File(out, "level.dat").isFile());
    assertFalse(new File(out, "world-backup-2026-07-01.zip").exists());
    assertFalse(new File(out, "BACKUP.ZIP").exists());
    assertFalse(new File(out, "notes.txt").exists());
    assertTrue(new File(out, "datapacks/mypack.zip").isFile());
  }

  /**
   * Re-exporting an untouched world must not churn the zip in git, so the same
   * input has to produce byte-identical output.
   */
  @Test
  public void zipIsDeterministic() throws IOException {
    final File source = tmp.newFolder("world");

    write(new File(source, "level.dat"), "level");
    write(new File(source, "region/r.0.0.mca"), "region");
    write(new File(source, "data/map_0.dat"), "map");

    final File first = new File(tmp.getRoot(), "first.zip");
    final File second = new File(tmp.getRoot(), "second.zip");

    ZipUtil.zip(source, first, WorldFiles::isExcludedFromPack);
    ZipUtil.zip(source, second, WorldFiles::isExcludedFromPack);

    assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()));
  }

  @Test
  public void blocksZipSlip() throws IOException {
    final File evil = new File(tmp.getRoot(), "evil.zip");

    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(evil))) {
      out.putNextEntry(new ZipEntry("../escaped.txt"));
      out.write("boom".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }

    final File target = tmp.newFolder("target");

    try {
      ZipUtil.unzip(evil, target);
      fail("Expected zip-slip entry to be rejected");
    } catch (IOException expected) {
      // expected
    }
  }

  private static void write(File file, String content) throws IOException {
    file.getParentFile().mkdirs();
    Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
  }

  private static String read(File file) throws IOException {
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}

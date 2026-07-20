package me.metallicgoat.arenapack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import me.metallicgoat.arenapack.util.WorldFiles;
import me.metallicgoat.arenapack.util.ZipUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZipUtilTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void roundTrip() throws IOException {
    final File source = tmp.newFolder("source");

    write(new File(source, "arena.yml"), "hello: world");
    write(new File(source, "world/region/r.0.0.mca"), "fake region data");

    final File zip = new File(tmp.getRoot(), "pack.zip");

    ZipUtil.zip(source, zip);

    final File out = tmp.newFolder("out");

    ZipUtil.unzip(zip, out);

    assertEquals("hello: world", read(new File(out, "arena.yml")));
    assertEquals("fake region data", read(new File(out, "world/region/r.0.0.mca")));
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

  @Test
  public void copyDirectoryHonorsExcludes() throws IOException {
    final File source = tmp.newFolder("world");

    write(new File(source, "level.dat"), "level");
    write(new File(source, "uid.dat"), "uuid");
    write(new File(source, "playerdata/someone.dat"), "player");
    write(new File(source, "region/r.0.0.mca"), "region");

    final File target = new File(tmp.getRoot(), "copy");

    WorldFiles.copyDirectory(source, target, WorldFiles.WORLD_EXCLUDES);

    assertTrue(new File(target, "level.dat").isFile());
    assertTrue(new File(target, "region/r.0.0.mca").isFile());
    assertTrue(!new File(target, "uid.dat").exists());
    assertTrue(!new File(target, "playerdata").exists());
  }

  private static void write(File file, String content) throws IOException {
    file.getParentFile().mkdirs();
    Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
  }

  private static String read(File file) throws IOException {
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}

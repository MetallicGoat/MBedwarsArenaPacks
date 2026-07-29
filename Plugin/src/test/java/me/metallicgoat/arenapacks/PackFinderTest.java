package me.metallicgoat.arenapacks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import me.metallicgoat.arenapacks.pack.PackFinder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackFinderTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private File imports;
  private File exports;
  private File[] roots;

  @Before
  public void setUp() throws IOException {
    this.imports = this.tmp.newFolder("imports");
    this.exports = this.tmp.newFolder("exports");
    this.roots = new File[]{this.imports, this.exports};

    pack(new File(this.imports, "4-Teams/Aquarium"));
    pack(new File(this.imports, "2-Teams/Picnic"));
    pack(new File(this.imports, "Loose"));
    pack(new File(this.exports, "4-Teams/Glacier"));
  }

  @Test
  public void findsNestedPackByBareName() {
    assertEquals(new File(this.imports, "4-Teams/Aquarium"), single("Aquarium"));
  }

  @Test
  public void findsNestedPackByRelativePath() {
    assertEquals(new File(this.imports, "4-Teams/Aquarium"), single("4-Teams/Aquarium"));
  }

  @Test
  public void findsPackAtRootOfSearchFolder() {
    assertEquals(new File(this.imports, "Loose"), single("loose"));
  }

  @Test
  public void searchesEverySearchFolder() {
    assertEquals(new File(this.exports, "4-Teams/Glacier"), single("Glacier"));
  }

  @Test
  public void matchingIsCaseInsensitive() {
    assertEquals(new File(this.imports, "2-Teams/Picnic"), single("2-TEAMS/picnic"));
  }

  /** The traversal guard: a pack argument may contain ../ and must not escape. */
  @Test
  public void rejectsPathTraversal() throws IOException {
    final File outside = this.tmp.newFolder("outside");

    pack(new File(outside, "Secret"));

    assertTrue(PackFinder.find(this.roots, "../outside/Secret").isEmpty());
    assertTrue(PackFinder.find(this.roots, "../../outside/Secret").isEmpty());
    assertTrue(PackFinder.find(this.roots, "Secret").isEmpty());
  }

  @Test
  public void rejectsAbsolutePathAndEmptyName() throws IOException {
    final File outside = this.tmp.newFolder("elsewhere");

    pack(new File(outside, "Secret"));

    assertTrue(PackFinder.find(this.roots, new File(outside, "Secret").getAbsolutePath()).isEmpty());
    assertTrue(PackFinder.find(this.roots, "").isEmpty());
    assertTrue(PackFinder.find(this.roots, "   ").isEmpty());
  }

  @Test
  public void reportsEveryMatchForADuplicatedName() throws IOException {
    pack(new File(this.imports, "8-Teams/Aquarium"));

    final List<File> matches = PackFinder.find(this.roots, "Aquarium");

    assertEquals(2, matches.size());
    // A full path still resolves to exactly one
    assertEquals(new File(this.imports, "8-Teams/Aquarium"), single("8-Teams/Aquarium"));
  }

  @Test
  public void findAllReturnsEveryPackWithRelativeNames() {
    final List<String> names = new ArrayList<>();

    for (File pack : PackFinder.findAll(this.imports))
      names.add(PackFinder.relativeName(this.imports, pack));

    assertEquals(3, names.size());
    assertTrue(names.contains("4-Teams/Aquarium"));
    assertTrue(names.contains("2-Teams/Picnic"));
    assertTrue(names.contains("Loose"));
  }

  /** A pack is a leaf: the walk must not descend into the world's own folders. */
  @Test
  public void doesNotDescendIntoAPack() throws IOException {
    pack(new File(this.imports, "4-Teams/Aquarium/nested"));

    assertTrue(PackFinder.find(this.roots, "nested").isEmpty());
    assertEquals(3, PackFinder.findAll(this.imports).size());
  }

  @Test
  public void ignoresFoldersWithoutArenaJson() throws IOException {
    assertTrue(new File(this.imports, "4-Teams/NotAPack").mkdirs());

    assertTrue(PackFinder.find(this.roots, "NotAPack").isEmpty());
  }

  private File single(String name) {
    final List<File> matches = PackFinder.find(this.roots, name);

    assertEquals("expected exactly one match for '" + name + "'", 1, matches.size());

    return matches.get(0);
  }

  private static void pack(File dir) throws IOException {
    assertTrue(dir.mkdirs() || dir.isDirectory());

    Files.write(new File(dir, "arena.json").toPath(),
        "{\"format\": 1}".getBytes(StandardCharsets.UTF_8));
    Files.write(new File(dir, "world.zip").toPath(),
        "not really a zip".getBytes(StandardCharsets.UTF_8));
  }
}

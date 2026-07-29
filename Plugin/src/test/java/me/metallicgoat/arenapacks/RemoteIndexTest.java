package me.metallicgoat.arenapacks;

import java.util.Arrays;
import me.metallicgoat.arenapacks.remote.RemoteIndex;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RemoteIndexTest {

  private static RemoteIndex index(String... paths) {
    final RemoteIndex index = new RemoteIndex();

    index.packs = Arrays.asList(paths);

    return index;
  }

  @Test
  public void findsPackByBareName() {
    assertEquals("4-Teams/Aquarium",
        index("2-Teams/Picnic", "4-Teams/Aquarium").findPack("Aquarium"));
  }

  @Test
  public void findsPackByFullPath() {
    assertEquals("4-Teams/Aquarium",
        index("2-Teams/Picnic", "4-Teams/Aquarium").findPack("4-Teams/Aquarium"));
  }

  @Test
  public void matchingIsCaseInsensitive() {
    final RemoteIndex index = index("4-Teams/Aquarium");

    assertEquals("4-Teams/Aquarium", index.findPack("aquarium"));
    assertEquals("4-Teams/Aquarium", index.findPack("4-teams/AQUARIUM"));
  }

  @Test
  public void findsPackAtIndexRoot() {
    assertEquals("Picnic", index("Picnic").findPack("picnic"));
  }

  @Test
  public void returnsNullForUnknownName() {
    assertNull(index("4-Teams/Aquarium").findPack("Glacier"));
  }

  @Test
  public void ignoresEmptyEntries() {
    assertEquals("4-Teams/Aquarium", index("", "4-Teams/Aquarium").findPack("Aquarium"));
  }

  /** The same map may be published under two categories; the caller must ask. */
  @Test
  public void reportsEveryMatchForADuplicatedName() {
    final RemoteIndex index = index("2-Teams/Picnic", "4-Teams/Picnic");

    assertEquals(Arrays.asList("2-Teams/Picnic", "4-Teams/Picnic"), index.findPacks("Picnic"));
    // A full path is unambiguous
    assertEquals(Arrays.asList("4-Teams/Picnic"), index.findPacks("4-Teams/Picnic"));
  }

  @Test
  public void splitsCategoryAndDirectoryName() {
    assertEquals("4-Teams", RemoteIndex.categoryOf("4-Teams/Aquarium"));
    assertEquals("Aquarium", RemoteIndex.directoryName("4-Teams/Aquarium"));
  }

  @Test
  public void rootLevelPackHasNoCategory() {
    assertEquals("", RemoteIndex.categoryOf("Picnic"));
    assertEquals("Picnic", RemoteIndex.directoryName("Picnic"));
  }

  @Test
  public void handlesNestedCategoriesAndTrailingSlash() {
    assertEquals("4-Teams/Legacy", RemoteIndex.categoryOf("4-Teams/Legacy/Aquarium"));
    assertEquals("Aquarium", RemoteIndex.directoryName("4-Teams/Aquarium/"));
    assertEquals("4-Teams", RemoteIndex.categoryOf("4-Teams/Aquarium/"));
  }
}

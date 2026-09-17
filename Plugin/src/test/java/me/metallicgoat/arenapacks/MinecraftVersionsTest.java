package me.metallicgoat.arenapacks;

import me.metallicgoat.arenapacks.util.MinecraftVersions;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MinecraftVersionsTest {

  @Test
  public void olderAndSamePacksAreAllowed() {
    assertFalse(MinecraftVersions.isNewerThan("1.8.8", "26.3"));
    assertFalse(MinecraftVersions.isNewerThan("1.8.8", "1.8.8"));
    assertFalse(MinecraftVersions.isNewerThan("1.19", "1.19.0"));
    assertFalse(MinecraftVersions.isNewerThan("1.21.4", "1.21.10"));
  }

  @Test
  public void newerPacksAreRejected() {
    assertTrue(MinecraftVersions.isNewerThan("1.19", "1.8.8"));
    assertTrue(MinecraftVersions.isNewerThan("26.2", "1.21.4"));
    assertTrue(MinecraftVersions.isNewerThan("26.3", "26.2"));
    assertTrue(MinecraftVersions.isNewerThan("1.21.10", "1.21.4"));
    assertTrue(MinecraftVersions.isNewerThan("1.8.9", "1.8"));
  }

  @Test
  public void unknownVersionsNeverBlock() {
    assertFalse(MinecraftVersions.isNewerThan(null, "1.8.8"));
    assertFalse(MinecraftVersions.isNewerThan("", "1.8.8"));
    assertFalse(MinecraftVersions.isNewerThan("26.3-pre1", "1.8.8"));
    assertFalse(MinecraftVersions.isNewerThan("26.3", "unknown"));
  }
}

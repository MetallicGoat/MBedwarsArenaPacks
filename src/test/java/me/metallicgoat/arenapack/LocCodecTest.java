package me.metallicgoat.arenapack;

import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;
import me.metallicgoat.arenapack.util.LocCodec;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocCodecTest {

  @Test
  public void roundTripXYZ() {
    final XYZ original = new XYZ(10.5, -64.0, 1000.25);
    final XYZ parsed = LocCodec.parseXYZ(LocCodec.format(original));

    assertEquals(original.getX(), parsed.getX(), 0);
    assertEquals(original.getY(), parsed.getY(), 0);
    assertEquals(original.getZ(), parsed.getZ(), 0);
  }

  @Test
  public void roundTripXYZYP() {
    final XYZYP original = new XYZYP(1.5, 78.0, -3.5, 90.5f, -12.25f);
    final XYZYP parsed = LocCodec.parseXYZYP(LocCodec.format(original));

    assertEquals(original.getX(), parsed.getX(), 0);
    assertEquals(original.getY(), parsed.getY(), 0);
    assertEquals(original.getZ(), parsed.getZ(), 0);
    assertEquals(original.getYaw(), parsed.getYaw(), 0);
    assertEquals(original.getPitch(), parsed.getPitch(), 0);
  }

  @Test
  public void roundTripXYZD() {
    final XYZD original = new XYZD(25.0, 78.0, 100.0, XYZD.Direction.WEST);
    final XYZD parsed = LocCodec.parseXYZD(LocCodec.format(original));

    assertEquals(original.getX(), parsed.getX(), 0);
    assertEquals(original.getY(), parsed.getY(), 0);
    assertEquals(original.getZ(), parsed.getZ(), 0);
    assertEquals(XYZD.Direction.WEST, parsed.getDirection());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsWrongPartCount() {
    LocCodec.parseXYZ("1;2");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsUnknownDirection() {
    LocCodec.parseXYZD("1;2;3;UP");
  }
}

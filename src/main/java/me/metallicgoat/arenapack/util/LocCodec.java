package me.metallicgoat.arenapack.util;

import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;
import java.util.Locale;

/**
 * Encodes MBedwars location types as compact strings so packs do not rely
 * on ConfigurationSerialization alias registration on the target server.
 * <p>
 * Formats: XYZ "x;y;z", XYZYP "x;y;z;yaw;pitch", XYZD "x;y;z;DIRECTION"
 */
public class LocCodec {

  public static String format(XYZ loc) {
    return num(loc.getX()) + ";" + num(loc.getY()) + ";" + num(loc.getZ());
  }

  public static String format(XYZYP loc) {
    return format((XYZ) loc) + ";" + num(loc.getYaw()) + ";" + num(loc.getPitch());
  }

  public static String format(XYZD loc) {
    return format((XYZ) loc) + ";" + loc.getDirection().name();
  }

  public static XYZ parseXYZ(String value) {
    final String[] parts = split(value, 3);

    return new XYZ(parseDouble(parts[0]), parseDouble(parts[1]), parseDouble(parts[2]));
  }

  public static XYZYP parseXYZYP(String value) {
    final String[] parts = split(value, 5);

    return new XYZYP(
        parseDouble(parts[0]), parseDouble(parts[1]), parseDouble(parts[2]),
        (float) parseDouble(parts[3]), (float) parseDouble(parts[4]));
  }

  public static XYZD parseXYZD(String value) {
    final String[] parts = split(value, 4);
    final XYZD.Direction direction = XYZD.Direction.fromName(parts[3]);

    if (direction == null)
      throw new IllegalArgumentException("Unknown direction: " + parts[3]);

    return new XYZD(parseDouble(parts[0]), parseDouble(parts[1]), parseDouble(parts[2]), direction);
  }

  private static String[] split(String value, int expectedParts) {
    final String[] parts = value.trim().split(";");

    if (parts.length != expectedParts)
      throw new IllegalArgumentException("Expected " + expectedParts + " parts in location: " + value);

    return parts;
  }

  private static double parseDouble(String value) {
    return Double.parseDouble(value.trim());
  }

  private static String num(double value) {
    return String.format(Locale.ROOT, "%s", value);
  }
}

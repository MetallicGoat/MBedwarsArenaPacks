package me.metallicgoat.arenapacks.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;

/**
 * Encodes MBedwars location types as plain JSON objects, so packs do not rely
 * on ConfigurationSerialization alias registration on the target server and
 * every coordinate stays readable (and editable) in the pack's arena.json.
 * <p>
 * Shapes: XYZ {x,y,z}, XYZYP {x,y,z,yaw,pitch}, XYZD {x,y,z,direction}
 */
public class LocJson {

  public static JsonObject write(XYZ loc) {
    final JsonObject json = new JsonObject();

    json.addProperty("x", loc.getX());
    json.addProperty("y", loc.getY());
    json.addProperty("z", loc.getZ());

    return json;
  }

  public static JsonObject write(XYZYP loc) {
    final JsonObject json = write((XYZ) loc);

    json.addProperty("yaw", loc.getYaw());
    json.addProperty("pitch", loc.getPitch());

    return json;
  }

  public static JsonObject write(XYZD loc) {
    final JsonObject json = write((XYZ) loc);

    json.addProperty("direction", loc.getDirection().name());

    return json;
  }

  public static XYZ readXYZ(JsonObject json) {
    return new XYZ(number(json, "x"), number(json, "y"), number(json, "z"));
  }

  public static XYZYP readXYZYP(JsonObject json) {
    return new XYZYP(
        number(json, "x"), number(json, "y"), number(json, "z"),
        (float) number(json, "yaw"), (float) number(json, "pitch"));
  }

  public static XYZD readXYZD(JsonObject json) {
    final String name = string(json, "direction");
    final XYZD.Direction direction = XYZD.Direction.fromName(name);

    if (direction == null)
      throw new IllegalArgumentException("Unknown direction: " + name);

    return new XYZD(number(json, "x"), number(json, "y"), number(json, "z"), direction);
  }

  private static double number(JsonObject json, String key) {
    final JsonElement element = json.get(key);

    if (element == null || element.isJsonNull())
      throw new IllegalArgumentException("Location is missing '" + key + "': " + json);

    try {
      return element.getAsDouble();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Location field '" + key + "' is not a number: " + element);
    }
  }

  private static String string(JsonObject json, String key) {
    final JsonElement element = json.get(key);

    if (element == null || element.isJsonNull())
      throw new IllegalArgumentException("Location is missing '" + key + "': " + json);

    return element.getAsString();
  }
}

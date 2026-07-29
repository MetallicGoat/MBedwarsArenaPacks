package me.metallicgoat.arenapacks;

import com.google.gson.JsonObject;
import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;
import me.metallicgoat.arenapacks.util.LocJson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocJsonTest {

  @Test
  public void xyzRoundTrip() {
    final XYZ read = LocJson.readXYZ(LocJson.write(new XYZ(10.5, -64, 210)));

    assertEquals(10.5, read.getX(), 0);
    assertEquals(-64, read.getY(), 0);
    assertEquals(210, read.getZ(), 0);
  }

  @Test
  public void xyzypRoundTrip() {
    final XYZYP read = LocJson.readXYZYP(LocJson.write(new XYZYP(20.5, 78, 100.5, 90.5f, -12.25f)));

    assertEquals(20.5, read.getX(), 0);
    assertEquals(78, read.getY(), 0);
    assertEquals(100.5, read.getZ(), 0);
    assertEquals(90.5f, read.getYaw(), 0);
    assertEquals(-12.25f, read.getPitch(), 0);
  }

  @Test
  public void xyzdRoundTrip() {
    final XYZD read = LocJson.readXYZD(LocJson.write(new XYZD(25, 78, 100, XYZD.Direction.WEST)));

    assertEquals(25, read.getX(), 0);
    assertEquals(XYZD.Direction.WEST, read.getDirection());
  }

  @Test
  public void writesNamedFields() {
    final JsonObject json = LocJson.write(new XYZYP(1, 2, 3, 4f, 5f));

    assertEquals(1.0, json.get("x").getAsDouble(), 0);
    assertEquals(2.0, json.get("y").getAsDouble(), 0);
    assertEquals(3.0, json.get("z").getAsDouble(), 0);
    assertEquals(4.0, json.get("yaw").getAsDouble(), 0);
    assertEquals(5.0, json.get("pitch").getAsDouble(), 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsUnknownDirection() {
    final JsonObject json = LocJson.write(new XYZ(1, 2, 3));

    json.addProperty("direction", "SIDEWAYS");

    LocJson.readXYZD(json);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMissingCoordinate() {
    final JsonObject json = LocJson.write(new XYZ(1, 2, 3));

    json.remove("y");

    LocJson.readXYZ(json);
  }
}

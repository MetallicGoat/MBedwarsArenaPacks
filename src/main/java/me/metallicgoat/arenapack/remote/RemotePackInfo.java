package me.metallicgoat.arenapack.remote;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** One entry of the repo's index.json. Deserialized by Gson. */
public class RemotePackInfo {

  public String name;
  public int version = 1;
  public String file;

  public @Nullable String description;
  public @Nullable List<String> authors;

  @SerializedName("minecraft-version")
  public @Nullable String minecraftVersion;

  @SerializedName("min-mbedwars-api")
  public int minMBedwarsApi = 0;

  public boolean isValid() {
    return name != null && !name.isEmpty() && file != null && !file.isEmpty();
  }
}

package me.metallicgoat.arenapack.remote;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** The repo's index.json. Deserialized by Gson. */
public class RemoteIndex {

  public int format = 1;
  public List<RemotePackInfo> packs = new ArrayList<>();

  public @Nullable RemotePackInfo findPack(String name) {
    for (RemotePackInfo pack : this.packs) {
      if (pack.isValid() && pack.name.equalsIgnoreCase(name))
        return pack;
    }

    return null;
  }
}

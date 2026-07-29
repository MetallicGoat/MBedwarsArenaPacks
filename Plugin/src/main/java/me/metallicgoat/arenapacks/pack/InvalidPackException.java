package me.metallicgoat.arenapacks.pack;

import java.io.IOException;

/** Thrown when a pack's arena.json is missing, malformed or of a foreign format. */
public class InvalidPackException extends IOException {

  public InvalidPackException(String message) {
    super(message);
  }
}

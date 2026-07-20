package me.metallicgoat.arenapack.pack;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pack operations touch global state (world container, world autosave), so
 * only one export/import/install may run at a time server-wide.
 */
public class OperationLock {

  private static final AtomicBoolean BUSY = new AtomicBoolean(false);

  public static boolean tryAcquire() {
    return BUSY.compareAndSet(false, true);
  }

  public static void release() {
    BUSY.set(false);
  }
}

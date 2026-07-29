package me.metallicgoat.arenapacks.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.Nullable;

public class ZipUtil {

  /**
   * Fixed timestamp written on every entry. Zipping an unchanged folder twice
   * must produce byte-identical output, otherwise every export would show up as
   * a change in git even when the map never moved.
   */
  private static final long FIXED_ENTRY_TIME = 0L;

  private static final Comparator<File> BY_NAME = new Comparator<File>() {
    @Override
    public int compare(File a, File b) {
      return a.getName().compareTo(b.getName());
    }
  };

  /**
   * Zips the contents of {@code sourceDir} (not the folder itself) into
   * {@code zipFile}. Entries accepted by {@code excludedRootEntries} are
   * skipped at the top level only.
   */
  public static void zip(File sourceDir, File zipFile, @Nullable Predicate<String> excludedRootEntries) throws IOException {
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
      zipEntry(sourceDir, "", out, excludedRootEntries);
    }
  }

  public static void zip(File sourceDir, File zipFile) throws IOException {
    zip(sourceDir, zipFile, null);
  }

  private static void zipEntry(File file, String path, ZipOutputStream out,
                               @Nullable Predicate<String> excludedRootEntries) throws IOException {
    if (file.isDirectory()) {
      final File[] entries = file.listFiles();

      if (entries == null)
        throw new IOException("Failed to list directory: " + file);

      // Filesystem order is not guaranteed; sort so the output stays stable
      Arrays.sort(entries, BY_NAME);

      for (File entry : entries) {
        if (excludedRootEntries != null && excludedRootEntries.test(entry.getName()))
          continue;

        zipEntry(entry, path.isEmpty() ? entry.getName() : path + "/" + entry.getName(), out, null);
      }

      return;
    }

    final ZipEntry entry = new ZipEntry(path);

    entry.setTime(FIXED_ENTRY_TIME);
    out.putNextEntry(entry);

    try (InputStream in = new FileInputStream(file)) {
      copy(in, out);
    }

    out.closeEntry();
  }

  public static void unzip(File zipFile, File targetDir) throws IOException {
    final String targetPrefix = targetDir.getCanonicalPath() + File.separator;

    try (ZipInputStream in = new ZipInputStream(new FileInputStream(zipFile))) {
      ZipEntry entry;

      while ((entry = in.getNextEntry()) != null) {
        final File target = new File(targetDir, entry.getName());

        // Zip-slip guard: refuse entries that escape the target directory
        if (!target.getCanonicalPath().startsWith(targetPrefix))
          throw new IOException("Illegal zip entry path: " + entry.getName());

        if (entry.isDirectory()) {
          if (!target.isDirectory() && !target.mkdirs())
            throw new IOException("Failed to create directory: " + target);

          continue;
        }

        final File parent = target.getParentFile();

        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
          throw new IOException("Failed to create directory: " + parent);

        try (OutputStream out = new FileOutputStream(target)) {
          copy(in, out);
        }
      }
    }
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    final byte[] buffer = new byte[8192];
    int read;

    while ((read = in.read(buffer)) != -1)
      out.write(buffer, 0, read);
  }
}

package me.metallicgoat.arenapack.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

  /**
   * Zips the contents of {@code sourceDir} (not the folder itself) into
   * {@code zipFile}.
   */
  public static void zip(File sourceDir, File zipFile) throws IOException {
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
      zipEntry(sourceDir, "", out);
    }
  }

  private static void zipEntry(File file, String path, ZipOutputStream out) throws IOException {
    if (file.isDirectory()) {
      final File[] entries = file.listFiles();

      if (entries == null)
        throw new IOException("Failed to list directory: " + file);

      for (File entry : entries)
        zipEntry(entry, path.isEmpty() ? entry.getName() : path + "/" + entry.getName(), out);

      return;
    }

    out.putNextEntry(new ZipEntry(path));

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

package dev.snowdrop.buildpack.docker;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Abstraction representing an entry in a container. Allows for entries to be
 * added from sources other than File/Directory (eg, String as a test content,
 * or programmatic supply via future i/f)
 */
public interface ContainerEntry {
  public String getPath();

  public long getSize() throws IOException;

  public ContentSupplier getContentSupplier() throws IOException;

  @FunctionalInterface
  public interface ContentSupplier {
    InputStream getData() throws IOException;
  }

  /**
   * build a container entry from a string, to be present in the container at
   * /path
   */
  static ContainerEntry fromString(String name, String content) throws IOException {
    return new ContainerEntry() {
      @Override
      public long getSize() throws IOException {
        return content.getBytes().length;
      }

      @Override
      public String getPath() {
        return name;
      }

      @Override
      public ContentSupplier getContentSupplier() throws IOException {
        return () -> new ByteArrayInputStream(content.getBytes());
      }
    };
  }

  static ContainerEntry fromStream(String name, long length, ContentSupplier data) {
    return new ContainerEntry() {
      @Override
      public long getSize() throws IOException {
        return length;
      }

      @Override
      public String getPath() {
        return name;
      }

      @Override
      public ContentSupplier getContentSupplier() throws IOException {
        return data;
      }
    };
  }

  /**
   * build a container entry from a File, (can also be directory) to be present in
   * the container at prefix/name for a File, or prefix/relativePath for fies in a
   * dir. Eg, given /a/one/a /a/two/a, passing prefix of /stiletto with /a results
   * in /stiletto/one/a and /stiletto/two/a being created.
   */
  static ContainerEntry[] fromFile(String prefix, File f) throws IOException {
    if (!f.exists()) {
      return null;
    }

    if (f.isFile() && !f.isDirectory()) {
      return new ContainerEntry[] { new ContainerEntry() {
        @Override
        public long getSize() throws IOException {
          return Files.size(f.toPath());
        }

        @Override
        public String getPath() {
          // format MUST be unix, as we're putting them in a unix container
          return prefix + "/" + f.getName();
        }

        @Override
        public ContentSupplier getContentSupplier() {
          Path p = f.toPath();
          return () -> new BufferedInputStream(Files.newInputStream(p));
        }
      } };
    } else if (f.isDirectory()) {
      List<ContainerEntry> entries = new ArrayList<>();
      try (Stream<Path> paths = Files.walk(f.toPath())) {
        paths.filter(p -> !p.toFile().isDirectory()).forEach(p -> {
          entries.add(new ContainerEntry() {
            @Override
            public long getSize() throws IOException {
              return Files.size(p);
            }
            @Override
            public String getPath() {
              // format MUST be unix, as we're putting them in a unix container
              // This is slightly daft (have to handle when running on windows, where
              // Paths.get returns windows paths)
              String path = f.toPath().relativize(p).toString().replace("\\", "/");
              return prefix + "/" + path;
            }
            @Override
            public ContentSupplier getContentSupplier() throws IOException {
              return () -> new BufferedInputStream(Files.newInputStream(p));
            }
          });
        });
      }
      return entries.toArray(new ContainerEntry[] {});
    }
    return null;
  }
}

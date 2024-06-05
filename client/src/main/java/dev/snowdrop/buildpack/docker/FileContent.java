package dev.snowdrop.buildpack.docker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import dev.snowdrop.buildpack.BuildpackException;
import dev.snowdrop.buildpack.utils.FilePermissions;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage = true, builderPackage = "dev.snowdrop.buildpack.builder")
public class FileContent implements Content {

  private static final String DEFAULT_PREFIX = "";
  private static final String UNIX_FILE_SEPARATOR = "/";
  private static final String NON_UNIX_FILE_SEPARATOR = "\\";

  private final String prefix;
  private final File file;
  private final File root;
  //delegate calls for permissions thru static, to enable testing.
  private static FilePermissions filePermissions = new FilePermissions();

  public FileContent(File file) {
    this(DEFAULT_PREFIX, file);
  }

  public FileContent(String prefix, File file) {
    this(prefix, file, null);
    if(!file.exists()){
      throw new RuntimeException(new FileNotFoundException(file.getAbsolutePath()));
    }
  }

  private FileContent(String prefix, File file, File root) {
    this.prefix = prefix;
    this.file = file;
    this.root = root;
  }

  public String getPrefix() {
    return prefix;
  }

  public File getFile() {
    return file;
  }

  /**
   * Build a container entry from a File, (can also be directory) to be present in
   * the container at prefix/name for a File, or prefix/relativePath for fies in a
   * dir. Eg, given /a/one/a /a/two/a, passing prefix of /stiletto with /a results
   * in /stiletto/one/a and /stiletto/two/a being created.
   */
  public List<ContainerEntry> getContainerEntries() {
    if (!file.exists()) {
      return Collections.emptyList();
    }

    if (file.isFile() && !file.isDirectory()) {
      return Arrays.asList(new ContainerEntry() {
        @Override
        public long getSize() {
          try {
            return Files.size(file.toPath());
          } catch (IOException e) {
            throw BuildpackException.launderThrowable(e);
          }
        }

        @Override
        public String getPath() {
          if (root == null) {
            return prefix + "/" + file.getName();
          }
          // format MUST be unix, as we're putting them in a unix container
          return prefix + root.toPath().relativize(file.toPath()).toString().replace(NON_UNIX_FILE_SEPARATOR,
              UNIX_FILE_SEPARATOR);
        }

        @Override
        public Integer getMode() {
          return filePermissions.getPermissions(file);
        }

        @Override
        public DataSupplier getDataSupplier() {
          Path p = file.toPath();
          return new DataSupplier() {
            public InputStream getData() {
              try {
                return new BufferedInputStream(Files.newInputStream(p));
              } catch (IOException e) {
                throw BuildpackException.launderThrowable(e);
              }
            }
          };
        }

      });
    } else if (file.isDirectory()) {
      try {
        return Files.walk(file.toPath()).filter(p -> !p.toFile().isDirectory())
            .flatMap(p -> new FileContent(prefix, p.toFile(), file).getContainerEntries().stream())
            .collect(Collectors.toList());
      } catch (IOException e) {
        throw BuildpackException.launderThrowable(e);
      }
    }
    return Collections.emptyList();
  }
}

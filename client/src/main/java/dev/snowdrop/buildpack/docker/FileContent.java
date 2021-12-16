package dev.snowdrop.buildpack.docker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import dev.snowdrop.buildpack.BuildpackException;
import io.sundr.builder.annotations.Buildable;

@Buildable(generateBuilderPackage=true, builderPackage="dev.snowdrop.buildpack.builder")
public class FileContent implements ContainerEntry {

  private static final String DEFAULT_PREFIX="";
  private static final String UNIX_FILE_SEPARATOR="/";
  private static final String NON_UNIX_FILE_SEPARATOR="\\";

  private final String prefix;
  private final File file;
  private final File root;

  public FileContent(File file) {
    this(DEFAULT_PREFIX, file);
  }

  public FileContent(String prefix, File file) {
    this(prefix, file, null);
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

  @Override
  public long getSize() {
    try { 
      return Files.size(file.toPath());
    } catch (IOException e)  {
      throw BuildpackException.launderThrowable(e);
    }
  }

  @Override
  public String getPath() {
    if (root == null) {
      return prefix + "/" + file.getName();
    }
    // format MUST be unix, as we're putting them in a unix container
    return prefix + root.toPath().relativize(file.toPath()).toString().replace(NON_UNIX_FILE_SEPARATOR, UNIX_FILE_SEPARATOR);
  }

  @Override
  public ContentSupplier getContentSupplier() {
    Path p = file.toPath();
    return new ContentSupplier() {
      public InputStream getData() {
        try {
          return new BufferedInputStream(Files.newInputStream(p));
        } catch (IOException e) {
          throw BuildpackException.launderThrowable(e);
        }
      }
    };
  }

  public List<ContainerEntry> getEntries() {
    if (!file.isDirectory()) {
      return Arrays.asList(this);
    }

    try {
    return Files.walk(file.toPath())
      .filter(p -> !p.toFile().isDirectory())
      .flatMap(f -> new FileContent(prefix, f.toFile(), file).getEntries().stream())
      .collect(Collectors.toList());
    } catch (IOException e) {
      throw BuildpackException.launderThrowable(e);
    }
  }
}

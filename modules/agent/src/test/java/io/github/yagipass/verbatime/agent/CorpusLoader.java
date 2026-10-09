package io.github.yagipass.verbatime.agent;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

final class CorpusLoader extends ClassLoader {

  interface Instrument {
    byte[] apply(ClassLoader loader, String internalName, byte[] original);
  }

  private static final String AGENT_PREFIX = "io.github.yagipass.verbatime.";

  private final CorpusJars corpus;

  private final CorpusJars.Jar preferred;

  private final List<Path> classDirs;

  private final Instrument instrument;

  CorpusLoader(
      String name,
      CorpusJars corpus,
      CorpusJars.Jar preferred,
      List<Path> classDirs,
      Instrument instrument) {
    super(name, ClassLoader.getPlatformClassLoader());
    this.corpus = corpus;
    this.preferred = preferred;
    this.classDirs = List.copyOf(classDirs);
    this.instrument = instrument;
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      @Var Class<?> c = findLoadedClass(name);
      if (c == null && !name.startsWith("java.")) {
        byte[] bytes = bytesOf(name.replace('.', '/') + ".class");
        if (bytes != null) {
          byte[] out =
              instrument == null ? null : instrument.apply(this, name.replace('.', '/'), bytes);
          byte[] def = out != null ? out : bytes;
          c = defineClass(name, def, 0, def.length);
        } else if (name.startsWith(AGENT_PREFIX)) {
          c = CorpusLoader.class.getClassLoader().loadClass(name);
        }
      }
      if (c == null) {
        c = getParent().loadClass(name);
      }
      return c;
    }
  }

  @Override
  protected URL findResource(String name) {
    List<URL> all = resources(name, true);
    return all.isEmpty() ? null : all.get(0);
  }

  @Override
  protected Enumeration<URL> findResources(String name) {
    return Collections.enumeration(resources(name, false));
  }

  byte[] bytesOf(String entryName) {
    for (Path dir : classDirs) {
      Path p = dir.resolve(entryName);
      if (Files.isRegularFile(p)) {
        try {
          return Files.readAllBytes(p);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
    if (preferred != null) {
      byte[] b = preferred.read(entryName);
      if (b != null) {
        return b;
      }
    }
    CorpusJars.Jar j = corpus.find(entryName);
    return j == null ? null : j.read(entryName);
  }

  private List<URL> resources(String name, boolean firstOnly) {
    List<URL> out = new ArrayList<>();
    for (Path dir : classDirs) {
      Path p = dir.resolve(name);
      if (Files.exists(p)) {
        try {
          out.add(p.toUri().toURL());
        } catch (MalformedURLException e) {
          throw new IllegalStateException(e);
        }
        if (firstOnly) {
          return out;
        }
      }
    }
    List<CorpusJars.Jar> order = new ArrayList<>();
    if (preferred != null) {
      order.add(preferred);
    }
    for (CorpusJars.Jar j : corpus.jars()) {
      if (!j.equals(preferred)) {
        order.add(j);
      }
    }
    for (CorpusJars.Jar j : order) {
      URL u = j.url(name);
      if (u != null) {
        out.add(u);
        if (firstOnly) {
          return out;
        }
      }
    }
    return out;
  }
}

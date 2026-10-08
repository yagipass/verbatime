package io.github.yagipass.verbatime.agent;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

final class BootstrapInstaller {

  private static final String PROBE_PREFIX = "io/github/yagipass/verbatime/agent/probe/";

  private static final String FORMAT_PREFIX = "io/github/yagipass/verbatime/format/";

  private BootstrapInstaller() {}

  static void install(Instrumentation inst) {
    Path self = selfLocation();
    if (Files.isDirectory(self)) {
      System.err.println(
          "[verbatime] running from " + self + ", so the probe classes stay on the class path");
      return;
    }
    try {
      Path probeJar = extractBootstrapJar(self);
      inst.appendToBootstrapClassLoaderSearch(new JarFile(probeJar.toFile()));
      probeJar.toFile().deleteOnExit();
    } catch (IOException e) {
      System.err.println(
          "[verbatime] cannot install the probe classes on the bootstrap class path: " + e);
      throw new UncheckedIOException(e);
    }
  }

  private static Path selfLocation() {
    CodeSource cs = BootstrapInstaller.class.getProtectionDomain().getCodeSource();
    if (cs == null || cs.getLocation() == null) {
      throw new IllegalStateException("cannot locate the agent jar");
    }
    try {
      return Path.of(cs.getLocation().toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("cannot locate the agent jar: " + cs.getLocation(), e);
    }
  }

  static Path extractBootstrapJar(Path agentJar) throws IOException {
    Path tmp = Files.createTempFile("verbatime-probe-", ".jar");
    @Var int copied = 0;
    try (JarFile in = new JarFile(agentJar.toFile());
        JarOutputStream out = new JarOutputStream(Files.newOutputStream(tmp))) {
      Enumeration<JarEntry> entries = in.entries();
      while (entries.hasMoreElements()) {
        JarEntry e = entries.nextElement();
        if (e.isDirectory() || !isBootstrapEntry(e.getName())) {
          continue;
        }
        out.putNextEntry(new JarEntry(e.getName()));
        try (InputStream is = in.getInputStream(e)) {
          is.transferTo(out);
        }
        out.closeEntry();
        copied++;
      }
    }
    if (copied == 0) {
      throw new IOException(
          "no " + PROBE_PREFIX + " or " + FORMAT_PREFIX + " entries in " + agentJar);
    }
    return tmp;
  }

  private static boolean isBootstrapEntry(String name) {
    return name.startsWith(PROBE_PREFIX) || name.startsWith(FORMAT_PREFIX);
  }
}

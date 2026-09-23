package io.github.yagipass.verbatime.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import io.github.yagipass.verbatime.agent.test.Check;

public final class BootstrapInstallerTest {

    private BootstrapInstallerTest() {
    }

    public static void run() throws Exception {
        final Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp")).resolve("boot");
        Files.createDirectories(tmp);

        final Path fake = tmp.resolve("fake-agent.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(fake))) {
            put(out, "META-INF/MANIFEST.MF");
            put(out, "io/github/yagipass/verbatime/agent/probe/");
            put(out, "io/github/yagipass/verbatime/agent/probe/Probe.class");
            put(out, "io/github/yagipass/verbatime/agent/probe/MethodRegistry.class");
            put(out, "io/github/yagipass/verbatime/format/Vbtm.class");
            put(out, "io/github/yagipass/verbatime/agent/VerbatimeAgent.class");
            put(out, "io/github/yagipass/verbatime/agent/jmx/VerbatimeControl.class");
        }
        final Path probe = BootstrapInstaller.extractBootstrapJar(fake);
        final List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(probe.toFile())) {
            final Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                names.add(e.nextElement().getName());
            }
        }
        Check.eq(List.of("io/github/yagipass/verbatime/agent/probe/Probe.class", "io/github/yagipass/verbatime/agent/probe/MethodRegistry.class", "io/github/yagipass/verbatime/format/Vbtm.class"), names, "probe jar carries exactly the probe and format class entries, which the bootstrap loader must see together");

        final Path noProbe = tmp.resolve("no-probe.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(noProbe))) {
            put(out, "io/github/yagipass/verbatime/agent/VerbatimeAgent.class");
        }
        Check.thrown(IOException.class, () -> BootstrapInstaller.extractBootstrapJar(noProbe), "a jar without probe entries is rejected");
    }

    private static void put(final JarOutputStream out, final String name) throws IOException {
        out.putNextEntry(new JarEntry(name));
        if (!name.endsWith("/")) {
            out.write(new byte[] { 1, 2, 3 });
        }
        out.closeEntry();
    }
}

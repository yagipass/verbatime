package io.github.yagipass.verbatime.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

final class CorpusJars implements AutoCloseable {

    static final String DIRS_PROPERTY = "io.github.yagipass.verbatime.agent.test.corpus";

    record Jar(String name, Path path, JarFile file, List<String> classEntries) {

        byte[] read(String entryName) {
            JarEntry e = file.getJarEntry(entryName);
            if (e == null) {
                return null;
            }
            try (InputStream in = file.getInputStream(e)) {
                return in.readAllBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        URL url(String entryName) {
            JarEntry e = file.getJarEntry(entryName);
            if (e == null) {
                return null;
            }
            try {
                return URI.create("jar:" + path.toUri() + "!/" + e.getRealName()).toURL();
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        long bytes() {
            try {
                return Files.size(path);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    private final List<Jar> jars;

    private final Map<String, Jar> firstByEntry = new HashMap<>();

    private CorpusJars(List<Jar> jars) {
        this.jars = List.copyOf(jars);
        for (Jar j : jars) {
            try (Stream<JarEntry> s = j.file().versionedStream()) {
                s.forEach(e -> firstByEntry.putIfAbsent(e.getName(), j));
            }
        }
    }

    static CorpusJars fromProperty() throws IOException {
        String dirs = System.getProperty(DIRS_PROPERTY);
        if (dirs == null || dirs.isBlank()) {
            throw new IllegalStateException("-D" + DIRS_PROPERTY + " must name the directories holding the corpus jars");
        }
        List<Path> paths = new ArrayList<>();
        for (String d : dirs.split(File.pathSeparator, -1)) {
            if (d.isBlank()) {
                continue;
            }
            Path dir = Path.of(d);
            if (!Files.isDirectory(dir)) {
                throw new IllegalStateException("corpus directory " + dir + " does not exist: run the build through the pre-integration-test phase so maven-dependency-plugin copies the jars");
            }
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().forEach(paths::add);
            }
        }
        if (paths.isEmpty()) {
            throw new IllegalStateException("no corpus jars in " + dirs);
        }
        return open(paths);
    }

    static CorpusJars open(List<Path> paths) throws IOException {
        List<Jar> jars = new ArrayList<>();
        for (Path p : paths) {
            JarFile jf = new JarFile(p.toFile(), false, ZipFile.OPEN_READ, Runtime.version());
            List<String> classes = new ArrayList<>();
            try (Stream<JarEntry> s = jf.versionedStream()) {
                s.map(JarEntry::getName).filter(CorpusJars::isClassEntry).sorted().forEach(classes::add);
            }
            String file = p.getFileName().toString();
            jars.add(new Jar(file.substring(0, file.length() - ".jar".length()), p, jf, List.copyOf(classes)));
        }
        return new CorpusJars(jars);
    }

    private static boolean isClassEntry(String name) {
        return name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class") && !name.startsWith("java/");
    }

    List<Jar> jars() {
        return jars;
    }

    List<Jar> named(List<String> prefixes) {
        List<Jar> out = new ArrayList<>();
        for (String prefix : prefixes) {
            List<Jar> hits = jars.stream().filter(j -> j.name().startsWith(prefix)).toList();
            if (hits.isEmpty()) {
                throw new IllegalStateException("no corpus jar starts with '" + prefix + "'; the corpus holds " + jars.stream().map(Jar::name).toList());
            }
            out.addAll(hits);
        }
        return out;
    }

    Jar find(String entryName) {
        return firstByEntry.get(entryName);
    }

    @Override
    public void close() throws IOException {
        for (Jar j : jars) {
            j.file().close();
        }
    }
}

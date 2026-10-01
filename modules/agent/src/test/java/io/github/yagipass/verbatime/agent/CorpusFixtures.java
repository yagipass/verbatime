package io.github.yagipass.verbatime.agent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class CorpusFixtures implements AutoCloseable {

    static final String FIXTURES_PROPERTY = "io.github.yagipass.verbatime.agent.test.corpus.fixtures";

    static final String PACKAGE = "io.github.yagipass.verbatime.corpusfixtures.";

    private final CorpusJars corpus;

    final Transformer transformer = new Transformer(Config.parse(""), new Roots(), null);

    final Set<String> instrumented = ConcurrentHashMap.newKeySet();

    final CorpusLoader original;

    final CorpusLoader transformed;

    private CorpusFixtures(CorpusJars corpus, Path fixtures) {
        this.corpus = corpus;
        original = new CorpusLoader("corpus-original", corpus, null, List.of(fixtures), null);
        transformed = new CorpusLoader("corpus-transformed", corpus, null, List.of(fixtures), (loader, internalName, bytes) -> {
            byte[] out = transformer.transform(null, loader, internalName, null, null, bytes);
            if (out != null) {
                instrumented.add(internalName);
            }
            return out;
        });
    }

    static CorpusFixtures open() throws IOException {
        String dir = System.getProperty(FIXTURES_PROPERTY);
        if (dir == null || !Files.isDirectory(Path.of(dir, PACKAGE.replace('.', '/')))) {
            throw new IllegalStateException("-D" + FIXTURES_PROPERTY + " must name the directory the compile-corpus-fixtures execution compiles into, got " + dir);
        }
        return new CorpusFixtures(CorpusJars.fromProperty(), Path.of(dir));
    }

    static String call(ClassLoader loader, String fixture, String method) {
        Thread thread = Thread.currentThread();
        ClassLoader saved = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return String.valueOf(loader.loadClass(PACKAGE + fixture).getMethod(method).invoke(null));
        } catch (InvocationTargetException e) {
            System.err.println("[corpus] " + fixture + "." + method + " threw in " + loader.getName() + ":");
            e.getCause().printStackTrace(System.err);
            return "threw " + e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        } finally {
            thread.setContextClassLoader(saved);
        }
    }

    @Override
    public void close() throws IOException {
        corpus.close();
    }
}

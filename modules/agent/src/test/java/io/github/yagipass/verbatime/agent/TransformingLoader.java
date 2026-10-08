package io.github.yagipass.verbatime.agent;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;

final class TransformingLoader extends ClassLoader {

  private final Transformer transformer;

  private final String prefix;

  private final Function<String, byte[]> source;

  TransformingLoader(Transformer transformer, String prefix, Function<String, byte[]> source) {
    super(TransformingLoader.class.getClassLoader());
    this.transformer = transformer;
    this.prefix = prefix;
    this.source = source;
  }

  static byte[] classpathBytes(String binaryName) {
    try (InputStream in =
        TransformingLoader.class
            .getClassLoader()
            .getResourceAsStream(binaryName.replace('.', '/') + ".class")) {
      return in == null ? null : in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      @Var Class<?> c = findLoadedClass(name);
      if (c == null && name.startsWith(prefix)) {
        byte[] bytes = source.apply(name);
        if (bytes == null) {
          throw new ClassNotFoundException(name);
        }
        byte[] transformed =
            transformer.transform(null, this, name.replace('.', '/'), null, null, bytes);
        byte[] def = transformed != null ? transformed : bytes;
        c = defineClass(name, def, 0, def.length);
      }
      if (c == null) {
        c = super.loadClass(name, false);
      }
      if (resolve) {
        resolveClass(c);
      }
      return c;
    }
  }
}

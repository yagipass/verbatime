package io.github.yagipass.verbatime.format;

public final class ReadLimitException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  ReadLimitException(String resource, long limitBytes) {
    super("recording exceeds the reader memory budget (" + limitBytes + " bytes) for " + resource);
  }
}

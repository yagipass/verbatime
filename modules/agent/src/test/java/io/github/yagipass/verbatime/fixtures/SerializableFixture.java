package io.github.yagipass.verbatime.fixtures;

import java.io.Serializable;

@SuppressWarnings("serial")
public final class SerializableFixture implements Serializable {

  private int n;

  public static synchronized boolean holdsClassMonitor() {
    return Thread.holdsLock(SerializableFixture.class);
  }

  public synchronized int increment() {
    return ++n;
  }

  public synchronized boolean holdsOwnMonitor() {
    return Thread.holdsLock(this);
  }
}

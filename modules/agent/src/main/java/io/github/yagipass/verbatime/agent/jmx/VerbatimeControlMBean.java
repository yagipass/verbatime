package io.github.yagipass.verbatime.agent.jmx;

import org.jspecify.annotations.Nullable;

public interface VerbatimeControlMBean {

  String[] status();

  String[] searchMethods(@Nullable String query, int max);

  void replaceRoots(@Nullable String @Nullable [] specs);

  long startRecording(@Nullable String name);

  void stopRecording();

  long openStream(long recordingId, long fromOffset);

  byte @Nullable [] readStream(long streamId);

  void closeStream(long streamId);
}

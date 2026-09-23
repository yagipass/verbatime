package io.github.yagipass.verbatime.agent.jmx;

public interface VerbatimeControlMBean {

    String[] status();

    String[] searchMethods(String query, int max);

    void replaceRoots(String[] specs);

    long startRecording(String name);

    void stopRecording();

    long openStream(long recordingId, long fromOffset);

    byte[] readStream(long streamId);

    void closeStream(long streamId);
}

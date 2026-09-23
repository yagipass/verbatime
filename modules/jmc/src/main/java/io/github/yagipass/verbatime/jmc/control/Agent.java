package io.github.yagipass.verbatime.jmc.control;

import java.io.IOException;
import java.util.Map;

interface Agent extends AutoCloseable {

    interface Dialer {
        Agent dial(String target) throws IOException;
    }

    Map<String, String> status() throws IOException;

    String[] searchMethods(String query, int max) throws IOException;

    void replaceRoots(String[] specs) throws IOException;

    long startRecording() throws IOException;

    void stopRecording() throws IOException;

    long openStream(long recordingId, long fromOffset) throws IOException;

    byte[] readStream(long streamId) throws IOException;

    void closeStream(long streamId) throws IOException;

    @Override
    void close() throws IOException;
}

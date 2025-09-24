package com.datadobi.s3test.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class DummyInputStream extends InputStream {
    private final long size;
    private long position;

    public DummyInputStream(long size) {
        this.size = size;
        position = 0;
    }

    @Override
    public void close() {
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (position >= size) {
            return -1;
        }

        int remaining = (int) Math.min(len, size - position);
        Arrays.fill(b, off, off + remaining, (byte) 0);
        position += remaining;
        return remaining;
    }

    @Override
    public int read() throws IOException {
        if (position >= size) {
            return -1;
        }
        position++;
        return 0;
    }
}
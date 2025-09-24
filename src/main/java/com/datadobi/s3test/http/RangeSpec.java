package com.datadobi.s3test.http;

import javax.annotation.Nullable;

public record RangeSpec(@Nullable Long start, @Nullable Long end) {
    public RangeSpec {
        if (start == null && end == null) {
            throw new IllegalArgumentException("Both start and end cannot be null");
        }
    }

    @Override
    public String toString() {
        StringBuilder header = new StringBuilder();
        if (start != null) {
            header.append(start);
        }
        header.append('-');
        if (end != null) {
            header.append(end);
        }
        return header.toString();
    }
}

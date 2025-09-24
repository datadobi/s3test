package com.datadobi.s3test.util;

import java.util.Objects;

public record Pair<A, B>(A first, B second) {
    public Pair(A first, B second) {
        this.first = Objects.requireNonNull(first);
        this.second = Objects.requireNonNull(second);
    }

    public static <A, B> Pair<A, B> create(A first, B second) {
        return new Pair<>(first, second);
    }

    @Override
    public String toString() {
        return "(" + first() + ", " + second() + ")";
    }
}
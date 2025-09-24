package com.datadobi.s3test.util;

@FunctionalInterface
public interface SupplierThatThrows<R, E extends Throwable> {
    R get() throws E;
}

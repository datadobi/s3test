package com.datadobi.s3test.util;

import javax.annotation.Nullable;
import java.util.function.Function;

public class SystemPropertyUtil {
    @Nullable
    public static <T> T getValue(String name, @Nullable T defaultValue, Function<String, T> parse) {
        String property = System.getProperty(name);
        if (property == null) {
            return defaultValue;
        }

        try {
            T parsed = parse.apply(property);
            if (parsed != null) {
                return parsed;
            } else {
                return defaultValue;
            }
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String name, boolean defaultValue) {
        return getValue(name, defaultValue, Boolean::valueOf);
    }

    @Nullable
    public static Boolean getBoolean(String name) {
        return getValue(name, null, Boolean::valueOf);
    }

    public static int getInt(String name, int defaultValue) {
        return getValue(name, defaultValue, Integer::parseInt);
    }

    @Nullable
    public static Integer getInt(String name) {
        return getValue(name, null, Integer::parseInt);
    }

    public static long getLong(String name, long defaultValue) {
        return getValue(name, defaultValue, Long::parseLong);
    }

    @Nullable
    public static Long getLong(String name) {
        return getValue(name, null, Long::parseLong);
    }

    @Nullable
    public static String getString(String name, @Nullable String defaultValue) {
        String property = System.getProperty(name);
        if (property == null) {
            return defaultValue;
        }

        return property;
    }
}

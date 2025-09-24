package com.datadobi.s3test.http;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Range(String unit, ImmutableList<RangeSpec> rangeSpecs) {
    // ranges-specifier = range-unit "=" range-set
    //  range-set        = 1#range-spec
    //  range-spec       = int-range
    //                   / suffix-range
    //                   / other-range
    private static final Pattern RANGE = Pattern.compile("(?<unit>[^=]+)=(?<rangesets>.*)");
    private static final Pattern RANGE_SPEC = Pattern.compile("(?<first>[0-9]*)-(?<last>[0-9]*),?");

    @Nullable
    public static Range parseRange(@Nullable String range) {
        if (range == null) {
            return null;
        }

        int split = range.indexOf('=');
        if (split == -1) {
            return null;
        }

        String unit = range.substring(0, split);
        ImmutableList<RangeSpec> rangeSet = parseRangeSpecs(range.substring(split + 1));
        if (rangeSet.isEmpty()) {
            return null;
        }

        return new Range(unit, rangeSet);
    }

    private static ImmutableList<RangeSpec> parseRangeSpecs(String rangeSets) {
        ImmutableList.Builder<RangeSpec> rangeSetBuilder = ImmutableList.builder();

        Matcher rangeSpecMatcher = RANGE_SPEC.matcher(rangeSets);
        while (rangeSpecMatcher.find()) {
            String first = rangeSpecMatcher.group("first");
            String last = rangeSpecMatcher.group("last");
            rangeSetBuilder.add(new RangeSpec(
                    first.isEmpty() ? null : Long.parseLong(first),
                    last.isEmpty() ? null : Long.parseLong(last)
            ));
        }

        return rangeSetBuilder.build();
    }

    @Override
    public String toString() {
        StringBuilder header = new StringBuilder(unit);
        header.append("=");
        for (int i = 0; i < rangeSpecs.size(); i++) {
            if (i > 0) {
                header.append(",");
            }
            header.append(rangeSpecs.get(i));
        }

        return header.toString();
    }
}

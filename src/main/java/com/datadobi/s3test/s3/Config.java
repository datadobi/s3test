/*
 *
 *  Copyright Datadobi
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software

 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.datadobi.s3test.s3;

import com.google.common.collect.ImmutableSet;
import io.github.wasabithumb.jtoml.JToml;
import io.github.wasabithumb.jtoml.value.primitive.TomlPrimitive;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;

public record Config(ImmutableSet<Quirk> quirks) {
    public static final Config AWS_CONFIG = new Config(ImmutableSet.of(
            Quirk.PUT_OBJECT_IF_NONE_MATCH_ETAG_NOT_SUPPORTED
    ));

    public static Config loadFromToml(Path path) {
        var quirks = EnumSet.noneOf(Quirk.class);

        var toml = JToml.jToml();
        var doc = toml.read(path);
        var quirksValue = doc.get("quirks");

        if (quirksValue != null && quirksValue.isArray()) {
            quirksValue.asArray().forEach(item -> {
                if (item.isPrimitive()) {
                    TomlPrimitive primitive = item.asPrimitive();
                    quirks.add(Quirk.valueOf(primitive.asString().toUpperCase(Locale.ROOT)));
                }
            });
        }

        return new Config(ImmutableSet.copyOf(quirks));
    }
}

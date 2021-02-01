/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.debian;

import com.amihaiemil.eoyaml.Yaml;
import java.util.Optional;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Config.FromYaml}.
 * @since 0.2
 */
class ConfigFromYamlTest {

    @Test
    void returnsCodename() {
        final String name = "my-deb";
        MatcherAssert.assertThat(
            new Config.FromYaml(name, Optional.of(Yaml.createYamlMappingBuilder().build()))
                .codename(),
            new IsEqual<>(name)
        );
    }

    @Test
    void returnsComponents() {
        final String comps = "one two three";
        MatcherAssert.assertThat(
            new Config.FromYaml(
                "any",
                Optional.of(Yaml.createYamlMappingBuilder().add("Components", comps).build())
            ).components(),
            Matchers.contains(comps.split(" "))
        );
    }

    @Test
    void returnsArchs() {
        final String archs = "amd64 intel";
        MatcherAssert.assertThat(
            new Config.FromYaml(
                "some",
                Optional.of(Yaml.createYamlMappingBuilder().add("Architectures", archs).build())
            ).archs(),
            Matchers.contains(archs.split(" "))
        );
    }

    @Test
    void failsOnEmptySetting() {
        MatcherAssert.assertThat(
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new Config.FromYaml("abc", Optional.empty())
            ).getMessage(),
            new IsEqual<>("Illegal config: `setting` section is required for debian repos")
        );
    }

    @Test
    void failsToGetComponentIfFieldIsMissing() {
        MatcherAssert.assertThat(
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new Config.FromYaml(
                    "123", Optional.of(Yaml.createYamlMappingBuilder().build())
                ).components()
            ).getMessage(),
            new IsEqual<>("Illegal config: `Components` is required for debian repos")
        );
    }

    @Test
    void failsToGetArchIfFieldIsMissing() {
        MatcherAssert.assertThat(
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new Config.FromYaml(
                    "deb", Optional.of(Yaml.createYamlMappingBuilder().build())
                ).archs()
            ).getMessage(),
            new IsEqual<>("Illegal config: `Architectures` is required for debian repos")
        );
    }

}

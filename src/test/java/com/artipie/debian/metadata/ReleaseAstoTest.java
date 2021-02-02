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
package com.artipie.debian.metadata;

import com.amihaiemil.eoyaml.Yaml;
import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.debian.Config;
import com.artipie.http.slice.KeyFromPath;
import java.util.Optional;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.StringContains;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Release.Asto}.
 * @since 0.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
class ReleaseAstoTest {

    @Test
    void createsReleaseFile() {
        final Storage asto = new InMemoryStorage();
        asto.save(new Key.From("dists/abc/main/binaty-amd64/Packages.gz"), Content.EMPTY);
        asto.save(new Key.From("dists/abc/main/binaty-intel/Packages.gz"), Content.EMPTY);
        new Release.Asto(
            asto,
            new Config.FromYaml(
                "abc",
                Optional.of(
                    Yaml.createYamlMappingBuilder()
                        .add("Components", "main")
                        .add("Architectures", "amd intel")
                        .build()
                )
            )
        ).create().toCompletableFuture().join();
        MatcherAssert.assertThat(
            new PublisherAs(asto.value(new KeyFromPath("dists/abc/Release")).join())
                .asciiString().toCompletableFuture().join(),
            Matchers.allOf(
                new StringContainsInOrder(
                    new ListOf<String>(
                        "Codename: abc",
                        "Architectures: amd intel",
                        "Components: main",
                        "Date:",
                        "SHA256:"
                    )
                ),
                new StringContains("main/binaty-amd64/Packages.gz"),
                new StringContains("main/binaty-intel/Packages.gz")
            )
        );
    }
}

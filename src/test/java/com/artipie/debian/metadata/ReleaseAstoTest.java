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
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.StringContains;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Release.Asto}.
 * @since 0.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ReleaseAstoTest {

    /**
     * Test storage.
     */
    private Storage asto;

    @BeforeEach
    void init() {
        this.asto = new InMemoryStorage();
    }

    @Test
    void createsReleaseFile() {
        this.asto.save(
            new Key.From("dists/abc/main/binaty-amd64/Packages.gz"), Content.EMPTY
        ).join();
        this.asto.save(
            new Key.From("dists/abc/main/binaty-intel/Packages.gz"), Content.EMPTY
        ).join();
        new Release.Asto(
            this.asto,
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
            new PublisherAs(this.asto.value(new KeyFromPath("dists/abc/Release")).join())
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

    @Test
    void addsNewRecord() {
        this.asto.save(
            new Key.From("dists/my-deb/main/binaty-amd64/Packages.gz"), Content.EMPTY
        ).join();
        final Key key = new Key.From("dists/my-deb/main/binaty-intel/Packages.gz");
        this.asto.save(key, Content.EMPTY).join();
        final ListOf<String> content = new ListOf<>(
            "Codename: my-deb",
            "Architectures: amd64 intel",
            "Components: main",
            "Date:",
            "SHA256:",
            " abc123 main/binaty-amd64/Packages.gz"
        );
        this.asto.save(
            new Key.From("dists/my-deb/Release"),
            new Content.From(String.join("\n", content).getBytes(StandardCharsets.UTF_8))
        ).join();
        new Release.Asto(
            this.asto,
            new Config.FromYaml(
                "my-deb",
                Optional.of(Yaml.createYamlMappingBuilder().build())
            )
        ).update(key).toCompletableFuture().join();
        MatcherAssert.assertThat(
            new PublisherAs(this.asto.value(new KeyFromPath("dists/my-deb/Release")).join())
                .asciiString().toCompletableFuture().join(),
            Matchers.allOf(
                new StringContainsInOrder(content),
                new StringContains("main/binaty-intel/Packages.gz")
            )
        );
    }

    @Test
    void updatesRecord() {
        this.asto.save(
            new Key.From("dists/my-repo/main/binaty-amd64/Packages.gz"), Content.EMPTY
        ).join();
        final Key key = new Key.From("dists/my-repo/main/binaty-intel/Packages.gz");
        this.asto.save(key, Content.EMPTY).join();
        final ListOf<String> content = new ListOf<>(
            "Codename: my-repo",
            "Architectures: amd64 intel",
            "Components: main",
            "Date:",
            "SHA256:",
            " xyz098 main/binaty-intel/Packages.gz",
            " abc123 main/binaty-amd64/Packages.gz"
        );
        this.asto.save(
            new Key.From("dists/my-repo/Release"),
            new Content.From(String.join("\n", content).getBytes(StandardCharsets.UTF_8))
        ).join();
        new Release.Asto(
            this.asto,
            new Config.FromYaml(
                "my-repo",
                Optional.of(Yaml.createYamlMappingBuilder().build())
            )
        ).update(key).toCompletableFuture().join();
        // @checkstyle LineLengthCheck (2 lines)
        // @checkstyle MagicNumberCheck (1 line)
        content.set(5, " e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 main/binaty-intel/Packages.gz");
        MatcherAssert.assertThat(
            new PublisherAs(this.asto.value(new KeyFromPath("dists/my-repo/Release")).join())
                .asciiString().toCompletableFuture().join(),
            new IsEqual<>(String.join("\n", content))
        );
    }

    @Test
    void returnsReleaseIndexKey() {
        MatcherAssert.assertThat(
            new Release.Asto(
                this.asto,
                new Config.FromYaml(
                    "deb-repo",
                    Optional.of(Yaml.createYamlMappingBuilder().build())
                )
            ).key(),
            new IsEqual<>(new Key.From("dists/deb-repo/Release"))
        );
    }
}

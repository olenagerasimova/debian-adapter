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
import com.artipie.asto.test.TestResource;
import com.artipie.debian.Config;
import org.cactoos.list.ListOf;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link InRelease.Asto}.
 * @since 0.4
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class InReleaseAstoTest {

    /**
     * Test storage.
     */
    private Storage asto;

    @BeforeEach
    void init() {
        this.asto = new InMemoryStorage();
    }

    @Test
    void generatesInRelease() {
        final String name = "my-deb";
        final Key key = new Key.From("dists", name, "Release");
        new TestResource("Release").saveTo(this.asto, key);
        final String secret = "secret-keys.gpg";
        new TestResource(secret).saveTo(this.asto);
        new InRelease.Asto(
            this.asto,
            new Config.FromYaml(
                name,
                Yaml.createYamlMappingBuilder().add("gpg_password", "1q2w3e4r5t6y7u")
                    .add("gpg_secret_key", secret).build(),
                this.asto
            )
        ).generate(key).toCompletableFuture().join();
        MatcherAssert.assertThat(
            new PublisherAs(this.asto.value(new Key.From("dists", name, "InRelease")).join())
                .asciiString().toCompletableFuture().join(),
            new AllOf<>(
                new ListOf<Matcher<? super String>>(
                    new StringContains(new String(new TestResource("Release").asBytes())),
                    new StringContains("-----BEGIN PGP SIGNED MESSAGE-----"),
                    new StringContains("Hash: SHA256"),
                    new StringContains("-----BEGIN PGP SIGNATURE-----"),
                    new StringContains("-----END PGP SIGNATURE-----")
                )
            )
        );
    }

    @Test
    void generatesIfGpgIsNotSet() {
        final String name = "my-repo";
        final Key.From key = new Key.From("dists", name, "Release");
        this.asto.save(key, Content.EMPTY).join();
        new InRelease.Asto(
            this.asto,
            new Config.FromYaml(
                name,
                Yaml.createYamlMappingBuilder().build(),
                this.asto
            )
        ).generate(key).toCompletableFuture().join();
        MatcherAssert.assertThat(
            this.asto.exists(key).join(),
            new IsEqual<>(true)
        );
    }

}

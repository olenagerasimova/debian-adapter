/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/debian-adapter/LICENSE.txt
 */
package com.artipie.debian.misc;

import com.artipie.asto.Content;
import com.artipie.asto.test.TestResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link UnpackedContent}.
 * @since 0.3
 * @checkstyle MagicNumberCheck (500 lines)
 */
class UnpackedContentTest {

    @Test
    void calcsSizeAndDigest() throws IOException {
        MatcherAssert.assertThat(
            new UnpackedContent(
                new Content.From(new TestResource("Packages.gz").asBytes())
            ).sizeAndDigest().toCompletableFuture().join(),
            new IsEqual<>(
                new ImmutablePair<>(
                    2564L, "c1cfc96b4ca50645c57e10b65fcc89fd1b2b79eb495c9fa035613af7ff97dbff"
                )
            )
        );
        final Path systemtemp = Paths.get(System.getProperty("java.io.tmpdir"));
        MatcherAssert.assertThat(
            "Temp dir for indexes removed",
            Files.list(systemtemp)
                .noneMatch(path -> path.getFileName().toString().startsWith("unpack")),
            new IsEqual<>(true)
        );
    }

}

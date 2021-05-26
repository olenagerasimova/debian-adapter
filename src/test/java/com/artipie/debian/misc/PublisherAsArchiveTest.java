/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/debian-adapter/LICENSE.txt
 */
package com.artipie.debian.misc;

import com.artipie.asto.Content;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link PublisherAsArchive}.
 * @since 0.3
 */
class PublisherAsArchiveTest {

    @Test
    void unpacksContent() throws IOException {
        final byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gcos =
            new GzipCompressorOutputStream(new BufferedOutputStream(baos))) {
            gcos.write(bytes);
        }
        MatcherAssert.assertThat(
            new PublisherAsArchive(new Content.From(baos.toByteArray()))
                .unpackedGz().toCompletableFuture().join(),
            new IsEqual<>(bytes)
        );
    }

}

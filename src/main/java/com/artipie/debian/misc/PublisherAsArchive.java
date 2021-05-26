/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/debian-adapter/LICENSE.txt
 */
package com.artipie.debian.misc;

import com.artipie.asto.Concatenation;
import com.artipie.asto.Remaining;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.reactivestreams.Publisher;

/**
 * Publisher as extension to work with archives.
 * @since 0.3
 */
public final class PublisherAsArchive {

    /**
     * Publisher.
     */
    private final Publisher<ByteBuffer> content;

    /**
     * Ctor.
     * @param content Content
     */
    public PublisherAsArchive(final Publisher<ByteBuffer> content) {
        this.content = content;
    }

    /**
     * Publisher as unpacked gz archive.
     * @return Unpacked bytes
     */
    @SuppressWarnings("PMD.AssignmentInOperand")
    public CompletionStage<byte[]> unpackedGz() {
        return new Concatenation(this.content)
            .single()
            .map(buf -> new Remaining(buf, true))
            .map(Remaining::bytes)
            .map(
                bytes -> {
                    try (
                        GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                            new BufferedInputStream(new ByteArrayInputStream(bytes))
                        )
                    ) {
                        final ByteArrayOutputStream out = new ByteArrayOutputStream();
                        // @checkstyle MagicNumberCheck (1 line)
                        final byte[] buf = new byte[1024];
                        int cnt;
                        while (-1 != (cnt = gcis.read(buf))) {
                            out.write(buf, 0, cnt);
                        }
                        return out.toByteArray();
                    } catch (final IOException err) {
                        throw new UncheckedIOException(err);
                    }
                }
            ).to(SingleInterop.get());
    }
}

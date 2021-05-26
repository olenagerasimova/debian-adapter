/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/debian-adapter/LICENSE.txt
 */
package com.artipie.debian.misc;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletionStage;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.cqfn.rio.WriteGreed;
import org.cqfn.rio.stream.ReactiveOutputStream;
import org.reactivestreams.Publisher;

/**
 * Unpacked content that uses {@link ReactiveOutputStream}.
 * @since 0.6
 */
public final class RosUnpackedContent {

    /**
     * Publisher.
     */
    private final Publisher<ByteBuffer> content;

    /**
     * Ctor.
     * @param content Content
     */
    public RosUnpackedContent(final Publisher<ByteBuffer> content) {
        this.content = content;
    }

    /**
     * Calculates size and digest of the gz packed content.
     * @return Size and digest
     */
    @SuppressWarnings("PMD.AssignmentInOperand")
    public CompletionStage<Pair<Long, String>> sizeAndDigest() {
        try (
            PipedInputStream in = new PipedInputStream();
            PipedOutputStream out = new PipedOutputStream(in)
        ) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final CompletionStage<Void> ros =
                new ReactiveOutputStream(out).write(this.content, WriteGreed.SYSTEM);
            long size = 0;
            try (GzipCompressorInputStream gcis = new GzipCompressorInputStream(in)) {
                // @checkstyle MagicNumberCheck (1 line)
                final byte[] buf = new byte[1024];
                int cnt;
                while (-1 != (cnt = gcis.read(buf))) {
                    digest.update(buf, 0, cnt);
                    size = size + cnt;
                }
                final ImmutablePair<Long, String> pair =
                    new ImmutablePair<>(size, Hex.encodeHexString(digest.digest()));
                return ros.thenApply(nothing -> pair);
            }
        } catch (final NoSuchAlgorithmException err) {
            throw new IllegalStateException(err);
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }
}

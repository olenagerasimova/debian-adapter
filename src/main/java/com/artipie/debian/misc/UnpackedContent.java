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
package com.artipie.debian.misc;

import com.artipie.asto.Content;
import com.artipie.asto.FailedCompletionStage;
import com.artipie.asto.Key;
import com.artipie.asto.fs.FileStorage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.reactivestreams.Publisher;

/**
 * Unpacked content.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class UnpackedContent {

    /**
     * Publisher.
     */
    private final Publisher<ByteBuffer> content;

    /**
     * Ctor.
     * @param content Content
     */
    public UnpackedContent(final Publisher<ByteBuffer> content) {
        this.content = content;
    }

    /**
     * Calculates size and digest of the gz packed content.
     * @return Size and digest
     */
    @SuppressWarnings("PMD.AssignmentInOperand")
    public CompletionStage<Pair<Long, String>> sizeAndDigest() {
        try {
            final Path temp = Files.createTempDirectory("unpack-");
            final Path file = Files.createTempFile(temp, "Packages-", ".gz");
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new FileStorage(temp).save(
                new Key.From(file.getFileName().toString()), new Content.From(this.content)
            ).thenApply(
                ignored -> {
                    try (
                        GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                            new BufferedInputStream(Files.newInputStream(file))
                        )
                    ) {
                        long size = 0;
                        // @checkstyle MagicNumberCheck (1 line)
                        final byte[] buf = new byte[1024];
                        int cnt;
                        while (-1 != (cnt = gcis.read(buf))) {
                            digest.update(buf, 0, cnt);
                            size = size + cnt;
                        }
                        return new ImmutablePair<>(size, Hex.encodeHexString(digest.digest()));
                    } catch (final IOException err) {
                        throw new UncheckedIOException(err);
                    }
                }
            ).handle(
                (pair, throwable) -> {
                    final CompletionStage<Pair<Long, String>> res;
                    FileUtils.deleteQuietly(temp.toFile());
                    if (throwable == null) {
                        res = CompletableFuture.completedFuture(pair);
                    } else {
                        res = new FailedCompletionStage<>(throwable);
                    }
                    return res;
                }
            ).thenCompose(Function.identity());
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        } catch (final NoSuchAlgorithmException err) {
            throw new IllegalStateException(err);
        }
    }
}

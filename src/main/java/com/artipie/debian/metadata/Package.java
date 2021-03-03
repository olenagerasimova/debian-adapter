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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * Package index.
 * @since 0.1
 */
public interface Package {

    /**
     * Adds item to the packages index.
     * @param items Index items to add
     * @param index Package index key
     * @return Completion action
     */
    CompletionStage<Void> add(List<String> items, Key index);

    /**
     * Simple {@link Package} implementation: it appends item to the index without any validation.
     * @since 0.1
     * @checkstyle ClassDataAbstractionCouplingCheck (100 lines)
     */
    final class Asto implements Package {

        /**
         * Package index items separator.
         */
        private static final String SEP = "\n\n";

        /**
         * Abstract storage.
         */
        private final Storage asto;

        /**
         * Ctor.
         * @param asto Storage
         */
        public Asto(final Storage asto) {
            this.asto = asto;
        }

        @Override
        public CompletionStage<Void> add(final List<String> items, final Key index) {
            final byte[] bytes = String.join(Asto.SEP, items).getBytes(StandardCharsets.UTF_8);
            return this.asto.exists(index).thenCompose(
                exists -> {
                    final CompletionStage<byte[]> res;
                    if (exists) {
                        res = this.asto.value(index).thenCompose(
                            content -> new PublisherAs(content).bytes()
                        ).thenApply(
                            buffer -> Asto.decompressAppendCompress(buffer, bytes)
                        );
                    } else {
                        res = CompletableFuture.completedFuture(compress(bytes));
                    }
                    return res;
                }
            )
                .thenCompose(res -> this.asto.save(index, new Content.From(res)));
        }

        /**
         * Compress bytes in gz format.
         * @param decompress Bytes to decompress
         * @param append New bytes to append
         * @return Compressed bytes
         */
        @SuppressWarnings("PMD.AssignmentInOperand")
        private static byte[] decompressAppendCompress(
            final byte[] decompress, final byte[] append
        ) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (
                GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                    new BufferedInputStream(new ByteArrayInputStream(decompress))
                );
                GzipCompressorOutputStream gcos =
                    new GzipCompressorOutputStream(new BufferedOutputStream(baos))
            ) {
                // @checkstyle MagicNumberCheck (1 line)
                final byte[] buf = new byte[1024];
                int cnt;
                while (-1 != (cnt = gcis.read(buf))) {
                    gcos.write(buf, 0, cnt);
                }
                gcos.write(Asto.SEP.getBytes(StandardCharsets.UTF_8));
                gcos.write(append);
            } catch (final IOException err) {
                throw new UncheckedIOException(err);
            }
            return baos.toByteArray();
        }

        /**
         * Compress text for new Package index.
         * @param bytes Bytes to compress
         * @return Compressed bytes
         */
        private static byte[] compress(final byte[] bytes) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GzipCompressorOutputStream gcos =
                new GzipCompressorOutputStream(new BufferedOutputStream(baos))
                ) {
                gcos.write(bytes);
            } catch (final IOException err) {
                throw new UncheckedIOException(err);
            }
            return baos.toByteArray();
        }
    }

}

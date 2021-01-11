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
     * @param item Index item to add
     * @param index Package index key
     * @return Completion action
     */
    CompletionStage<Void> add(String item, Key index);

    /**
     * Simple {@link Package} implementation: it appends item to the index without any validation.
     * @since 0.1
     * @checkstyle ClassDataAbstractionCouplingCheck (100 lines)
     */
    final class Simple implements Package {

        /**
         * Abstract storage.
         */
        private final Storage asto;

        /**
         * Ctor.
         * @param asto Storage
         */
        public Simple(final Storage asto) {
            this.asto = asto;
        }

        @Override
        public CompletionStage<Void> add(final String item, final Key index) {
            return this.asto.exists(index).thenCompose(
                exists -> {
                    final CompletionStage<byte[]> res;
                    if (exists) {
                        res = this.asto.value(index).thenCompose(
                            content -> new PublisherAs(content).bytes()
                        ).thenApply(
                            bytes -> Simple.unpackAppendCompress(bytes, item)
                        );
                    } else {
                        res = CompletableFuture.completedFuture(
                            Simple.compress(item.getBytes(StandardCharsets.UTF_8))
                        );
                    }
                    return res;
                }
            ).thenCompose(res -> this.asto.save(index, new Content.From(res)));
        }

        /**
         * Unpacks Packages index file, appends new item and compress the file.
         * @param content Packages file bytes
         * @param item Item to add
         * @return Packages file bytes with added item
         */
        @SuppressWarnings("PMD.AssignmentInOperand")
        private static byte[] unpackAppendCompress(final byte[] content, final String item) {
            try (
                GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                    new BufferedInputStream(new ByteArrayInputStream(content))
                )
            ) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                // @checkstyle MagicNumberCheck (1 line)
                final byte[] buf = new byte[1024];
                int cnt;
                while (-1 != (cnt = gcis.read(buf))) {
                    out.write(buf, 0, cnt);
                }
                out.write("\n\n".getBytes(StandardCharsets.UTF_8));
                out.write(item.getBytes(StandardCharsets.UTF_8));
                return Simple.compress(out.toByteArray());
            } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /**
         * Compress bytes in gz format.
         * @param bytes Bytes to pack
         * @return Compressed bytes
         */
        private static byte[] compress(final byte[] bytes) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GzipCompressorOutputStream gcos =
                new GzipCompressorOutputStream(new BufferedOutputStream(baos))) {
                gcos.write(bytes);
            } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return baos.toByteArray();
        }

    }

}

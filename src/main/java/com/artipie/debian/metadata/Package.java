/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/debian-adapter/LICENSE.txt
 */
package com.artipie.debian.metadata;

import com.artipie.asto.Content;
import com.artipie.asto.Copy;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.fs.FileStorage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FileUtils;
import org.cactoos.list.ListOf;

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
    CompletionStage<Void> add(Iterable<String> items, Key index);

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
        public CompletionStage<Void> add(final Iterable<String> items, final Key index) {
            final byte[] bytes = String.join(Asto.SEP, items).getBytes(StandardCharsets.UTF_8);
            return this.asto.exists(index).thenCompose(
                exists -> {
                    final CompletionStage<Void> res;
                    if (exists) {
                        try {
                            final Path temp = Files.createTempDirectory("packages-");
                            final Path latest = Files.createTempFile(temp, "latest-", ".gz");
                            res = new Copy(this.asto, new ListOf<>(index))
                                .copy(new FileStorage(temp))
                                .thenAccept(
                                    nothing -> decompressAppendCompress(
                                        temp.resolve(index.string()), latest, bytes
                                    )
                                ).thenCompose(
                                    nothing -> new FileStorage(temp)
                                        .move(new Key.From(latest.getFileName().toString()), index)
                                ).thenCompose(
                                    nothing -> new Copy(new FileStorage(temp), new ListOf<>(index))
                                        .copy(this.asto)
                                ).thenAccept(nothing -> FileUtils.deleteQuietly(temp.toFile()));
                        } catch (final IOException err) {
                            throw new IllegalStateException("Failed to create temp dir", err);
                        }
                    } else {
                        res = this.asto.save(index, new Content.From(compress(bytes)));
                    }
                    return res;
                }
            );
        }

        /**
         * Decompresses Packages.gz file, appends information and writes compressed result
         * into new file.
         * @param decompress File to decompress
         * @param res Where to write the result
         * @param append New bytes to append
         */
        @SuppressWarnings("PMD.AssignmentInOperand")
        private static void decompressAppendCompress(
            final Path decompress, final Path res, final byte[] append
        ) {
            try (
                OutputStream baos = new BufferedOutputStream(Files.newOutputStream(res));
                GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                    new BufferedInputStream(Files.newInputStream(decompress))
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

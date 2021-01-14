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

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.ContentDigest;
import com.artipie.asto.ext.Digests;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Packages index item.
 * @since 0.1
 */
public interface PackagesItem {

    /**
     * Formats packages item by adding filename, size and checksums to control.
     * @param content Deb package content
     * @param key Deb package key
     * @return Completion action with formatted package item
     */
    CompletionStage<String> format(String content, Key key);

    /**
     * {@link PackagesItem} from abstract storage.
     * @since 0.1
     */
    final class Asto implements PackagesItem {

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
        public CompletionStage<String> format(final String control, final Key deb) {
            return this.asto.value(deb).thenApply(
                val -> Asto.addSizeAndFilename(
                    val.size().orElseThrow(
                        () -> new IllegalStateException("Content size unknown")
                    ), deb.string(), control
                )
            )
            .thenCompose(
                res -> this.digests(deb, Digests.MD5)
                    .thenApply(hex -> Asto.addDigest(res, "MD5sum", hex))
            )
            .thenCompose(
                res -> this.digests(deb, Digests.SHA1)
                    .thenApply(hex -> Asto.addDigest(res, "SHA1", hex))
            )
            .thenCompose(
                res -> this.digests(deb, Digests.SHA256)
                    .thenApply(hex -> Asto.addDigest(res, "SHA256", hex))
            )
            .thenApply(Asto::sort);
        }

        /**
         * Calculates digest for the given algorithm.
         * @param deb Debian package key
         * @param alg Algorithm
         * @return Digest hex
         */
        private CompletionStage<String> digests(final Key deb, final Digests alg) {
            return this.asto.value(deb).thenCompose(
                val -> new ContentDigest(val, alg).hex()
            );
        }

        /**
         * Adds digest to control.
         * @param control Control to append info to
         * @param alg Algorithm
         * @param hex Hex
         * @return Control with digest
         */
        private static String addDigest(final String control, final String alg, final String hex) {
            return Stream.concat(
                Stream.of(control.split("\n")),
                Stream.of(
                    String.format("%s: %s", alg, hex)
                )
            ).collect(Collectors.joining("\n"));
        }

        /**
         * Adds size and filename to the control.
         * @param size Package size
         * @param filename Filename
         * @param control Control file
         * @return Control with size and file name
         * @checkstyle ParameterNumberCheck (5 lines)
         */
        private static String addSizeAndFilename(
            final long size, final String filename, final String control
        ) {
            return Stream.concat(
                Stream.of(control.split("\n")),
                Stream.of(
                    String.format("Filename: %s", filename),
                    String.format("Size: %d", size)
                )
            ).collect(Collectors.joining("\n"));
        }

        /**
         * Sorts packages item: the only condition is that Package field should be first.
         * @param item Package item to sort
         * @return Sorted package item
         */
        private static String sort(final String item) {
            final String res;
            final String name = "Package";
            if (item.startsWith(name)) {
                res = item;
            } else {
                res = Stream.of(item.split("\n")).sorted(
                    (one, two) -> {
                        final int sort;
                        if (one.startsWith(name)) {
                            sort = -1;
                        } else {
                            sort = 1;
                        }
                        return sort;
                    }
                ).collect(Collectors.joining("\n"));
            }
            return res;
        }
    }

}

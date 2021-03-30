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
import com.artipie.asto.ext.ContentDigest;
import com.artipie.asto.ext.Digests;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.debian.Config;
import com.artipie.debian.GpgConfig;
import com.artipie.debian.misc.GpgClearsign;
import com.artipie.debian.misc.RosUnpackedContent;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Observable;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Release metadata file.
 * @since 0.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public interface Release {

    /**
     * Creates Release metadata file for the repository.
     * @return Completed action
     */
    CompletionStage<Void> create();

    /**
     * Updates (or adds) info of the package.
     * @param pckg Package index key to update/add
     * @return Completed action
     */
    CompletionStage<Void> update(Key pckg);

    /**
     * Release index file storage key.
     * @return Item key
     */
    Key key();

    /**
     * Key of the storage item with the detached GPG signature of the Release index.
     * @return Item key
     */
    Key gpgSignatureKey();

    /**
     * Implementation of {@link Release} from abstract storage.
     * @since 0.2
     */
    final class Asto implements Release {

        /**
         * Abstract storage.
         */
        private final Storage asto;

        /**
         * Repository config.
         */
        private final Config config;

        /**
         * Ctor.
         * @param asto Abstract storage
         * @param config Repository config
         */
        public Asto(final Storage asto, final Config config) {
            this.asto = asto;
            this.config = config;
        }

        @Override
        public CompletionStage<Void> create() {
            return this.checksums()
                .thenApply(
                    checksums -> String.join(
                        "\n",
                        String.format("Codename: %s", this.config.codename()),
                        String.format("Architectures: %s", String.join(" ", this.config.archs())),
                        String.format("Components: %s", String.join(" ", this.config.components())),
                        String.format(
                            "Date: %s",
                            DateTimeFormatter.ofPattern("E, MMM dd yyyy HH:mm:ss Z")
                                .format(ZonedDateTime.now())
                        ),
                        "SHA256:",
                        checksums
                    )
                ).thenApply(str -> str.getBytes(StandardCharsets.UTF_8))
                .thenCompose(
                    bytes -> this.asto.save(this.key(), new Content.From(bytes))
                        .thenCompose(nothing -> this.handleGpg(bytes))
                );
        }

        @Override
        public CompletionStage<Void> update(final Key pckg) {
            final String key = pckg.string().replace(this.subDir(), "");
            return this.packageData(pckg).thenCompose(
                pair -> this.asto.value(this.key()).thenCompose(
                    content -> new PublisherAs(content).asciiString().thenApply(
                        str -> Asto.addReplace(str, key, pair.getLeft())
                    ).thenApply(
                        str -> Asto.addReplace(str, key.replace(".gz", ""), pair.getRight())
                    )
                )
            ).thenApply(str -> str.getBytes(StandardCharsets.UTF_8))
                .thenCompose(
                    bytes -> this.asto.save(this.key(), new Content.From(bytes))
                        .thenCompose(nothing -> this.handleGpg(bytes))
            );
        }

        @Override
        public Key key() {
            return new Key.From(String.format("dists/%s/Release", this.config.codename()));
        }

        @Override
        public Key gpgSignatureKey() {
            return new Key.From(String.format("dists/%s/Release.gpg", this.config.codename()));
        }

        /**
         * Handles gpg clearsign: generates the signature if corresponding settings are provided or
         * removes the .gpg file if it is present and settings are not provided.
         * @param release Release file bytes
         * @return Completion action
         */
        private CompletionStage<Void> handleGpg(final byte[] release) {
            final CompletionStage<Void> res;
            if (this.config.gpg().isPresent()) {
                final GpgConfig gpg = this.config.gpg().get();
                res = gpg.key().thenApply(
                    key -> new GpgClearsign(release).signature(key, gpg.password())
                ).thenCompose(
                    sign -> this.asto.save(this.gpgSignatureKey(), new Content.From(sign))
                );
            } else {
                res = this.asto.exists(this.gpgSignatureKey()).thenCompose(
                    exists -> {
                        final CompletionStage<Void> del;
                        if (exists) {
                            del = this.asto.delete(this.gpgSignatureKey());
                        } else {
                            del = CompletableFuture.allOf();
                        }
                        return del;
                    }
                );
            }
            return res;
        }

        /**
         * Repository subdirectory.
         * @return Subdir path
         */
        private String subDir() {
            return String.format("dists/%s/", this.config.codename());
        }

        /**
         * SHA256 checksums of Packages.gz files.
         * @return Checksums future
         */
        private CompletionStage<String> checksums() {
            final RxStorageWrapper rxsto = new RxStorageWrapper(this.asto);
            return rxsto.list(Key.ROOT).flatMapObservable(Observable::fromIterable)
                .filter(key -> key.string().endsWith("Packages.gz"))
                .flatMapSingle(
                    item -> SingleInterop.fromFuture(this.packageData(item))
                ).collect(
                    StringBuilder::new,
                    (builder, pair) -> builder.append(pair.getKey()).append("\n")
                        .append(pair.getValue()).append("\n")
                )
                .map(StringBuilder::toString)
                .to(SingleInterop.get());
        }

        /**
         * Calculates lines of the following format
         *  sha256 size relative_path.gz
         *  sha256 size relative_path
         * for the Package index file.
         * @param pkg Package key
         * @return Pair of lines for Package index
         */
        private CompletionStage<Pair<String, String>> packageData(final Key pkg) {
            final String key = pkg.string().replace(this.subDir(), "");
            return this.asto.value(pkg).thenCompose(
                content -> new ContentDigest(content, Digests.SHA256).hex()
            ).thenCompose(
                hex -> this.asto.value(pkg).thenCompose(
                    content -> new RosUnpackedContent(content).sizeAndDigest().thenApply(
                        data -> new ImmutablePair<>(
                            String.format(
                                " %s %d %s", hex,
                                content.size().orElseThrow(
                                    () -> new IllegalStateException("Content size unknown")
                                ),
                                key
                            ),
                            String.format(
                                " %s %d %s",
                                data.getValue(), data.getKey(), key.replace(".gz", "")
                            )
                        )
                    )
                )
            );
        }

        /**
         * Adds or replaces Package index line in Release index.
         * @param origin Release index
         * @param key Package index relative path
         * @param repl Replacement
         * @return Corrected Release index
         */
        private static String addReplace(final String origin, final String key, final String repl) {
            final String res;
            if (origin.contains(String.format("%s\n", key)) || origin.endsWith(key)) {
                res = origin.replaceAll(
                    String.format(" .* %s(\n|$)", Pattern.quote(key)), String.format("%s\n", repl)
                );
            } else {
                res = String.format("%s\n%s\n", origin, repl);
            }
            return res.replaceAll("\n+", "\n");
        }
    }
}

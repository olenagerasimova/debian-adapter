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
import com.artipie.asto.SubStorage;
import com.artipie.asto.ext.ContentDigest;
import com.artipie.asto.ext.Digests;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.debian.Config;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Observable;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import org.cactoos.map.MapEntry;

/**
 * Release metadata file.
 * @since 0.2
 */
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
                ).thenCompose(
                    file -> this.asto.save(
                        this.key(),
                        new Content.From(file.getBytes(StandardCharsets.UTF_8))
                    )
                );
        }

        @Override
        public CompletionStage<Void> update(final Key pckg) {
            final String key = pckg.string().replace(this.subDir(), "");
            return this.asto.value(pckg).thenCompose(
                content -> new ContentDigest(content, Digests.SHA256).hex()
            ).thenCompose(
                hex -> this.asto.value(this.key()).thenCompose(
                    content -> new PublisherAs(content).asciiString().thenApply(
                        str -> {
                            final String res;
                            if (str.contains(key)) {
                                res = str.replaceAll(
                                    String.format(" .* %s\n", Pattern.quote(key)),
                                    String.format(" %s %s\n", hex, key)
                                );
                            } else {
                                res = String.format("%s\n %s %s", str, hex, key);
                            }
                            return res;
                        }
                    ).thenCompose(
                        str -> this.asto.save(
                            this.key(),
                            new Content.From(str.getBytes(StandardCharsets.UTF_8))
                        )
                    )
                )
            );
        }

        @Override
        public Key key() {
            return new Key.From(String.format("dists/%s/Release", this.config.codename()));
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
            final SubStorage sub = new SubStorage(new Key.From(this.subDir()), this.asto);
            final RxStorageWrapper rxsto = new RxStorageWrapper(sub);
            return rxsto.list(Key.ROOT).flatMapObservable(Observable::fromIterable)
                .filter(key -> key.string().endsWith("Packages.gz"))
                .flatMapSingle(
                    item -> SingleInterop.fromFuture(
                        sub.value(item).thenCompose(
                            content -> new ContentDigest(content, Digests.SHA256).hex()
                        ).thenApply(hash -> new MapEntry<>(item, hash))
                    )
                ).collect(
                    StringBuilder::new,
                    (builder, hash) -> builder.append(" ").append(hash.getValue())
                        .append(" ").append(hash.getKey().string()).append("\n")
                )
                .map(StringBuilder::toString)
                .to(SingleInterop.get());
        }
    }
}

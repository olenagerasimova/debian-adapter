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
import com.artipie.debian.Config;
import com.artipie.debian.GpgConfig;
import com.artipie.debian.misc.GpgClearsign;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * InRelease index file.
 * https://wiki.debian.org/DebianRepository/Format#A.22Release.22_files
 * @since 0.4
 */
public interface InRelease {

    /**
     * Generates InRelease index file by provided Release index.
     * @param release Release index key
     * @return Completion action
     */
    CompletionStage<Void> generate(Key release);

    /**
     * Key (storage item key) of the InRelease index.
     * @return Storage item
     */
    Key key();

    /**
     * Implementation of {@link InRelease} from abstract storage.
     * @since 0.4
     */
    final class Asto implements InRelease {

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
        public CompletionStage<Void> generate(final Key release) {
            final CompletionStage<Void> res;
            if (this.config.gpg().isPresent()) {
                final GpgConfig gpg = this.config.gpg().get();
                res = this.asto.value(release).thenApply(PublisherAs::new)
                    .thenCompose(PublisherAs::bytes)
                    .thenCompose(
                        bytes -> gpg.key().thenApply(
                            key -> new GpgClearsign(bytes).signedContent(key, gpg.password())
                        )
                    ).thenCompose(bytes -> this.asto.save(this.key(), new Content.From(bytes)));
            } else {
                res = this.asto.exists(this.key()).thenCompose(
                    exists -> {
                        final CompletionStage<Void> del;
                        if (exists) {
                            del = this.asto.delete(this.key());
                        } else {
                            del = CompletableFuture.allOf();
                        }
                        return del;
                    }
                );
            }
            return res;
        }

        @Override
        public Key key() {
            return new Key.From("dists", this.config.codename(), "InRelease");
        }
    }
}

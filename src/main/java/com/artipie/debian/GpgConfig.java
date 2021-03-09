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
package com.artipie.debian;

import com.amihaiemil.eoyaml.YamlMapping;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.http.slice.KeyFromPath;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Gpg configuration.
 * @since 0.4
 */
public interface GpgConfig {

    /**
     * Password to unlock gpg-private key.
     * @return String password
     */
    String password();

    /**
     * Gpg-private key.
     * @return Completion action with key bytes
     */
    CompletionStage<byte[]> key();

    /**
     * Gpg-configuration from yaml settings.
     * @since 0.4
     */
    final class FromYaml implements GpgConfig {

        /**
         * Gpg password field name.
         */
        static final String GPG_PASSWORD = "gpg_password";

        /**
         * Gpg secret key path field name.
         */
        static final String GPG_SECRET_KEY = "gpg_secret_key";

        /**
         * Setting in yaml format.
         */
        private final YamlMapping yaml;

        /**
         * Artipie configuration storage.
         */
        private final Storage storage;

        /**
         * Ctor.
         * @param yaml Yaml `settings` section
         * @param storage Artipie configuration storage
         */
        public FromYaml(final Optional<YamlMapping> yaml, final Storage storage) {
            this(
                yaml.orElseThrow(
                    () -> new IllegalArgumentException(
                        "Illegal config: `setting` section is required for debian repos"
                    )
                ),
                storage
            );
        }

        /**
         * Ctor.
         * @param yaml Yaml `settings` section
         * @param storage Artipie configuration storage
         */
        public FromYaml(final YamlMapping yaml, final Storage storage) {
            this.yaml = yaml;
            this.storage = storage;
        }

        @Override
        public String password() {
            return this.yaml.string(FromYaml.GPG_PASSWORD);
        }

        @Override
        public CompletionStage<byte[]> key() {
            return this.storage.value(new KeyFromPath(this.yaml.string(FromYaml.GPG_SECRET_KEY)))
                .thenApply(PublisherAs::new)
                .thenCompose(PublisherAs::bytes);
        }
    }
}

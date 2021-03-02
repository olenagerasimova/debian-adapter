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

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Debian repository.
 * @since 0.4
 */
public interface Debian {

    /**
     * Updates or creates Packages index file by adding information about provided
     * packages list. For mo information about Packages index file check the
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Packages.22_Indices">documentation</a>.
     * @param debs Packages '.deb' list to add
     * @param packages Packages index file
     * @return Completion action
     */
    CompletionStage<Void> updatePackages(List<Key> debs, Key packages);

    /**
     * Updates Release index file by adding information about Packages index file and generate
     * corresponding Release.gpg file with the GPG signature. Find more information in the
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Release.22_files">documentation</a>.
     * @param packages Packages index file to add info about
     * @return Completion action
     */
    CompletionStage<Void> updateRelease(Key packages);

    /**
     * Updates InRelease index file by adding information about Packages index file and signs the
     * file with the GPG signature. Find more information in the
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Release.22_files">documentation</a>.
     * @param packages Packages index file to add info about
     * @return Completion action
     */
    CompletionStage<Void> updateInRelease(Key packages);

    /**
     * Generates Release index file and corresponding Release.gpg file with the GPG
     * signature. Find more information in the
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Release.22_files">documentation</a>.
     * @return Completion action
     */
    CompletionStage<Void> generateRelease();

    /**
     * Generates InRelease index file and signs it with a GPG clearsign signature. Check
     * <a href="https://wiki.debian.org/DebianRepository/Format#A.22Release.22_files">documentation</a>
     * for more details.
     * @return Completion action
     */
    CompletionStage<Void> generateInRelease();

    /**
     * Implementation of {@link Debian} from abstract storage.
     * @since 0.4
     */
    final class Asto implements Debian {

        /**
         * Abstract storage.
         */
        private final Storage asto;

        /**
         * Repository configuration.
         */
        private final Config config;

        /**
         * Ctor.
         * @param asto Abstract storage
         * @param config Repository configuration
         */
        public Asto(final Storage asto, final Config config) {
            this.asto = asto;
            this.config = config;
        }

        @Override
        public CompletionStage<Void> updatePackages(final List<Key> debs, final Key packages) {
            throw new NotImplementedException("Not yet implemented");
        }

        @Override
        public CompletionStage<Void> updateRelease(final Key packages) {
            throw new NotImplementedException("Will be implemented letter");
        }

        @Override
        public CompletionStage<Void> updateInRelease(final Key packages) {
            throw new NotImplementedException("Will be implemented some day");
        }

        @Override
        public CompletionStage<Void> generateRelease() {
            throw new NotImplementedException("Not implemented yet");
        }

        @Override
        public CompletionStage<Void> generateInRelease() {
            throw new NotImplementedException("To be implemented");
        }
    }
}

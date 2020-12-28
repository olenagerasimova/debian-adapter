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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;

/**
 * Control metadata file from debian package.
 * See <a href="https://www.debian.org/doc/debian-policy/ch-controlfields.html">docs</a>.
 * @since 0.1
 */
public interface Control {

    /**
     * Control file content as string.
     * @return String with package info
     */
    String asString();

    /**
     * Control from debian binary package.
     * Check <a href="https://www.debian.org/doc/debian-policy/ch-controlfields.html#binary-package-control-files-debian-control">docs</a>.
     * @since 0.1
     */
    final class FromBinary implements Control {

        /**
         * Control file name.
         */
        private static final String FILE_NAME = "control";

        /**
         * Path to debian binary package.
         */
        private final Path pkg;

        /**
         * Ctor.
         *
         * @param pkg Debian binary package
         */
        public FromBinary(final Path pkg) {
            this.pkg = pkg;
        }

        @Override
        @SuppressWarnings({"PMD.AssignmentInOperand", "PMD.CyclomaticComplexity"})
        public String asString() {
            try (
                ArchiveInputStream input = new ArchiveStreamFactory().createArchiveInputStream(
                    new BufferedInputStream(Files.newInputStream(this.pkg))
                )
            ) {
                ArchiveEntry first;
                while ((first = input.getNextEntry()) != null) {
                    if (!input.canReadEntryData(first)) {
                        continue;
                    }
                    if (first.getName().startsWith(FromBinary.FILE_NAME)) {
                        try (
                            GzipCompressorInputStream internal =
                                new GzipCompressorInputStream(input);
                            TarArchiveInputStream tar = new TarArchiveInputStream(internal)
                        ) {
                            TarArchiveEntry second;
                            while ((second = (TarArchiveEntry) tar.getNextEntry()) != null) {
                                if (second.isFile()
                                    && second.getName().endsWith(FromBinary.FILE_NAME)) {
                                    return IOUtils.toString(tar, StandardCharsets.UTF_8);
                                }
                            }
                        }
                    }
                }
            } catch (final ArchiveException | IOException ex) {
                throw new IllegalStateException("Failed to obtain package metadata", ex);
            }
            throw new IllegalStateException("Package metadata not found");
        }
    }
}

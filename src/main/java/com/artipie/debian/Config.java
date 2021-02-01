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
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Debian repository configuration.
 * @since 0.2
 */
public interface Config {

    /**
     * Repository codename.
     * @return String codename
     */
    String codename();

    /**
     * Repository components (subdirectories).
     * @return Components list
     */
    Collection<String> components();

    /**
     * List of the architectures repository supports.
     * @return Supported architectures
     */
    Collection<String> archs();

    /**
     * Implementation of {@link Config} that reads settings from yaml.
     * @since 0.2
     */
    final class FromYaml implements Config {

        /**
         * Repository name.
         */
        private final String name;

        /**
         * Setting in yaml format.
         */
        private final YamlMapping yaml;

        /**
         * Ctor.
         * @param name Repository name
         * @param yaml Setting in yaml format
         */
        public FromYaml(final String name, final Optional<YamlMapping> yaml) {
            this.name = name;
            this.yaml = yaml.orElseThrow(
                () -> new IllegalArgumentException(
                    "Illegal config: `setting` section is required for debian repos"
                )
            );
        }

        @Override
        public String codename() {
            return this.name;
        }

        @Override
        public Collection<String> components() {
            return this.getValue("Components").orElseThrow(
                () -> new IllegalArgumentException(
                    "Illegal config: `Components` is required for debian repos"
                )
            );
        }

        @Override
        public Collection<String> archs() {
            return this.getValue("Architectures").orElseThrow(
                () -> new IllegalArgumentException(
                    "Illegal config: `Architectures` is required for debian repos"
                )
            );
        }

        /**
         * Get field value from yaml.
         * @param field Field name
         * @return Field value list
         */
        private Optional<Collection<String>> getValue(final String field) {
            return Optional.ofNullable(this.yaml.string(field)).map(
                val -> Arrays.asList(val.split(" "))
            );
        }
    }

}

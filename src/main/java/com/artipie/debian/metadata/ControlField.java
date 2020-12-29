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

import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Control file field.
 * See <a href="https://www.debian.org/doc/debian-policy/ch-controlfields.html">docs</a>.
 * @since 0.1
 */
public interface ControlField {

    /**
     * Control file field values.
     * @param control Control file as string
     * @return Values of the field
     */
    String[] value(String control);

    /**
     * Wrap for {@link ControlField}.
     * @since 0.1
     */
    abstract class Wrap implements ControlField {

        /**
         * Field name.
         */
        private final String field;

        /**
         * Ctor.
         * @param field Field
         */
        protected Wrap(final String field) {
            this.field = field;
        }

        @Override
        public String[] value(final String control) {
            return Stream.of(control.split("\n")).filter(item -> item.startsWith(this.field))
                .findFirst()
                //@checkstyle StringLiteralsConcatenationCheck (1 line)
                .map(item -> item.substring(item.indexOf(":") + 2))
                .map(res -> res.split(" "))
                .orElseThrow(
                    () -> new NoSuchElementException(
                        String.format("Field %s not found in control", this.field)
                    )
                );
        }
    }

    /**
     * Architecture.
     * @since 0.1
     */
    final class Architecture extends Wrap {

        /**
         * Ctor.
         */
        protected Architecture() {
            super("Architecture");
        }
    }
}

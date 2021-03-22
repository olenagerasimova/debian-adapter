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
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link ControlField}.
 * @since 0.1
 */
class ControlFieldTest {

    @Test
    void extractsArchitectureField() {
        MatcherAssert.assertThat(
            new ControlField.Architecture().value(
                String.join(
                    "\n",
                    "Package: aglfn",
                    "Version: 1.7-3",
                    "Architecture: all",
                    "Maintainer: Debian Fonts Task Force <pkg-fonts-devel@lists.alioth.debian.org>",
                    "Installed-Size: 138",
                    "Section: fonts"
                )
            ),
            Matchers.contains("all")
        );
    }

    @Test
    void extractsArchitecturesField() {
        MatcherAssert.assertThat(
            new ControlField.Architecture().value(
                String.join(
                    "\n",
                    "Package: abc",
                    "Version: 0.1",
                    "Architecture: amd64 amd32"
                )
            ),
            Matchers.contains("amd64", "amd32")
        );
    }

    @Test
    void extractsPackageField() {
        MatcherAssert.assertThat(
            new ControlField.Package().value(
                String.join(
                    "\n",
                    "Package: xyz",
                    "Version: 0.3",
                    "Architecture: amd64 intell"
                )
            ),
            Matchers.contains("xyz")
        );
    }

    @Test
    void extractsVersionField() {
        MatcherAssert.assertThat(
            new ControlField.Version().value(
                String.join(
                    "\n",
                    "Package: 123",
                    "Version: 0.987",
                    "Architecture: amd32"
                )
            ),
            Matchers.contains("0.987")
        );
    }

    @Test
    void throwsExceptionWhenElementNotFound() {
        Assertions.assertThrows(
            NoSuchElementException.class,
            () -> new ControlField.Architecture().value("invalid control")
        );
    }

}

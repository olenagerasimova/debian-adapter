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
import com.artipie.asto.memory.InMemoryStorage;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link PackagesItem.Asto}.
 * @since 0.1
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class PackagesItemTest {

    @Test
    void addsInfoAndSorts() {
        final Storage asto = new InMemoryStorage();
        final Key.From key = new Key.From("some/debian/package.deb");
        asto.save(key, new Content.From("abc123".getBytes())).join();
        MatcherAssert.assertThat(
            new PackagesItem.Asto(
                asto
            ).format(
                String.join(
                    "\n",
                    "Version: 1.7-3",
                    "Package: aglfn",
                    "Architecture: all",
                    "Maintainer: Debian Fonts Task Force <pkg-fonts-devel@lists.alioth.debian.org>",
                    "Installed-Size: 138",
                    "Section: fonts"
                ),
                key
            ).toCompletableFuture().join(),
            new IsEqual<>(
                String.join(
                    "\n",
                    "Package: aglfn",
                    "Version: 1.7-3",
                    "Architecture: all",
                    "Maintainer: Debian Fonts Task Force <pkg-fonts-devel@lists.alioth.debian.org>",
                    "Installed-Size: 138",
                    "Section: fonts",
                    "Filename: some/debian/package.deb",
                    "Size: 6",
                    "MD5sum: e99a18c428cb38d5f260853678922e03",
                    "SHA1: 246ffaf3e5e06259e663d404f16764171216c538",
                    "SHA256: 66f92b0628fb5fcbc76b9e1388f4f4d1ebf5a68835f05a03a876e08c56f46ab3"
                )
            )
        );
    }

}

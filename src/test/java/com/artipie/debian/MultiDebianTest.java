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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link MultiDebian.MergedPackages}.
 * @since 0.6
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class MultiDebianTest {

    @Test
    void mergesPackages() throws IOException {
        final ByteArrayOutputStream res = new ByteArrayOutputStream();
        new MultiDebian.MergedPackages().merge(
            new ListOf<InputStream>(
                new ByteArrayInputStream(
                    new GzArchive().compress(this.abcPackageInfo().getBytes(StandardCharsets.UTF_8))
                ),
                new ByteArrayInputStream(
                    new GzArchive().compress(this.xyzPackageInfo().getBytes(StandardCharsets.UTF_8))
                )
            ),
            res
        );
        MatcherAssert.assertThat(
            new GzArchive().decompress(res.toByteArray()),
            new IsEqual<>(
                String.join("\n\n", this.abcPackageInfo(), this.xyzPackageInfo(), "")
            )
        );
    }

    @Test
    void addsOnlyUniquePackages() throws IOException {
        final ByteArrayOutputStream res = new ByteArrayOutputStream();
        new MultiDebian.MergedPackages().merge(
            new ListOf<InputStream>(
                new ByteArrayInputStream(
                    new GzArchive().compress(
                        String.join(
                            "\n\n",
                            this.abcPackageInfo(),
                            this.zeroPackageInfo()
                        ).getBytes(StandardCharsets.UTF_8)
                    )
                ),
                new ByteArrayInputStream(
                    new GzArchive().compress(
                        this.zeroPackageInfo().getBytes(StandardCharsets.UTF_8)
                    )
                ),
                new ByteArrayInputStream(
                    new GzArchive().compress(
                        String.join(
                            "\n\n",
                            this.xyzPackageInfo(),
                            this.zeroPackageInfo()
                        ).getBytes(StandardCharsets.UTF_8)
                    )
                )
            ),
            res
        );
        MatcherAssert.assertThat(
            new GzArchive().decompress(res.toByteArray()),
            new IsEqual<>(
                String.join(
                    "\n\n", this.abcPackageInfo(), this.zeroPackageInfo(), this.xyzPackageInfo(), ""
                )
            )
        );
    }

    @Test
    void addsSamePackagesWithDiffVersions() throws IOException {
        final ByteArrayOutputStream res = new ByteArrayOutputStream();
        final String two = this.abcPackageInfo().replace("0.1", "0.2");
        final String three = this.abcPackageInfo().replace("0.1", "0.3");
        new MultiDebian.MergedPackages().merge(
            new ListOf<InputStream>(
                new ByteArrayInputStream(
                    new GzArchive().compress(
                        String.join(
                            "\n\n", two, this.abcPackageInfo()
                        ).getBytes(StandardCharsets.UTF_8)
                    )
                ),
                new ByteArrayInputStream(
                    new GzArchive().compress(
                        this.abcPackageInfo().getBytes(StandardCharsets.UTF_8)
                    )
                ),
                new ByteArrayInputStream(
                    new GzArchive().compress(
                        String.join("\n\n", two, three).getBytes(StandardCharsets.UTF_8)
                    )
                )
            ),
            res
        );
        MatcherAssert.assertThat(
            new GzArchive().decompress(res.toByteArray()),
            new IsEqual<>(String.join("\n\n", two, this.abcPackageInfo(), three, ""))
        );
    }

    private String xyzPackageInfo() {
        return String.join(
            "\n",
            "Package: xyz",
            "Version: 0.6",
            "Architecture: amd64",
            "Maintainer: Blue Sky",
            "Installed-Size: 131",
            "Section: Un-Existing",
            "Filename: some/not/existing/package.deb",
            "Size: 45",
            "MD5sum: e99a18c428cb38d5f260883678922e03"
        );
    }

    private String abcPackageInfo() {
        return String.join(
            "\n",
            "Package: abc",
            "Version: 0.1",
            "Architecture: all",
            "Maintainer: Task Force",
            "Installed-Size: 130",
            "Section: The Force",
            "Filename: my/repo/abc.deb",
            "Size: 23",
            "MD5sum: e99a18c428cb38d5f260853678922e03"
        );
    }

    private String zeroPackageInfo() {
        return String.join(
            "\n",
            "Package: zero",
            "Version: 0.0",
            "Architecture: all",
            "Maintainer: Zero division",
            "Installed-Size: 0",
            "Section: Zero",
            "Filename: zero/division/package.deb",
            "Size: 0",
            "MD5sum: 0000"
        );
    }

}

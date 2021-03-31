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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link MultiDebian.MergedPackages}.
 * @since 0.6
 */
class MultiDebianTest {

    @Test
    void mergesPackages() throws IOException {
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        new MultiDebian.MergedPackages().merge(
            new ListOf<InputStream>(
                new ByteArrayInputStream(
                    this.pack(this.abcPackageInfo().getBytes(StandardCharsets.UTF_8))
                ),
                new ByteArrayInputStream(
                    this.pack(this.xyzPackageInfo().getBytes(StandardCharsets.UTF_8))
                )
            ),
            res
        );
        MatcherAssert.assertThat(
            this.unpack(res.toByteArray()),
            new IsEqual<>(
                String.join("\n\n", this.abcPackageInfo(), this.xyzPackageInfo(), "")
            )
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

    private byte[] pack(byte[] data) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gcos =
                 new GzipCompressorOutputStream(new BufferedOutputStream(baos))) {
            gcos.write(data);
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
        return baos.toByteArray();
    }

    final String unpack(final byte[] data) {
        try (
            GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                new BufferedInputStream(new ByteArrayInputStream(data))
            )
        ) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[1024];
            int cnt;
            while (-1 != (cnt = gcis.read(buf))) {
                out.write(buf, 0, cnt);
            }
            return out.toString();
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }
}
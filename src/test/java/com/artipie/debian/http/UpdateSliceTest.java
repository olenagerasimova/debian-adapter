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
package com.artipie.debian.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.http.Headers;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.hm.SliceHasResponse;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link UpdateSlice}.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle MagicNumberCheck (500 lines)
 */
@SuppressWarnings({"PMD.AssignmentInOperand", "PMD.AvoidDuplicateLiterals"})
class UpdateSliceTest {

    /**
     * Test storage.
     */
    private Storage asto;

    @BeforeEach
    void init() {
        this.asto = new InMemoryStorage();
    }

    @Test
    void uploadsAndCreatesIndex() {
        MatcherAssert.assertThat(
            "Response is OK",
            new UpdateSlice(this.asto, "my_repo"),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine(RqMethod.PUT, "/main/aglfn_1.7-3_all.deb"),
                Headers.EMPTY,
                new Content.From(new TestResource("aglfn_1.7-3_all.deb").asBytes())
            )
        );
        MatcherAssert.assertThat(
            "Packages index added",
            this.asto.exists(new Key.From("dists/my_repo/main/binary-all/Packages.gz")).join(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "Debian package added",
            this.asto.exists(new Key.From("main/aglfn_1.7-3_all.deb")).join(),
            new IsEqual<>(true)
        );
    }

    @Test
    void uploadsAndUpdatesIndex() throws IOException {
        final String key = "dists/deb_repo/main/binary-all/Packages.gz";
        new TestResource("Packages.gz").saveTo(this.asto, new Key.From(key));
        MatcherAssert.assertThat(
            "Response is OK",
            new UpdateSlice(this.asto, "deb_repo"),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine(RqMethod.PUT, "/main/cm-super_0.3.4-14_all.deb"),
                Headers.EMPTY,
                new Content.From(new TestResource("cm-super_0.3.4-14_all.deb").asBytes())
            )
        );
        MatcherAssert.assertThat(
            "Debian package added",
            this.asto.exists(new Key.From("main/cm-super_0.3.4-14_all.deb")).join(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            this.archiveAsString(key),
            new StringContainsInOrder(
                new ListOf<String>(
                    "Package: aglfn",
                    "Package: pspp",
                    "Package: cm-super"
                )
            )
        );
    }

    @Test
    void returnsErrorAndRemovesItem() {
        MatcherAssert.assertThat(
            "Response is internal error",
            new UpdateSlice(this.asto, "my_repo"),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.INTERNAL_ERROR),
                new RequestLine(RqMethod.PUT, "/main/corrupted.deb"),
                Headers.EMPTY,
                new Content.From("abc123".getBytes())
            )
        );
        MatcherAssert.assertThat(
            "Debian package was not added",
            this.asto.exists(new Key.From("main/corrupted.deb")).join(),
            new IsEqual<>(false)
        );
    }

    /**
     * Uncompress gz archive from storage.
     * @param key Storage item key
     * @return Uncompressed content as string
     * @throws IOException On error
     * @todo #11:30min This method is duplicated in PackageSimpleTest, let's extract class from it.
     *  Class should be located in test scope, accept Storage as a field and have one method to
     *  uncompress storage item by key and return string.
     */
    private String archiveAsString(final String key) throws IOException {
        try (
            GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                new BufferedInputStream(
                    new ByteArrayInputStream(
                        new BlockingStorage(this.asto).value(new Key.From(key))
                    )
                )
            )
        ) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[1024];
            int cnt;
            while (-1 != (cnt = gcis.read(buf))) {
                out.write(buf, 0, cnt);
            }
            return out.toString();
        }
    }

}
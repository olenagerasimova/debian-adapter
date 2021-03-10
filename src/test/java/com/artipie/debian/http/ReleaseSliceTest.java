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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.debian.metadata.Release;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.hm.SliceHasResponse;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.slice.SliceSimple;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.NotImplementedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link ReleaseSlice}.
 * @since 0.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
class ReleaseSliceTest {

    /**
     * Test storage.
     */
    private Storage asto;

    @BeforeEach
    void init() {
        this.asto = new InMemoryStorage();
    }

    @Test
    void createsReleaseFileAndForwardsResponse() {
        final Fake release = new Fake(new Key.From("any"));
        MatcherAssert.assertThat(
            "Response is CREATED",
            new ReleaseSlice(
                new SliceSimple(new RsWithStatus(RsStatus.CREATED)),
                this.asto,
                release
            ),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.CREATED),
                new RequestLine(RqMethod.GET, "/any/request/line")
            )
        );
        MatcherAssert.assertThat(
            "Release file was created",
            release.count.get(),
            new IsEqual<>(1)
        );
    }

    @Test
    void doesNothingAndForwardsResponse() {
        final Key key = new Key.From("dists/my-repo/Release");
        this.asto.save(key, Content.EMPTY).join();
        final Fake release = new Fake(key);
        MatcherAssert.assertThat(
            "Response is OK",
            new ReleaseSlice(
                new SliceSimple(new RsWithStatus(RsStatus.OK)),
                this.asto,
                release
            ),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine(RqMethod.GET, "/not/important")
            )
        );
        MatcherAssert.assertThat(
            "Release file was not created",
            release.count.get(),
            new IsEqual<>(0)
        );
    }

    /**
     * Fake {@link Release} implementation for the test.
     * @since 0.2
     */
    private static final class Fake implements Release {

        /**
         * Method calls count.
         */
        private final AtomicInteger count;

        /**
         * Release file key.
         */
        private final Key rfk;

        /**
         * Ctor.
         * @param key Release file key
         */
        private Fake(final Key key) {
            this.rfk = key;
            this.count = new AtomicInteger(0);
        }

        @Override
        public CompletionStage<Void> create() {
            this.count.incrementAndGet();
            return CompletableFuture.allOf();
        }

        @Override
        public CompletionStage<Void> update(final Key pckg) {
            throw new NotImplementedException("Not implemented");
        }

        @Override
        public Key key() {
            return this.rfk;
        }

        @Override
        public Key gpgKey() {
            throw new NotImplementedException("Not implemented yet");
        }
    }

}

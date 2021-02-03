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

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.debian.Config;
import com.artipie.debian.metadata.Release;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.reactivestreams.Publisher;

/**
 * Release slice decorator.
 * Checks, whether Release index exists and creates it if necessary.
 * @since 0.2
 */
public final class ReleaseSlice implements Slice {

    /**
     * Origin slice.
     */
    private final Slice origin;

    /**
     * Abstract storage.
     */
    private final Storage storage;

    /**
     * Repository configuration.
     */
    private final Config config;

    /**
     * Ctor.
     * @param origin Origin
     * @param asto Storage
     * @param config Repository configuration
     */
    public ReleaseSlice(final Slice origin, final Storage asto, final Config config) {
        this.origin = origin;
        this.config = config;
        this.storage = asto;
    }

    @Override
    public Response response(
        final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body
    ) {
        return new AsyncResponse(
            this.storage.exists(
                new Key.From(String.format("dists/%s/Release", this.config.codename()))
            ).thenCompose(
                exists -> {
                    final CompletionStage<Response> res;
                    if (exists) {
                        res = CompletableFuture.completedFuture(
                            this.origin.response(line, headers, body)
                        );
                    } else {
                        res = new Release.Asto(this.storage, this.config).create()
                            .thenApply(nothing -> this.origin.response(line, headers, body));
                    }
                    return res;
                }
            )
        );
    }
}

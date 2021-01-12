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
import com.artipie.asto.ext.PublisherAs;
import com.artipie.debian.metadata.Control;
import com.artipie.debian.metadata.ControlField;
import com.artipie.debian.metadata.Package;
import com.artipie.debian.metadata.PackagesItem;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rs.StandardRs;
import com.artipie.http.slice.KeyFromPath;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.reactivestreams.Publisher;

/**
 * Debian update slice adds uploaded slice to the storage and updates Packages index.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class DebianUpdateSlice implements Slice {

    /**
     * Abstract storage.
     */
    private final Storage asto;

    /**
     * Repository name.
     */
    private final String reponame;

    /**
     * Ctor.
     * @param asto Abstract storage
     * @param reponame Repository name
     */
    public DebianUpdateSlice(final Storage asto, final String reponame) {
        this.asto = asto;
        this.reponame = reponame;
    }

    @Override
    public Response response(final String line, final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        final Key key = new KeyFromPath(new RequestLineFrom(line).uri().getPath());
        return new AsyncResponse(
            this.asto.save(key, new Content.From(body))
                .thenCompose(nothing -> this.asto.value(key))
                .thenCompose(content -> new PublisherAs(content).bytes())
                .thenApply(bytes -> new Control.FromBinary(bytes).asString())
                .thenCompose(
                    control -> new PackagesItem.Asto(this.asto).format(control, key).thenCompose(
                        item -> CompletableFuture.allOf(
                            new ControlField.Architecture().value(control).stream().map(
                                arc -> String.format(
                                    "dists/%s/main/binary-%s/Packages.gz", this.reponame, arc
                                )
                            ).map(
                                index -> new Package.Simple(this.asto)
                                    .add(item, new Key.From(index))
                            ).toArray(CompletableFuture[]::new)
                        )
                    )
                ).handle(
                    (nothing, throwable) -> {
                        final CompletionStage<Response> resp;
                        if (throwable == null) {
                            resp = CompletableFuture.completedFuture(StandardRs.OK);
                        } else {
                            resp = this.asto.delete(key)
                                .thenApply(ignired -> new RsWithStatus(RsStatus.INTERNAL_ERROR));
                        }
                        return resp;
                    }
            ).thenCompose(Function.identity())
        );
    }
}

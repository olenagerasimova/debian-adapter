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

import com.artipie.asto.Storage;
import com.artipie.http.Slice;
import com.artipie.http.auth.Action;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.BasicAuthSlice;
import com.artipie.http.auth.Permission;
import com.artipie.http.auth.Permissions;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.StandardRs;
import com.artipie.http.rt.ByMethodsRule;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.RtRulePath;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.SliceDownload;
import com.artipie.http.slice.SliceSimple;

/**
 * Debian slice.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class DebianSlice extends Slice.Wrap {

    /**
     * Ctor.
     * @param storage Storage
     * @param reponame Repository name
     */
    public DebianSlice(final Storage storage, final String reponame) {
        this(storage, Permissions.FREE, Authentication.ANONYMOUS, reponame);
    }

    /**
     * Ctor.
     * @param storage Storage
     * @param perms Permissions
     * @param users Users
     * @param reponame Repository name
     * @checkstyle ParameterNumberCheck (5 lines)
     */
    public DebianSlice(final Storage storage, final Permissions perms,
        final Authentication users, final String reponame) {
        super(
            new SliceRoute(
                new RtRulePath(
                    new ByMethodsRule(RqMethod.GET),
                    new BasicAuthSlice(
                        new SliceDownload(storage),
                        users,
                        new Permission.ByName(perms, Action.Standard.READ)
                    )
                ),
                new RtRulePath(
                    new RtRule.Any(
                        new ByMethodsRule(RqMethod.PUT), new ByMethodsRule(RqMethod.POST)
                    ),
                    new BasicAuthSlice(
                        new UpdateSlice(storage, reponame),
                        users,
                        new Permission.ByName(perms, Action.Standard.READ)
                    )
                ),
                new RtRulePath(
                    RtRule.FALLBACK, new SliceSimple(StandardRs.NOT_FOUND)
                )
            )
        );
    }
}

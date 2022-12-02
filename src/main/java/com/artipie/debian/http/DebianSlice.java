/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/debian-adapter/LICENSE.txt
 */
package com.artipie.debian.http;

import com.artipie.asto.Storage;
import com.artipie.debian.Config;
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
     * @param config Repository configuration
     */
    public DebianSlice(final Storage storage, final Config config) {
        this(storage, Permissions.FREE, Authentication.ANONYMOUS, config);
    }

    /**
     * Ctor.
     * @param storage Storage
     * @param perms Permissions
     * @param users Users
     * @param config Repository configuration
     * @checkstyle ParameterNumberCheck (5 lines)
     */
    public DebianSlice(final Storage storage, final Permissions perms,
        final Authentication users, final Config config) {
        super(
            new SliceRoute(
                new RtRulePath(
                    new ByMethodsRule(RqMethod.GET),
                    new BasicAuthSlice(
                        new ReleaseSlice(new SliceDownload(storage), storage, config),
                        users,
                        new Permission.ByName(perms, Action.Standard.READ)
                    )
                ),
                new RtRulePath(
                    new RtRule.Any(
                        new ByMethodsRule(RqMethod.PUT), new ByMethodsRule(RqMethod.POST)
                    ),
                    new BasicAuthSlice(
                        new ReleaseSlice(new UpdateSlice(storage, config), storage, config),
                        users,
                        new Permission.ByName(perms, Action.Standard.WRITE)
                    )
                ),
                new RtRulePath(
                    RtRule.FALLBACK, new SliceSimple(StandardRs.NOT_FOUND)
                )
            )
        );
    }
}

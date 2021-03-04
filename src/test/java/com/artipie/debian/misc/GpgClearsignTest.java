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
package com.artipie.debian.misc;

import com.artipie.asto.test.TestResource;
import java.io.IOException;
import org.bouncycastle.openpgp.PGPException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link GpgClearsign}.
 * @since 0.4
 */
class GpgClearsignTest {

    @Test
    void signs() throws IOException, PGPException {
        final byte[] release = new TestResource("Release").asBytes();
        final byte[] res = new GpgClearsign(release)
            .signature(new TestResource("secret-keys.gpg").asBytes(), "1q2w3e4r5t6y7u");
        MatcherAssert.assertThat(
            "Contains original file and signature",
            new String(res),
            Matchers.allOf(
                new StringContains(new String(release)),
                new StringContains("-----BEGIN PGP SIGNED MESSAGE-----"),
                new StringContains("Hash: SHA256"),
                new StringContains("-----BEGIN PGP SIGNATURE-----"),
                new StringContains("-----END PGP SIGNATURE-----")
            )
        );
    }

}

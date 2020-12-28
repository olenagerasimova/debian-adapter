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

import com.artipie.asto.fs.FileStorage;
import com.artipie.asto.test.TestResource;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test for {@link Control.FromBinary}.
 * @since 0.1
 */
class ControlFromBinaryTest {

    @Test
    void readsData(@TempDir final Path temp) {
        final String name = "aglfn_1.7-3_all.deb";
        new TestResource(name).saveTo(new FileStorage(temp));
        MatcherAssert.assertThat(
            new Control.FromBinary(temp.resolve(name)).asString(),
            new IsEqual<>(
                String.join(
                    "\n",
                    "Package: aglfn",
                    "Version: 1.7-3",
                    "Architecture: all",
                    "Maintainer: Debian Fonts Task Force <pkg-fonts-devel@lists.alioth.debian.org>",
                    "Installed-Size: 138",
                    "Section: fonts",
                    "Priority: extra",
                    "Homepage: http://sourceforge.net/adobe/aglfn/",
                    "Description: Adobe Glyph List For New Fonts",
                    " AGL (Adobe Glyph List) maps glyph names to Unicode values for the",
                    " purpose of deriving content. AGLFN (Adobe Glyph List For New Fonts) is a",
                    " subset of AGL that excludes the glyph names associated with the PUA",
                    " (Private Use Area), and is meant to specify preferred glyph names for",
                    " new fonts. Also included is the ITC Zapf Dingbats Glyph List, which is",
                    " similar to AGL in that it maps glyph names to Unicode values for the",
                    " purpose of deriving content, but only for the glyphs in the ITC Zapf",
                    " Dingbats font.",
                    " .",
                    " Be sure to visit the AGL Specification and Developer Documentation pages",
                    " for detailed information about naming glyphs, interpreting glyph names,",
                    " and developing OpenType fonts.\n"
                )
            )
        );
    }

}

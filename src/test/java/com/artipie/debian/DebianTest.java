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

import com.amihaiemil.eoyaml.Yaml;
import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.cactoos.list.ListOf;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Debian.Asto}.
 * @since 0.4
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle MagicNumberCheck (500 lines)
 * @todo #51:30min Let's create a class in test scope to held/obtain information about test .deb
 *  packages, the class should provide package name, bytes, be able to put the package into provided
 *  storage and return meta info (like methods in this class do). We something similar in
 *  rpm-adapter, check https://github.com/artipie/rpm-adapter/blob/master/src/test/java/com/artipie/rpm/TestRpm.java
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.AssignmentInOperand"})
class DebianTest {

    /**
     * Debian repository name.
     */
    private static final String NAME = "my_deb_repo";

    /**
     * Packages index key.
     */
    private static final Key PACKAGES =
        new Key.From(DebianTest.NAME, "binary", "amd64", "Packages.gz");

    /**
     * Test storage.
     */
    private Storage storage;

    /**
     * Debian test instance.
     */
    private Debian debian;

    @BeforeEach
    void init() {
        this.storage = new InMemoryStorage();
        this.debian = new Debian.Asto(
            this.storage,
            new Config.FromYaml(
                DebianTest.NAME, Optional.of(Yaml.createYamlMappingBuilder().build()),
                new InMemoryStorage()
            )
        );
    }

    @Test
    void addsPackagesIndex() throws IOException {
        final List<String> debs = new ListOf<>(
            "libobus-ocaml_1.2.3-1+b3_amd64.deb",
            "aglfn_1.7-3_amd64.deb"
        );
        final String prefix = "my_deb";
        debs.forEach(
            item -> new TestResource(item).saveTo(this.storage, new Key.From(prefix, item))
        );
        this.debian.updatePackages(
            debs.stream().map(item -> new Key.From(prefix, item)).collect(Collectors.toList()),
            DebianTest.PACKAGES
        ).toCompletableFuture().join();
        MatcherAssert.assertThat(
            this.archiveAsString(),
            new AllOf<>(
                new ListOf<Matcher<? super String>>(
                    new StringContains("\n\n"),
                    new StringContains(this.libobusOcaml()),
                    new StringContains(this.aglfn())
                )
            )
        );
    }

    @Test
    void updatesPackagesIndex() throws IOException {
        final String pckg = "pspp_1.2.0-3_amd64.deb";
        final Key.From key = new Key.From("some_repo", pckg);
        new TestResource(pckg).saveTo(this.storage, key);
        this.storage.save(
            DebianTest.PACKAGES,
            new Content.From(this.archiveBytes(this.aglfn().getBytes(StandardCharsets.UTF_8)))
        ).join();
        this.debian.updatePackages(new ListOf<>(key), DebianTest.PACKAGES)
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            this.archiveAsString(),
            new AllOf<>(
                new ListOf<Matcher<? super String>>(
                    new StringContains("\n\n"),
                    new StringContains(this.pspp()),
                    new StringContains(this.aglfn())
                )
            )
        );
    }

    private String archiveAsString() throws IOException {
        try (
            GzipCompressorInputStream gcis = new GzipCompressorInputStream(
                new BufferedInputStream(
                    new ByteArrayInputStream(
                        new BlockingStorage(this.storage).value(DebianTest.PACKAGES)
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

    private byte[] archiveBytes(final byte[] bytes) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gcos = new GzipCompressorOutputStream(out)) {
            gcos.write(bytes);
        }
        return out.toByteArray();
    }

    private String pspp() {
        return String.join(
            "\n",
            "Package: pspp",
            "Version: 1.2.0-3",
            "Architecture: amd64",
            "Maintainer: Debian Science Team <debian-science-maintainers@lists.alioth.debian.org>",
            "Installed-Size: 15735",
            // @checkstyle LineLengthCheck (1 line)
            "Depends: libatk1.0-0 (>= 1.12.4), libc6 (>= 2.17), libcairo-gobject2 (>= 1.10.0), libcairo2 (>= 1.12), libgdk-pixbuf2.0-0 (>= 2.22.0), libglib2.0-0 (>= 2.43.4), libgsl23 (>= 2.5), libgslcblas0 (>= 2.4), libgtk-3-0 (>= 3.21.5), libgtksourceview-3.0-1 (>= 3.18), libpango-1.0-0 (>= 1.22), libpangocairo-1.0-0 (>= 1.22), libpq5, libreadline7 (>= 6.0), libspread-sheet-widget, libxml2 (>= 2.7.4), zlib1g (>= 1:1.1.4), emacsen-common",
            "Section: math",
            "Priority: optional",
            "Homepage: http://savannah.gnu.org/projects/pspp",
            "Description: Statistical analysis tool",
            " PSPP is a program for statistical analysis of sampled data. It is a free",
            " replacement for the proprietary program SPSS.",
            " .",
            " PSPP supports T-tests, ANOVA, GLM, factor analysis, non-parametric tests, and",
            " other statistical features. PSPP produces statistical reports in plain text,",
            " PDF, PostScript, CSV, HTML, SVG, and OpenDocument formats.",
            " .",
            " PSPP has both text-based and graphical user interfaces. The PSPP user interface",
            " has been translated into a number of languages.",
            "Filename: some_repo/pspp_1.2.0-3_amd64.deb",
            "Size: 3809960",
            "MD5sum: 42f4ff59934206b37574fc317b94a854",
            "SHA1: ec07cc41c41f0db4c287811d05564ad8c6ca1845",
            "SHA256: 02b15744576cefe92a1f874d8663575caaa71c0e6c60795e8617c23338fc5fc3"
        );
    }

    private String aglfn() {
        return String.join(
            "\n",
            "Package: aglfn",
            "Version: 1.7-3",
            "Architecture: amd64",
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
            " and developing OpenType fonts.",
            "Filename: my_deb/aglfn_1.7-3_amd64.deb",
            "Size: 29936",
            "MD5sum: eb647d864e8283cbf5b17e44a2a00b9c",
            "SHA1: 246ffaf3e5e06259e663d404f16764171216c538",
            "SHA256: 66f92b0628fb5fcbc76b9e1388f4f4d1ebf5a68835f05a03a876e08c56f46ab3"
        );
    }

    private String libobusOcaml() {
        return String.join(
            "\n",
            "Package: libobus-ocaml",
            "Source: obus (1.2.3-1)",
            "Version: 1.2.3-1+b3",
            "Architecture: amd64",
            "Maintainer: Debian OCaml Maintainers <debian-ocaml-maint@lists.debian.org>",
            "Installed-Size: 5870",
            // @checkstyle LineLengthCheck (1 line)
            "Depends: liblwt-log-ocaml-1f1y2, liblwt-ocaml-dt6l9, libmigrate-parsetree-ocaml-n2039, libreact-ocaml-pdm50, libresult-ocaml-ki2r2, libsexplib0-ocaml-drlz0, ocaml-base-nox-4.11.1",
            "Provides: libobus-ocaml-d0567",
            "Section: ocaml",
            "Priority: optional",
            "Homepage: https://github.com/ocaml-community/obus",
            "Description: pure OCaml implementation of D-Bus (runtime)",
            " OBus is a pure OCaml implementation of D-Bus. It aims to provide a",
            " clean and easy way for OCaml programmers to access and provide D-Bus",
            " services.",
            " .",
            " This package contains dynamically loadable plugins of OBus.",
            "Filename: my_deb/libobus-ocaml_1.2.3-1+b3_amd64.deb",
            "Size: 1338616",
            "MD5sum: 2121df46da5e94bb68603bb2f573d80b",
            "SHA1: b61297f47c6d8c8bb530301cd915e05e2bd23365",
            "SHA256: 90dce70b7604a4e3a35faa35830039af203c7b8df5399ef0eab818157f5c4ce6"
        );
    }

}

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

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.debian.GzArchive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link UniquePackage}.
 * @since 0.5
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
class UniquePackageTest {

    /**
     * Packages file index key.
     */
    private static final String KEY = "Packages.gz";

    /**
     * Test storage.
     */
    private Storage asto;

    @BeforeEach
    void init() {
        this.asto = new InMemoryStorage();
    }

    @Test
    void appendsNewRecord() throws IOException {
        new TestResource(UniquePackageTest.KEY).saveTo(this.asto);
        new UniquePackage(this.asto)
            .add(new ListOf<>(this.abcPackageInfo()), new Key.From(UniquePackageTest.KEY))
            .toCompletableFuture().join();
        final Storage temp = new InMemoryStorage();
        new TestResource(UniquePackageTest.KEY).saveTo(temp);
        MatcherAssert.assertThat(
            "Packages index has info about 3 packages",
            new GzArchive(this.asto).unpack(new Key.From(UniquePackageTest.KEY)),
            new StringContainsInOrder(
                new ListOf<String>(
                    new GzArchive(temp).unpack(new Key.From(UniquePackageTest.KEY)),
                    "\n\n",
                    this.abcPackageInfo()
                )
            )
        );
        this.verifyThatTempDirIsCleanedUp();
    }

    private void verifyThatTempDirIsCleanedUp() throws IOException {
        final Path systemtemp = Paths.get(System.getProperty("java.io.tmpdir"));
        MatcherAssert.assertThat(
            "Temp dir for indexes removed",
            Files.list(systemtemp)
                .noneMatch(path -> path.getFileName().toString().startsWith("packages")),
            new IsEqual<>(true)
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
            "Filename: some/debian/package.deb",
            "Size: 23",
            "MD5sum: e99a18c428cb38d5f260853678922e03"
        );
    }

}

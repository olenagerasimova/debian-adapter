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

import com.artipie.asto.Content;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link UniquePackage}.
 * @since 0.5
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.TooManyMethods"})
class UniquePackageTest {

    /**
     * Packages file index name.
     */
    private static final String PCKG = "Packages.gz";

    /**
     * Packages file index key.
     */
    private static final Key.From KEY = new Key.From(UniquePackageTest.PCKG);

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
        new TestResource(UniquePackageTest.PCKG).saveTo(this.asto);
        new UniquePackage(this.asto)
            .add(new ListOf<>(this.abcPackageInfo()), UniquePackageTest.KEY)
            .toCompletableFuture().join();
        final Storage temp = new InMemoryStorage();
        new TestResource(UniquePackageTest.PCKG).saveTo(temp);
        MatcherAssert.assertThat(
            "Packages index has info about 3 packages",
            new GzArchive(this.asto).unpack(UniquePackageTest.KEY),
            new IsEqual<>(
                String.join(
                    "\n\n",
                    new GzArchive(temp).unpack(UniquePackageTest.KEY),
                    this.abcPackageInfo()
                )
            )
        );
        this.verifyThatTempDirIsCleanedUp();
    }

    @Test
    void replacesOneExistingPackage() throws IOException {
        final Key old = new Key.From("abc/old/package.deb");
        this.asto.save(old, Content.EMPTY).join();
        new GzArchive(this.asto).packAndSave(
            this.abcPackageInfo(old.string()),
            UniquePackageTest.KEY
        );
        new UniquePackage(this.asto)
            .add(new ListOf<>(this.abcPackageInfo()), UniquePackageTest.KEY)
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Packages index has info about 1 package",
            new GzArchive(this.asto).unpack(UniquePackageTest.KEY),
            new IsEqual<>(this.abcPackageInfo())
        );
        this.verifyOldPackageWasRemoved(old);
        this.verifyThatTempDirIsCleanedUp();
    }

    @Test
    void doesNotReplacePackageWithAnotherVersion() throws IOException {
        final String second = this.abcPackageInfo().replace("Version: 0.1", "Version: 0.2");
        new GzArchive(this.asto).packAndSave(second, UniquePackageTest.KEY);
        new UniquePackage(this.asto)
            .add(new ListOf<>(this.abcPackageInfo()), UniquePackageTest.KEY)
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Packages index has info about 2 packages",
            new GzArchive(this.asto).unpack(UniquePackageTest.KEY),
            new IsEqual<>(String.join("\n\n", second, this.abcPackageInfo()))
        );
        this.verifyThatTempDirIsCleanedUp();
    }

    @Test
    void replacesFirstDuplicatedPackage() throws IOException {
        final Key old = new Key.From("zero/old/package.deb");
        this.asto.save(old, Content.EMPTY).join();
        new GzArchive(this.asto).packAndSave(
            String.join(
                "\n\n",
                this.zeroPackageInfo(old.string()),
                this.xyzPackageInfo()
            ),
            UniquePackageTest.KEY
        );
        new UniquePackage(this.asto)
            .add(
                new ListOf<>(this.zeroPackageInfo(), this.abcPackageInfo()),
                UniquePackageTest.KEY
            ).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Packages index has info about 3 packages",
            new GzArchive(this.asto).unpack(UniquePackageTest.KEY),
            new IsEqual<>(
                String.join(
                    "\n\n", this.xyzPackageInfo(), this.zeroPackageInfo(), this.abcPackageInfo()
                )
            )
        );
        this.verifyOldPackageWasRemoved(old);
        this.verifyThatTempDirIsCleanedUp();
    }

    @Test
    void replacesLastDuplicatedPackage() throws IOException {
        final Key old = new Key.From("zero/one/two/package.deb");
        this.asto.save(old, Content.EMPTY).join();
        new GzArchive(this.asto).packAndSave(
            String.join(
                "\n\n",
                this.abcPackageInfo(),
                this.zeroPackageInfo(old.string())
            ),
            UniquePackageTest.KEY
        );
        new UniquePackage(this.asto)
            .add(
                new ListOf<>(this.xyzPackageInfo(), this.zeroPackageInfo()),
                UniquePackageTest.KEY
            ).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Packages index has info about 3 packages",
            new GzArchive(this.asto).unpack(UniquePackageTest.KEY),
            new IsEqual<>(
                String.join(
                    "\n\n", this.abcPackageInfo(), this.xyzPackageInfo(), this.zeroPackageInfo()
                )
            )
        );
        this.verifyOldPackageWasRemoved(old);
        this.verifyThatTempDirIsCleanedUp();
    }

    @Test
    void replacesMiddleDuplicatedPackage() throws IOException {
        final Key old = new Key.From("zero/one/one/package.deb");
        this.asto.save(old, Content.EMPTY).join();
        new GzArchive(this.asto).packAndSave(
            String.join(
                "\n\n",
                this.abcPackageInfo(),
                this.zeroPackageInfo(old.string()),
                this.xyzPackageInfo()
            ),
            UniquePackageTest.KEY
        );
        new UniquePackage(this.asto)
            .add(
                new ListOf<>(this.zeroPackageInfo()),
                UniquePackageTest.KEY
            ).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Packages index has info about 3 packages",
            new GzArchive(this.asto).unpack(UniquePackageTest.KEY),
            new IsEqual<>(
                String.join(
                    "\n\n", this.abcPackageInfo(), this.xyzPackageInfo(), this.zeroPackageInfo()
                )
            )
        );
        this.verifyOldPackageWasRemoved(old);
        this.verifyThatTempDirIsCleanedUp();
    }

    @Test
    void replacesTwoDuplicatedPackages() throws IOException {
        final Key one = new Key.From("one/zero/package.deb");
        this.asto.save(one, Content.EMPTY).join();
        final Key two = new Key.From("two/abc/package.deb");
        this.asto.save(two, Content.EMPTY).join();
        new GzArchive(this.asto).packAndSave(
            String.join(
                "\n\n",
                this.abcPackageInfo(two.string()),
                this.zeroPackageInfo(one.string()),
                this.xyzPackageInfo()
            ),
            UniquePackageTest.KEY
        );
        new UniquePackage(this.asto)
            .add(
                new ListOf<>(this.abcPackageInfo(), this.zeroPackageInfo()),
                UniquePackageTest.KEY
            ).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Packages index has info about 3 packages",
            new GzArchive(this.asto).unpack(UniquePackageTest.KEY),
            new IsEqual<>(
                String.join(
                    "\n\n", this.xyzPackageInfo(), this.abcPackageInfo(), this.zeroPackageInfo()
                )
            )
        );
        this.verifyOldPackageWasRemoved(one, two);
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

    private void verifyOldPackageWasRemoved(final Key... keys) {
        for (final Key key : keys) {
            MatcherAssert.assertThat(
                String.format("Key %s was removed", key),
                this.asto.exists(key).join(),
                new IsEqual<>(false)
            );
        }
    }

    private String abcPackageInfo(final String filename) {
        return String.join(
            "\n",
            "Package: abc",
            "Version: 0.1",
            "Architecture: all",
            "Maintainer: Task Force",
            "Installed-Size: 130",
            "Section: The Force",
            String.format("Filename: %s", filename),
            "Size: 23",
            "MD5sum: e99a18c428cb38d5f260853678922e03"
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

    private String xyzPackageInfo() {
        return String.join(
            "\n",
            "Package: xyz",
            "Version: 0.6",
            "Architecture: amd64",
            "Maintainer: Blue Sky",
            "Installed-Size: 131",
            "Section: Un-Existing",
            "Filename: some/not/existing/package.deb",
            "Size: 45",
            "MD5sum: e99a18c428cb38d5f260883678922e03"
        );
    }

    private String zeroPackageInfo() {
        return String.join(
            "\n",
            "Package: zero",
            "Version: 0.0",
            "Architecture: all",
            "Maintainer: Zero division",
            "Installed-Size: 0",
            "Section: Zero",
            "Filename: zero/package.deb",
            "Size: 0",
            "MD5sum: 0000"
        );
    }

    private String zeroPackageInfo(final String filename) {
        return String.join(
            "\n",
            "Package: zero",
            "Version: 0.0",
            "Architecture: all",
            "Maintainer: Zero division",
            "Installed-Size: 0",
            "Section: Zero",
            String.format("Filename: %s", filename),
            "Size: 0",
            "MD5sum: 0000"
        );
    }

}

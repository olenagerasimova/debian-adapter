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
import com.artipie.asto.fs.FileStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.debian.http.DebianSlice;
import com.artipie.debian.misc.GpgClearsign;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import com.jcabi.log.Logger;
import io.vertx.reactivex.core.Vertx;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.cactoos.list.ListOf;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.IsNot;
import org.hamcrest.core.StringContains;
import org.hamcrest.text.MatchesPattern;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

/**
 * Test for {@link DebianSlice} with GPG-signature.
 * @since 0.4
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@EnabledOnOs({OS.LINUX, OS.MAC})
public final class DebianGpgSliceITCase {

    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Temporary directory for all tests.
     * @checkstyle VisibilityModifierCheck (3 lines)
     */
    @TempDir
    Path tmp;

    /**
     * Test storage.
     */
    private Storage storage;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    /**
     * Container.
     */
    private GenericContainer<?> cntn;

    @BeforeEach
    void init() throws Exception {
        this.storage = new InMemoryStorage();
        new TestResource("public-key.asc").saveTo(new FileStorage(this.tmp));
        this.server = new VertxSliceServer(
            DebianGpgSliceITCase.VERTX,
            new LoggingSlice(
                new DebianSlice(
                    this.storage,
                    new Config.FromYaml(
                        "artipie",
                        Yaml.createYamlMappingBuilder()
                            .add("Components", "main")
                            .add("Architectures", "amd64")
                            .build(),
                        new InMemoryStorage()
                    )
                )
            )
        );
        final int port = this.server.start();
        Testcontainers.exposeHostPorts(port);
        Files.write(
            this.tmp.resolve("sources.list"),
            String.format(
                "deb http://host.testcontainers.internal:%d/ artipie main", port
            ).getBytes()
        );
        this.cntn = new GenericContainer<>("debian")
            .withCommand("tail", "-f", "/dev/null")
            .withWorkingDirectory("/home/")
            .withFileSystemBind(this.tmp.toString(), "/home");
        this.cntn.start();
        this.exec("apt-get", "update");
        this.exec("apt-get", "install", "-y", "gnupg");
        this.exec("apt-key", "add", "/home/public-key.asc");
        this.exec("mv", "/home/sources.list", "/etc/apt/");
    }

    @Test
    void installWithInReleaseFileWorks() throws Exception {
        this.copyPackage("aglfn_1.7-3_amd64.deb");
        this.storage.save(
            new Key.From("dists/artipie/InRelease"),
            new Content.From(
                new GpgClearsign(new TestResource("Release").asBytes()).signedContent(
                    new TestResource("secret-keys.gpg").asBytes(), "1q2w3e4r5t6y7u"
                )
            )
        ).join();
        MatcherAssert.assertThat(
            "InRelease file is used on update the world",
            this.exec("apt-get", "update"),
            new AllOf<>(
                new ListOf<Matcher<? super String>>(
                    // @checkstyle LineLengthCheck (2 lines)
                    new MatchesPattern(Pattern.compile("[\\S\\s]*Get:1 http://host.testcontainers.internal:\\d+ artipie InRelease[\\S\\s]*")),
                    new MatchesPattern(Pattern.compile("[\\S\\s]*Get:2 http://host.testcontainers.internal:\\d+ artipie/main amd64 Packages \\[1351 B][\\S\\s]*")),
                    new IsNot<>(new StringContains("Get:3"))
                )
            )
        );
        MatcherAssert.assertThat(
            "Package was downloaded and unpacked",
            this.exec("apt-get", "install", "-y", "aglfn"),
            new AllOf<>(
                new ListOf<Matcher<? super String>>(
                    // @checkstyle LineLengthCheck (1 line)
                    new MatchesPattern(Pattern.compile("[\\S\\s]*Get:1 http://host.testcontainers.internal:\\d+ artipie/main amd64 aglfn amd64 1.7-3 \\[29.9 kB][\\S\\s]*")),
                    new IsNot<>(new StringContains("Get:2")),
                    new StringContainsInOrder(new ListOf<>("Unpacking aglfn", "Setting up aglfn"))
                )
            )
        );
    }

    @Test
    void installWithReleaseFileWorks() throws Exception {
        this.copyPackage("aglfn_1.7-3_amd64.deb");
        new TestResource("Release").saveTo(this.storage, new Key.From("dists/artipie/Release"));
        this.storage.save(
            new Key.From("dists/artipie/Release.gpg"),
            new Content.From(
                new GpgClearsign(new TestResource("Release").asBytes()).signature(
                    new TestResource("secret-keys.gpg").asBytes(), "1q2w3e4r5t6y7u"
                )
            )
        ).join();
        MatcherAssert.assertThat(
            "InRelease file is used on update the world",
            this.exec("apt-get", "update"),
            new AllOf<>(
                new ListOf<Matcher<? super String>>(
                    // @checkstyle LineLengthCheck (3 lines)
                    new MatchesPattern(Pattern.compile("[\\S\\s]*Get:2 http://host.testcontainers.internal:\\d+ artipie Release[\\S\\s]*")),
                    new MatchesPattern(Pattern.compile("[\\S\\s]*Get:3 http://host.testcontainers.internal:\\d+ artipie Release.gpg[\\S\\s]*")),
                    new MatchesPattern(Pattern.compile("[\\S\\s]*Get:4 http://host.testcontainers.internal:\\d+ artipie/main amd64 Packages \\[1351 B][\\S\\s]*")),
                    new IsNot<>(new StringContains("Get:5"))
                )
            )
        );
        MatcherAssert.assertThat(
            "Package was downloaded and unpacked",
            this.exec("apt-get", "install", "-y", "aglfn"),
            new AllOf<>(
                new ListOf<Matcher<? super String>>(
                    // @checkstyle LineLengthCheck (1 line)
                    new MatchesPattern(Pattern.compile("[\\S\\s]*Get:1 http://host.testcontainers.internal:\\d+ artipie/main amd64 aglfn amd64 1.7-3 \\[29.9 kB][\\S\\s]*")),
                    new IsNot<>(new StringContains("Get:2")),
                    new StringContainsInOrder(new ListOf<>("Unpacking aglfn", "Setting up aglfn"))
                )
            )
        );
    }

    @AfterEach
    void stop() {
        this.server.stop();
        this.cntn.stop();
    }

    private void copyPackage(final String pkg) {
        new TestResource(pkg).saveTo(this.storage, new Key.From("main", pkg));
        new TestResource("Packages.gz")
            .saveTo(this.storage, new Key.From("dists/artipie/main/binary-amd64/Packages.gz"));
    }

    private String exec(final String... command) throws Exception {
        final Container.ExecResult res = this.cntn.execInContainer(command);
        Logger.debug(this, "Command:\n%s\nResult:\n%s", String.join(" ", command), res.toString());
        return res.getStdout();
    }
}

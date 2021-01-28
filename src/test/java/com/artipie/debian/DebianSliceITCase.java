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

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.debian.http.DebianSlice;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import com.jcabi.log.Logger;
import io.vertx.reactivex.core.Vertx;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNot;
import org.hamcrest.core.StringContains;
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
 * Test for {@link com.artipie.debian.http.DebianSlice}.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @todo #2:30min Find (or create) package without any dependencies or necessary settings
 *  for install test: current package `aglfn_1.7-3_all.deb` is now successfully downloaded and
 *  unpacked, but then debian needs to configure it and fails.
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@EnabledOnOs({OS.LINUX, OS.MAC})
public final class DebianSliceITCase {

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
     * Artipie port.
     */
    private int port;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    /**
     * Container.
     */
    private GenericContainer<?> cntn;

    @BeforeEach
    void init() throws IOException, InterruptedException {
        this.storage = new InMemoryStorage();
        this.server = new VertxSliceServer(
            DebianSliceITCase.VERTX,
            new LoggingSlice(new DebianSlice(this.storage, "artipie"))
        );
        this.port = this.server.start();
        Testcontainers.exposeHostPorts(this.port);
        final Path setting = this.tmp.resolve("sources.list");
        Files.write(
            setting,
            String.format(
                "deb [trusted=yes] http://host.testcontainers.internal:%d/ artipie main", this.port
            ).getBytes()
        );
        this.cntn = new GenericContainer<>("debian")
            .withCommand("tail", "-f", "/dev/null")
            .withWorkingDirectory("/home/")
            .withFileSystemBind(this.tmp.toString(), "/home");
        this.cntn.start();
        this.cntn.execInContainer("mv", "/home/sources.list", "/etc/apt/");
        this.cntn.execInContainer("ls", "-la", "/etc/apt/");
        this.cntn.execInContainer("cat", "/etc/apt/sources.list");
    }

    @Test
    void searchWorks() throws Exception {
        copyPackage("pspp_1.2.0-3_amd64.deb");
        this.cntn.execInContainer("apt-get", "update");
        MatcherAssert.assertThat(
            this.exec("apt-cache", "search", "pspp"),
            new StringContainsInOrder(new ListOf<>("pspp", "Statistical analysis tool"))
        );
    }

    @Test
    void installWorks() throws Exception {
        copyPackage("aglfn_1.7-3_amd64.deb");
        this.cntn.execInContainer("apt-get", "update");
        MatcherAssert.assertThat(
            "Package was downloaded and unpacked",
            this.exec("apt-get", "install", "-y", "aglfn"),
            new StringContainsInOrder(new ListOf<>("Unpacking aglfn", "Setting up aglfn"))
        );
    }

    @Test
    void installWithInReleaseFileWorks() throws Exception {
        this.copyPackage("aglfn_1.7-3_amd64.deb");
        new TestResource("Release")
            .saveTo(this.storage, new Key.From("dists/artipie/Release"));
        MatcherAssert.assertThat(
            "Release file is used on update the world",
            this.exec("apt-get", "update"),
            Matchers.allOf(
                new StringContains("artipie/main amd64 Packages"),
                new IsNot<>(new StringContains("artipie/main all Packages"))
            )
        );
        MatcherAssert.assertThat(
            "Package was downloaded and unpacked",
            this.exec("apt-get", "install", "-y", "aglfn"),
            new StringContainsInOrder(new ListOf<>("Unpacking aglfn", "Setting up aglfn"))
        );
    }

    @Test
    void pushAndInstallWorks() throws Exception {
        final HttpURLConnection con = (HttpURLConnection) new URL(
            String.format("http://localhost:%d/main/aglfn_1.7-3_amd64.deb", this.port)
        ).openConnection();
        con.setDoOutput(true);
        con.setRequestMethod("PUT");
        final DataOutputStream out = new DataOutputStream(con.getOutputStream());
        out.write(new TestResource("aglfn_1.7-3_amd64.deb").asBytes());
        out.close();
        MatcherAssert.assertThat(
            "Response for upload is OK",
            con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
        );
        this.exec("apt-get", "update");
        MatcherAssert.assertThat(
            "Package was downloaded and unpacked",
            this.exec("apt-get", "install", "-y", "aglfn"),
            new StringContainsInOrder(new ListOf<>("Unpacking aglfn", "Setting up aglfn"))
        );
    }

    private void copyPackage(String s) {
        new TestResource(s).saveTo(this.storage, new Key.From("main", s));
        new TestResource("Packages.gz")
            .saveTo(this.storage, new Key.From("dists/artipie/main/binary-amd64/Packages.gz"));
    }

    @AfterEach
    void stop() {
        this.server.stop();
        this.cntn.stop();
    }

    private String exec(final String... command) throws Exception {
        final Container.ExecResult res = this.cntn.execInContainer(command);
        Logger.debug(this, "Command:\n%s\nResult:\n%s", String.join(" ", command), res.toString());
        return res.getStdout();
    }
}
